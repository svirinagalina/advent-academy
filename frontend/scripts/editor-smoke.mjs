// Real app + real content/user services. Never run against a production deployment.
import { withSmokeBrowser } from './smoke-browser.mjs';
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
const base = process.env.ADVENT_TEST_ORIGIN || 'http://127.0.0.1:3000';
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname));
await withSmokeBrowser('editor', async browser => {
    const fixture = JSON.parse(readFileSync(process.env.ADVENT_EDITOR_FIXTURE || '/tmp/management-browser-fixture.json', 'utf8'));
    const contexts = [];
    async function session(who) {
        const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } }); contexts.push(context);
        const response = await context.request.post(`${base}/api/session`, { headers: { Origin: base }, data: { email: fixture.users[who].email, password: fixture.password } });
        assert.equal(response.status(), 200, await response.text());
        return context;
    }
    async function api(context, method, path, data, expected = 200) {
        const response = await context.request.fetch(`${base}/api/backend/${path}`, { method, headers: { Origin: base }, ...(data === undefined ? {} : { data }) });
        const body = await response.text();
        assert.equal(response.status(), expected, body);
        return body ? JSON.parse(body) : null;
    }
    const owner = await session('editor');
    const page = await owner.newPage(); const errors = []; page.on('pageerror', e => errors.push(e.message));
    const course = await api(owner, 'POST', 'courses', { title: 'Markdown browser verification' }, 201);
    const module = await api(owner, 'POST', 'components', { title: 'Markdown editor' }, 201);
    const withLesson = await api(owner, 'POST', `components/${module.id}/lessons`, { title: 'Editable lesson' }, 201);
    const lesson = withLesson.lessons[0].id;
    const path = `lessons/${lesson}/content/draft`;
    await api(owner, 'POST', `courses/${course.id}/components`, { componentId: module.id }, 201);
    async function open(target) {
        await target.goto(`${base}/?view=manage&course=${course.id}`);
        const row = target.locator('.management-module');
        if (!(await row.evaluate(element => element.open))) {
            await row.locator('summary').click();
        }
        await row.getByRole('button', { name: 'Редактировать содержимое', exact: true }).click();
        await target.getByRole('dialog').waitFor();
        await target.getByRole('textbox', { name: 'Исходник Markdown' }).waitFor();
    }
    const source = target => target.getByRole('textbox', { name: 'Исходник Markdown' });
    // CodeMirror uses a <br> in empty lines; innerText adds extra visual newlines.
    const sourceText = async target => (await source(target).locator('.cm-line').allTextContents()).join('\n');
    const editor = target => target.getByRole('dialog');
    const save = async target => {
        await editor(target).getByRole('button', { name: 'Сохранить черновик', exact: true }).click();
        await editor(target).getByText('Черновик сохранён. Опубликованный материал не изменился.', { exact: true }).waitFor();
    };
    await open(page);
    const first = '# From browser\n\n**Bold** and *italic*.\n\n<script>alert(1)</script>';
    await source(page).fill(first); await save(page);
    assert.equal((await api(owner, 'GET', path)).markdown, first);
    await editor(page).getByRole('button', { name: 'Закрыть редактор' }).click();
    await page.getByRole('button', { name: 'Редактировать содержимое', exact: true }).click();
    assert.equal(await sourceText(page), first);
    await editor(page).getByRole('tab', { name: 'Предпросмотр', exact: true }).click();
    await editor(page).frameLocator('.editor-preview iframe').locator('h1').waitFor();
    assert.equal(await source(page).isVisible(), false);
    assert.equal(await editor(page).locator('.editor-preview .lesson-material').isVisible(), true);
    assert.equal(await editor(page).frameLocator('.editor-preview iframe').locator('script').count(), 0);
    // Simulate a sanitizer regression at the real BFF response boundary: the browser still refuses script/event execution.
    await editor(page).getByRole('tab', { name: 'Markdown', exact: true }).click();
    await page.route('**/api/backend/lessons/*/content/draft/preview', async route => {
        const response = await route.fetch(); const payload = await response.json();
        payload.html = '<h1>Containment check</h1><script>parent.document.documentElement.dataset.unsafeContent="script"</script><img src="/missing-test-image" onerror="parent.document.documentElement.dataset.unsafeContent=\'event\'">';
        await route.fulfill({response,json:payload});
    });
    await source(page).fill('# Containment check');
    await editor(page).getByRole('tab', { name: 'Предпросмотр', exact: true }).click();
    await editor(page).frameLocator('.editor-preview iframe').locator('h1').filter({hasText:'Containment check'}).waitFor();
    assert.equal(await page.evaluate(() => document.documentElement.dataset.unsafeContent),undefined);
    const permissions = await editor(page).locator('.editor-preview iframe').getAttribute('sandbox');
    assert.ok(!permissions.includes('allow-scripts') && !permissions.includes('allow-forms'));
    await page.unroute('**/api/backend/lessons/*/content/draft/preview');
    await editor(page).getByRole('tab', { name: 'Markdown', exact: true }).click();
    await source(page).fill(first);
    const preview = await api(owner, 'POST', `${path}/preview`, { markdown: first });
    await editor(page).getByRole('tab', { name: 'HTML', exact: true }).click();
    await editor(page).getByLabel('Сгенерированный HTML').waitFor();
    await editor(page).getByRole('tab', { name: 'Markdown', exact: true }).click();
    assert.equal(await sourceText(page), first);
    assert.equal(await editor(page).locator('.editor-preview').isVisible(), false);
    await editor(page).getByRole('checkbox', { name: 'Markdown и предпросмотр рядом' }).check();
    assert.equal(await source(page).isVisible(), true);
    assert.equal(await editor(page).locator('.editor-preview').isVisible(), true);
    await editor(page).getByRole('tab', { name: 'Предпросмотр', exact: true }).click();
    assert.equal(await source(page).isVisible(), false);
    await editor(page).getByRole('tab', { name: 'Markdown', exact: true }).click();
    await editor(page).getByRole('button', { name: 'Опубликовать', exact: true }).click();
    await editor(page).getByText('Материал опубликован. Модуль и курс публикуются отдельно.', { exact: true }).waitFor();
    await api(owner, 'POST', `components/${module.id}/publish`);
    await api(owner, 'POST', `courses/${course.id}/publish`);
    await api(owner, 'PUT', `learning/courses/${course.id}/students/${fixture.users.student.id}`);
    const learner = await session('student');
    const learnerPath = `learning/courses/${course.id}/lessons/${lesson}`;
    assert.equal((await api(learner, 'GET', learnerPath)).html, preview.html);
    await source(page).fill('# Draft only'); await save(page);
    assert.equal((await api(learner, 'GET', learnerPath)).html, preview.html);
    await editor(page).getByLabel('Вставить изображение', { exact: true }).setInputFiles({ name: 'pixel.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jk1sAAAAASUVORK5CYII=', 'base64') });
    await page.waitForFunction(() => document.querySelector('.editor-markdown .cm-content')?.textContent.includes('asset:'));
    await save(page);
    const imageText = (await api(owner, 'GET', path)).markdown;
    await editor(page).getByRole('button', { name: 'Закрыть редактор' }).click();
    await page.getByRole('button', { name: 'Редактировать содержимое', exact: true }).click();
    assert.equal(await sourceText(page), imageText);
    await editor(page).getByRole('checkbox', { name: 'Markdown и предпросмотр рядом' }).check();
    await editor(page).frameLocator('.editor-preview iframe').locator('img').waitFor();
    await page.waitForFunction(() => { const image = document.querySelector('.editor-preview iframe')?.contentDocument?.querySelector('img'); return image?.complete && image.naturalWidth > 0; });
    await editor(page).getByRole('button', { name: 'Опубликовать', exact: true }).click();
    await editor(page).getByText('Материал опубликован. Модуль и курс публикуются отдельно.', { exact: true }).waitFor();
    assert.match((await api(learner, 'GET', learnerPath)).html, /<img/);
    // Two real browser editors preserve the losing editor's source on a real server 409.
    const peer = await owner.newPage(); await open(peer);
    await source(page).fill('# Winning edit'); await save(page);
    await source(peer).fill('# Local conflict text');
    await editor(peer).getByRole('button', { name: 'Сохранить черновик', exact: true }).click();
    await editor(peer).getByRole('alert').filter({ hasText: 'Материал изменён в другом редакторе' }).waitFor();
    assert.equal(await sourceText(peer), '# Local conflict text');
    const download = peer.waitForEvent('download');
    await editor(peer).getByRole('button', { name: 'Скачать Markdown' }).click(); assert.equal((await download).suggestedFilename(), 'lesson.md');
    peer.once('dialog', dialog => dialog.dismiss());
    await editor(peer).getByRole('button', { name: 'Закрыть редактор' }).click(); assert.equal(await editor(peer).count(), 1);
    peer.once('dialog', dialog => dialog.accept()); await editor(peer).getByRole('button', { name: 'Закрыть редактор' }).click();
    // A network error must never replace the local text.
    await page.route(`**/api/backend/${path}`, route => route.request().method() === 'PUT' ? route.abort('failed') : route.continue());
    await source(page).fill('# Unsaved after failure');
    await editor(page).getByRole('button', { name: 'Сохранить черновик', exact: true }).click();
    await editor(page).getByRole('alert').filter({ hasText: 'Ваш текст остаётся' }).waitFor();
    assert.equal(await sourceText(page), '# Unsaved after failure');
    await page.unroute(`**/api/backend/${path}`);
    // A delayed, real server preview response must not replace newer edits.
    let releaseSlow; let receivedSlow; let completedSlow;
    const slowCompleted = new Promise(resolve => { completedSlow = resolve; });
    const slowArrived = new Promise(resolve => { receivedSlow = resolve; });
    const release = new Promise(resolve => { releaseSlow = resolve; });
    await page.route(`**/api/backend/${path}/preview`, async route => {
        if (route.request().postDataJSON().markdown === '# Slow preview') {
            const response = await route.fetch(); receivedSlow(); await release; await route.fulfill({ response }); completedSlow();
        } else await route.continue();
    });
    await source(page).fill('# Slow preview'); await slowArrived;
    await source(page).fill('# Fresh preview');
    await editor(page).frameLocator('.editor-preview iframe').locator('h1').filter({ hasText: 'Fresh preview' }).waitFor();
    releaseSlow();
    await slowCompleted;
    await page.unroute(`**/api/backend/${path}/preview`);
    assert.equal(await editor(page).frameLocator('.editor-preview iframe').locator('h1').innerText(), 'Fresh preview');
    await source(page).fill('# Unsaved after failure');
    // Import confirmation, cursor toolbar and Undo all act on the existing CodeMirror state.
    page.once('dialog', dialog => dialog.dismiss());
    await editor(page).getByLabel('Импорт .md', { exact: true }).setInputFiles({ name: 'import.md', mimeType: 'text/markdown', buffer: Buffer.from('# Imported locally') });
    assert.equal(await sourceText(page), '# Unsaved after failure');
    page.once('dialog', dialog => dialog.accept());
    await editor(page).getByLabel('Импорт .md', { exact: true }).setInputFiles({ name: 'import.md', mimeType: 'text/markdown', buffer: Buffer.from('# Imported locally') });
    await page.waitForFunction(() => document.querySelector('.editor-markdown .cm-content')?.textContent === '# Imported locally');
    await source(page).press('Control+z'); assert.equal(await sourceText(page), '# Unsaved after failure');
    await source(page).focus(); await source(page).press('Control+a');
    await editor(page).getByRole('button', { name: 'Жирный', exact: true }).click();
    assert.equal(await sourceText(page), '**# Unsaved after failure**');
    await source(page).press('Control+z');
    assert.equal(await sourceText(page), '# Unsaved after failure');
    const foreign = await session('foreign');
    await api(foreign, 'GET', path, undefined, 403);
    await api(foreign, 'PUT', path, { markdown: '# Denied', version: 0 }, 403);
    await api(foreign, 'POST', `${path}/preview`, { markdown: '# Denied' }, 403);
    await api(foreign, 'POST', `${path}/publish`, { version: 0 }, 403);
    // BFF still enforces same-origin protection and its path allowlist.
    assert.equal((await owner.request.put(`${base}/api/backend/${path}`, { headers: { Origin: 'https://foreign.example' }, data: { markdown: 'denied', version: 0 } })).status(), 403);
    assert.equal((await owner.request.get(`${base}/api/backend/arbitrary`)).status(), 404);
    for (const theme of ['light', 'dark']) {
        await page.evaluate(value => { document.documentElement.dataset.theme = value; }, theme);
        for (const width of [1440, 390, 320]) {
            await page.setViewportSize({ width, height: width === 1440 ? 1000 : 844 });
            await editor(page).getByRole('tab', { name: 'Предпросмотр', exact: true }).click();
            assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
            assert.equal(await editor(page).evaluate(el => el.scrollWidth <= el.clientWidth), true);
            await editor(page).getByRole('tab', { name: 'Markdown', exact: true }).click();
            assert.equal(await sourceText(page), '# Unsaved after failure');
        }
    }
    page.once('dialog', dialog => dialog.accept());
    await editor(page).getByRole('button', { name: 'Закрыть редактор' }).click();
    const zipCourse = await api(owner, 'POST', 'courses', { title: 'ZIP compatibility' }, 201);
    const zipModule = await api(owner, 'POST', 'components', { title: 'ZIP module' }, 201);
    const zipLesson = (await api(owner, 'POST', `components/${zipModule.id}/lessons`, { title: 'ZIP lesson' }, 201)).lessons[0].id;
    await api(owner, 'POST', `courses/${zipCourse.id}/components`, { componentId: zipModule.id }, 201);
    await page.goto(`${base}/?view=manage&course=${zipCourse.id}`);
    const zipRow = page.locator('.management-module');
    if (!(await zipRow.evaluate(element => element.open))) {
        await zipRow.locator('summary').click();
    }
    await page.getByRole('button', { name: 'Редактировать содержимое', exact: true }).click();
    await source(page).waitFor(); await editor(page).getByRole('button', { name: 'Закрыть редактор' }).click();
    await page.getByLabel('Markdown или ZIP с Markdown и вложениями').setInputFiles({ name: 'lesson.zip', mimeType: 'application/zip', buffer: Buffer.from('UEsDBBQAAAAAAFZPUF2JRvb4DAAAAAwAAAAJAAAAbGVzc29uLm1kIyBaSVAgaW1wb3J0UEsBAhQDFAAAAAAAVk9QXYlG9vgMAAAADAAAAAkAAAAAAAAAAAAAAIABAAAAAGxlc3Nvbi5tZFBLBQYAAAAAAQABADcAAAAzAAAAAAA=', 'base64') });
    await page.getByRole('button', { name: 'Загрузить материал', exact: true }).click();
    await page.frameLocator('.management-preview iframe').locator('h1').filter({ hasText: 'ZIP import' }).waitFor();
    await page.getByRole('button', { name: 'Редактировать содержимое', exact: true }).click();
    assert.equal(await sourceText(page), '# ZIP import');
    assert.equal((await api(owner, 'GET', `lessons/${zipLesson}/content/draft`)).markdown, '# ZIP import');
    assert.deepEqual(errors, []);
    console.log('PASS: real Markdown create/save/reopen/edit, server HTML and learner parity, publication isolation, image upload/reopen, concurrent editors, network recovery, import confirmation, Undo, download, ownership, BFF protection, themes, mobile and ZIP compatibility.');
    for (const context of contexts) await context.close();
});
