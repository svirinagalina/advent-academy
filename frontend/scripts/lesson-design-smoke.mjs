// Real local services, synthetic users and a guarded tmpfs database only.
import assert from 'node:assert/strict';
import { readFileSync, mkdirSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { join } from 'node:path';
import { withSmokeBrowser } from './smoke-browser.mjs';

const base = process.env.ADVENT_TEST_ORIGIN;
const gateway = process.env.ADVENT_TEST_GATEWAY;
for (const address of [base, gateway]) assert.ok(['127.0.0.1', 'localhost'].includes(new URL(address).hostname));
const fixture = JSON.parse(readFileSync(process.env.ADVENT_EDITOR_FIXTURE, 'utf8'));
assert.ok(fixture.users.editor.email.endsWith('@example.test'));
const container = process.env.ADVENT_TEST_POSTGRES;
const inspection = JSON.parse(execFileSync('docker', ['inspect', container], { encoding: 'utf8' }))[0];
assert.equal(inspection.Config.Labels['com.docker.compose.project'], process.env.ADVENT_TEST_PROJECT);
assert.equal(inspection.Config.Labels['com.docker.compose.service'], 'postgres');
assert.ok('/var/lib/postgresql/data' in inspection.HostConfig.Tmpfs);
const artifacts = join(process.env.ADVENT_TEST_ARTIFACTS, 'lesson-design');
mkdirSync(artifacts, { recursive: true });
const videoBytes = readFileSync(new URL('./fixtures/lesson-video.mp4', import.meta.url));
const java = '/* Многострочный\n   комментарий */\nint age = 18; if (age > 0) age++;\nString text = "<script>window.__codeRan = true</script>";\n' + '// ' + 'long '.repeat(200) + '\n';
const plain = '<unsafe value="1">\nplain & text\n';
const markdown = '# Материал для проверки дизайна\n\n' + 'Абзац учебного материала.\n\n'.repeat(35)
    + '```java\n' + java + '```\n\n```unknown-language\n' + plain + '```\n\n```\nno language\n```';

await withSmokeBrowser('lesson-design', async browser => {
    async function session(who) {
        const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, permissions: ['clipboard-read', 'clipboard-write'] });
        const response = await context.request.post(`${base}/api/session`, { headers: { Origin: base }, data: { email: fixture.users[who].email, password: fixture.password } });
        assert.equal(response.status(), 200, await response.text());
        return context;
    }
    async function api(context, method, path, data, expected = 200) {
        const response = await context.request.fetch(`${base}/api/backend/${path}`, { method, headers: { Origin: base }, ...(data === undefined ? {} : { data }) });
        const text = await response.text(); assert.equal(response.status(), expected, text);
        return text ? JSON.parse(text) : null;
    }
    const owner = await session('editor'), learner = await session('student');
    const page = await learner.newPage(), manage = await owner.newPage();
    const errors = [];
    for (const target of [page, manage]) target.on('pageerror', error => errors.push(error.message));
    const course = await api(owner, 'POST', 'courses', { title: 'Урок и состояния форм' }, 201);
    const module = await api(owner, 'POST', 'components', { title: 'Модуль дизайна' }, 201);
    const lesson = (await api(owner, 'POST', `components/${module.id}/lessons`, { title: 'Код и задание' }, 201)).lessons[0];
    assert.match(lesson.id, /^[0-9a-f-]{36}$/i);
    // There is no authoring UI/API for requiredAssignment; constrain setup to this synthetic lesson.
    execFileSync('docker', ['exec', '-i', container, 'psql', '-U', 'sprint', '-d', 'sprint', '-v', 'ON_ERROR_STOP=1'], {
        input: `UPDATE content.lessons SET required_assignment=true WHERE id='${lesson.id}';`,
    });
    const login = await owner.request.post(`${gateway}/api/auth/login`, { data: { email: fixture.users.editor.email, password: fixture.password } });
    assert.equal(login.status(), 200, await login.text());
    const token = (await login.json()).accessToken;
    const definition = await owner.request.put(`${gateway}/api/lessons/${lesson.id}/assignment`, {
        headers: { Authorization: `Bearer ${token}` }, data: { title: 'Проверка перехода к заданию', description: 'Отправьте ссылку на решение.' },
    });
    assert.equal(definition.status(), 200, await definition.text());
    await api(owner, 'POST', `courses/${course.id}/components`, { componentId: module.id }, 201);
    for (const [name, mimeType, buffer] of [['lesson-video.mp4', 'video/mp4', videoBytes], ['Конспект.txt', 'text/plain', Buffer.from('Конспект учебного урока\n')]]) {
        const response = await owner.request.post(`${base}/api/backend/lessons/${lesson.id}/assets`, {
            headers: { Origin: base }, multipart: { file: { name, mimeType, buffer } },
        });
        assert.equal(response.status(), 201, await response.text());
    }
    const revision = await api(owner, 'PUT', `lessons/${lesson.id}/content/draft`, { markdown, version: 0 });
    await api(owner, 'POST', `lessons/${lesson.id}/content/draft/publish`, { version: revision.version });
    await api(owner, 'POST', `components/${module.id}/publish`);
    await api(owner, 'POST', `courses/${course.id}/publish`);
    await api(owner, 'PUT', `learning/courses/${course.id}/students/${fixture.users.student.id}`);
    const href = `${base}/?view=lesson&course=${course.id}&lesson=${lesson.id}`;
    const assignmentRoute = `**/api/backend/learning/courses/${course.id}/lessons/${lesson.id}/assignment`;
    const assignmentHeading = page.getByRole('heading', { name: 'Проверка перехода к заданию', exact: true });
    async function focusedAssignment() {
        await assignmentHeading.waitFor();
        await page.waitForFunction(() => document.activeElement?.textContent === 'Проверка перехода к заданию');
        const box = await assignmentHeading.boundingBox();
        assert.ok(box.y >= 0 && box.y < page.viewportSize().height);
    }
    // Focus follows the real asynchronous form, including direct links and delayed responses.
    await page.route(assignmentRoute, async route => { const response = await route.fetch(); await new Promise(resolve => setTimeout(resolve, 300)); await route.fulfill({ response }); });
    await page.goto(href + '&assignment=1'); await focusedAssignment();
    await page.unroute(assignmentRoute);
    await page.getByRole('button', { name: 'Обновить статус', exact: true }).click();
    await page.getByRole('button', { name: 'Обновить статус', exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: 'Обновить статус', exact: true }).evaluate(el => el === document.activeElement), true);
    for (const view of ['home', 'assignments']) {
        await page.goto(`${base}/?view=${view}`);
        await page.locator('.assignment-row').filter({ hasText: 'Проверка перехода к заданию' }).getByRole('link', { name: 'Открыть задание', exact: true }).click();
        await focusedAssignment();
    }
    // Initial failure has an explicit retry, then focuses the loaded assignment exactly once.
    await page.route(assignmentRoute, route => route.abort('failed'), { times: 1 });
    await page.goto(href + '&assignment=1');
    await page.locator('.assignment-form').getByRole('button', { name: 'Повторить', exact: true }).click();
    await focusedAssignment();
    const submit = page.getByRole('button', { name: 'Отправить на проверку', exact: true });
    const solution = page.getByLabel('Ссылка на решение', { exact: true });
    await submit.click();
    assert.equal(await solution.getAttribute('aria-invalid'), 'true');
    assert.equal(await page.locator('#' + await solution.getAttribute('aria-describedby')).innerText(), 'Укажите ссылку на решение.');
    await solution.fill('http://example.test/repository'); await submit.click();
    await page.getByRole('alert').filter({ hasText: 'Укажите HTTPS-ссылку' }).waitFor();
    await solution.fill('https://example.test/repository');
    await page.getByLabel('Комментарий к решению', { exact: true }).fill('Сохранённый комментарий');
    const submissions = `**/api/backend/learning/courses/${course.id}/lessons/${lesson.id}/submissions`;
    await page.route(submissions, route => route.abort('failed'), { times: 1 });
    await submit.click(); await page.getByRole('alert').filter({ hasText: 'Проверьте соединение' }).waitFor();
    assert.equal(await solution.inputValue(), 'https://example.test/repository');
    assert.equal(await solution.getAttribute('aria-invalid'), 'false');
    await submit.click(); await page.getByText('Решение отправлено. Ожидайте результата проверки.', { exact: true }).waitFor();
    // Real Java highlighting, exact source copy, plain unknown languages, no code execution.
    const block = page.locator('.lesson-code').first();
    await block.locator('.hljs-keyword').first().waitFor();
    assert.equal(await block.locator('code').textContent(), java);
    assert.equal(await block.locator('.lesson-code-numbers').getAttribute('data-numbers'), '1\n2\n3\n4\n5');
    await block.getByRole('button', { name: 'Копировать код', exact: true }).click();
    await block.getByText('Код скопирован', { exact: true }).waitFor();
    assert.equal(await page.evaluate(() => navigator.clipboard.readText()), java);
    assert.equal(await page.locator('.lesson-code').nth(1).locator('code').textContent(), plain);
    assert.equal(await page.locator('.lesson-code').nth(1).locator('.hljs-keyword').count(), 0);
    assert.equal(await page.locator('.lesson-material script').count(), 0);
    assert.equal(await page.evaluate(() => window.__codeRan), undefined);
    const materials = await api(learner, 'GET', `learning/courses/${course.id}/lessons/${lesson.id}`);
    const attachment = materials.assets.find(asset => asset.contentType === 'text/plain');
    const video = materials.assets.find(asset => asset.contentType.startsWith('video/'));
    assert.ok(attachment && video);
    const assetRoute = asset => `**/api/backend/assets/${asset.id}`;
    await page.route(assetRoute(attachment), route => route.fulfill({ status: 503, body: 'Unavailable' }), { times: 1 });
    await page.getByRole('button', { name: `Скачать ${attachment.filename}`, exact: true }).click();
    await page.getByRole('alert').filter({ hasText: 'Не удалось скачать файл' }).waitFor();
    const downloadEvent = page.waitForEvent('download');
    await page.getByRole('button', { name: `Повторить скачивание ${attachment.filename}`, exact: true }).click();
    const download = await downloadEvent;
    assert.equal(download.suggestedFilename(), attachment.filename);
    assert.equal(readFileSync(await download.path(), 'utf8'), 'Конспект учебного урока\n');
    assert.equal(page.url(), href + '&assignment=1');
    // Real video asset fails once and recovers metadata/playback on retry.
    await page.route(assetRoute(video), route => route.abort('failed'), { times: 1 });
    await page.reload();
    await page.getByRole('button', { name: 'Повторить загрузку видео', exact: true }).click();
    await page.waitForFunction(() => { const video = document.querySelector('video.lesson-video'); return video && video.readyState >= 1 && Number.isFinite(video.duration) && video.duration > 0; });
    await page.locator('video.lesson-video').evaluate(video => video.play());
    assert.equal((await api(learner, 'GET', `learning/courses/${course.id}`)).completed, 0);
    // Same renderer in actual saved and authoring previews, including a changed preview response.
    await manage.goto(`${base}/?view=manage&course=${course.id}`);
    const row = manage.locator('.management-module');
    if (!(await row.evaluate(el => el.open))) await row.locator('summary').click();
    await row.getByRole('button', { name: 'Редактировать содержимое', exact: true }).click();
    await manage.getByRole('tab', { name: 'Предпросмотр', exact: true }).click();
    await manage.locator('.editor-preview .hljs-keyword').first().waitFor();
    assert.equal(await manage.locator('.editor-preview pre code').first().textContent(), java);
    await manage.getByRole('tab', { name: 'Markdown', exact: true }).click();
    await manage.getByRole('textbox', { name: 'Исходник Markdown' }).fill('```java\nint updated = 42;\n```');
    await manage.getByRole('tab', { name: 'Предпросмотр', exact: true }).click();
    await manage.locator('.editor-preview code').filter({ hasText: 'int updated = 42;' }).waitFor();
    assert.equal(await manage.locator('.editor-preview .lesson-code').count(), 1);
    manage.once('dialog', dialog => dialog.accept());
    await manage.getByRole('button', { name: 'Закрыть редактор', exact: true }).click();
    await manage.locator('.management-preview .hljs-keyword').first().waitFor();
    for (const theme of ['light', 'dark']) for (const width of [1440, 390, 320]) {
        await page.setViewportSize({ width, height: 900 });
        await page.evaluate(theme => { document.documentElement.dataset.theme = theme; }, theme);
        await page.locator('.lesson-code').first().scrollIntoViewIfNeeded();
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, `${theme} ${width}`);
        const pre = page.locator('.lesson-code pre').first();
        assert.equal(await pre.evaluate(el => el.scrollWidth > el.clientWidth), true);
        await page.screenshot({ path: join(artifacts, `lesson-${theme}-${width}.png`) });
    }
    // Management forms: precise validation, accessible errors, preserved input and disabled pending controls.
    await manage.goto(`${base}/?view=manage`);
    await manage.getByRole('button', { name: '+ Создать курс', exact: true }).click();
    const form = manage.getByRole('dialog'), title = form.getByLabel('Название курса', { exact: true });
    await form.getByRole('button', { name: 'Создать курс', exact: true }).click();
    assert.equal(await title.getAttribute('aria-invalid'), 'true');
    assert.equal(await form.locator('#management-title-error').innerText(), 'Введите название курса');
    await title.fill('Не терять название'); await form.getByLabel('Описание', { exact: true }).fill('Не терять описание');
    await manage.route('**/api/backend/courses', route => route.request().method() === 'POST' ? route.abort('failed') : route.continue(), { times: 1 });
    await form.getByRole('button', { name: 'Создать курс', exact: true }).click();
    await form.getByRole('alert').filter({ hasText: 'Не удалось подтвердить результат' }).waitFor();
    assert.equal(await title.inputValue(), 'Не терять название');
    assert.equal(await title.getAttribute('aria-invalid'), 'false');
    assert.equal(await form.getByLabel('Описание', { exact: true }).inputValue(), 'Не терять описание');
    // Force a genuine server field error while leaving valid client input untouched.
    await manage.route('**/api/backend/courses', async route => {
        if (route.request().method() !== 'POST') return route.continue();
        const response = await route.fetch({ postData: { title: 'x'.repeat(256), description: '' } });
        assert.equal(response.status(), 400);
        assert.equal((await response.json()).fieldErrors.title, 'Size');
        await route.fulfill({ response });
    }, { times: 1 });
    await form.getByRole('button', { name: 'Создать курс', exact: true }).click();
    await form.getByRole('alert').filter({ hasText: 'не длиннее 255' }).waitFor();
    assert.equal(await title.getAttribute('aria-invalid'), 'true');
    await title.fill('Создан через форму');
    let release, arrived;
    const gate = new Promise(resolve => { release = resolve; });
    const pending = new Promise(resolve => { arrived = resolve; });
    let requests = 0;
    await manage.route('**/api/backend/courses', async route => {
        if (route.request().method() !== 'POST') return route.continue();
        requests++; arrived(); await gate; await route.continue();
    });
    await form.getByRole('button', { name: 'Создать курс', exact: true }).click(); await pending;
    assert.equal(await form.getByRole('button', { name: 'Создаём…', exact: true }).isDisabled(), true);
    assert.equal(await title.isDisabled(), true);
    assert.equal(await form.getByRole('button', { name: 'Отмена', exact: true }).isDisabled(), true);
    release(); await form.waitFor({ state: 'hidden' }); await manage.unroute('**/api/backend/courses');
    assert.equal(requests, 1);
    await manage.getByRole('heading', { name: 'Создан через форму', exact: true }).waitFor();
    for (const [button, label, expected] of [
        ['Изменить название и описание', 'Название курса', 'Введите название курса'],
        ['+ Создать модуль', 'Название модуля', 'Введите название модуля'],
    ]) {
        await manage.getByRole('button', { name: button, exact: true }).click();
        await form.getByLabel(label, { exact: true }).fill(' ');
        await form.locator('button[type=submit]').click();
        assert.equal(await form.getByRole('alert').innerText(), expected);
        await manage.keyboard.press('Escape');
    }
    await manage.getByRole('button', { name: '+ Добавить существующий модуль', exact: true }).click();
    await form.getByRole('button', { name: 'Добавить в программу', exact: true }).click();
    assert.equal(await form.getByRole('alert').innerText(), 'Выберите модуль');
    assert.equal(await form.getByLabel('Модуль', { exact: true }).getAttribute('aria-invalid'), 'true');
    await manage.keyboard.press('Escape');
    const draftId = new URL(manage.url()).searchParams.get('course');
    assert.ok(draftId);
    const draftModule = await api(owner, 'POST', 'components', { title: 'Модуль для формы урока' }, 201);
    await api(owner, 'POST', `courses/${draftId}/components`, { componentId: draftModule.id }, 201);
    await manage.reload();
    for (const theme of ['light', 'dark']) for (const width of [1440, 390, 320]) {
        await manage.setViewportSize({ width, height: 900 });
        await manage.evaluate(theme => { document.documentElement.dataset.theme = theme; }, theme);
        await manage.getByRole('button', { name: '+ Создать урок', exact: true }).click();
        await form.getByRole('button', { name: 'Создать урок', exact: true }).click();
        assert.equal(await form.getByRole('alert').innerText(), 'Введите название урока');
        assert.equal(await form.getByLabel('Название урока', { exact: true }).getAttribute('aria-invalid'), 'true');
        assert.equal(await manage.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
        assert.equal(await form.evaluate(el => el.scrollWidth <= el.clientWidth), true);
        const border = await form.getByLabel('Название урока', { exact: true }).evaluate(el => {
            const probe = document.createElement('span'); probe.style.color = 'var(--error)'; document.body.append(probe);
            const expected = getComputedStyle(probe).color; probe.remove();
            return { actual: getComputedStyle(el).borderTopColor, expected };
        });
        assert.equal(border.actual, border.expected);
        await manage.screenshot({ path: join(artifacts, `form-${theme}-${width}.png`) });
        await manage.evaluate(() => { document.documentElement.style.fontSize = '200%'; });
        assert.equal(await manage.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
        assert.equal(await form.evaluate(el => el.scrollWidth <= el.clientWidth), true);
        await manage.evaluate(() => document.documentElement.style.removeProperty('font-size'));
        await manage.keyboard.press('Escape');
    }
    assert.deepEqual(errors, []);
    console.log('PASS: actual assignment navigation/focus/retry, no focus stealing on refresh, highlighted/numbered code and exact copy, safe plain fallback, shared previews, real video retry/playback, download retry/name/bytes, accessible form errors and pending protection, 6 theme/viewport combinations.');
});
