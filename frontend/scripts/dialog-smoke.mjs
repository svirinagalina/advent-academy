// Real local frontend and BFF; only synthetic fixtures may be used.
import { withSmokeBrowser } from './smoke-browser.mjs';
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
const base = process.env.ADVENT_TEST_ORIGIN || 'http://127.0.0.1:13024';
assert.ok(['localhost', '127.0.0.1'].includes(new URL(base).hostname));
const fixture = JSON.parse(readFileSync(process.env.ADVENT_EDITOR_FIXTURE || '/tmp/advent-editor-fixture.json', 'utf8'));
assert.ok(fixture.users.editor.email.endsWith('@example.test'));
await withSmokeBrowser('dialog', async browser => {
async function api(context, path, method = 'GET', data) {
    const response = await context.request.fetch(`${base}/api/backend/${path}`, { method, headers: { Origin: base }, data });
    const text = await response.text();
    assert.ok(response.ok(), `${method} ${path}: ${text}`);
    return text ? JSON.parse(text) : null;
}
async function centered(page, label, close = true) {
    const dialog = page.getByRole('dialog');
    await dialog.waitFor();
    // Initial focus can scroll the viewport; measure after the dialog has opened.
    const box = await dialog.boundingBox(), viewport = page.viewportSize();
    assert.ok(Math.abs(box.x + box.width / 2 - viewport.width / 2) <= 2, `${label}: horizontal ${JSON.stringify(box)}`);
    assert.ok(Math.abs(box.y + box.height / 2 - viewport.height / 2) <= 2, `${label}: vertical ${JSON.stringify(box)}`);
    assert.ok(box.x >= -1 && box.y >= -1, label);
    assert.ok(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth), `${label}: overflow`);
    if (close) {
        await page.keyboard.press('Escape');
        await dialog.waitFor({ state: 'hidden' });
    }
}
    for (const theme of ['light', 'dark']) for (const width of [1440, 390, 320]) {
        const context = await browser.newContext({ viewport: { width, height: 900 }, colorScheme: theme });
        try {
            const login = await context.request.post(`${base}/api/session`, { headers: { Origin: base }, data: { email: fixture.users.editor.email, password: fixture.password } });
            assert.equal(login.status(), 200);
            const page = await context.newPage(); page.setDefaultTimeout(15000);
            const errors = []; page.on('pageerror', error => errors.push(error.message));
            await page.goto(`${base}/?view=manage`);
            await page.getByRole('button', { name: '+ Создать курс', exact: true }).click();
            await page.getByLabel('Название курса', { exact: true }).fill('Несохранённое название');
            await centered(page, 'create course', false);
            assert.equal(await page.getByLabel('Название курса', { exact: true }).inputValue(), 'Несохранённое название');
            page.once('dialog', dialog => dialog.accept());
            await page.keyboard.press('Escape');
            await page.getByRole('dialog').waitFor({ state: 'hidden' });
            const course = await api(context, 'courses', 'POST', { title: `Dialog check ${theme} ${width}` });
            const module = await api(context, 'components', 'POST', { title: 'Dialog module' });
            await api(context, `components/${module.id}/lessons`, 'POST', { title: 'Dialog lesson' });
            await api(context, `courses/${course.id}/components`, 'POST', { componentId: module.id });
            await page.goto(`${base}/?view=manage&course=${course.id}`);
            for (const label of ['Изменить название и описание', '+ Создать модуль', '+ Добавить существующий модуль', '+ Создать урок', 'Убрать из программы']) {
                await page.getByRole('button', { name: label, exact: true }).click();
                await centered(page, label);
            }
            await page.getByRole('button', { name: 'Редактировать содержимое', exact: true }).click();
            await page.getByRole('textbox', { name: 'Исходник Markdown' }).waitFor();
            await centered(page, 'lesson editor', false);
            await page.getByRole('tab', { name: 'Предпросмотр', exact: true }).click();
            assert.equal(await page.getByRole('textbox', { name: 'Исходник Markdown' }).isVisible(), false);
            await centered(page, 'lesson preview');
            const published = (await api(context, 'courses?managed=true')).find(c => c.status === 'PUBLISHED');
            assert.ok(published, 'Run test:editor first to create a published course');
            await page.goto(`${base}/?view=manage&course=${published.id}`);
            await page.getByRole('button', { name: 'Архивировать курс', exact: true }).click();
            await centered(page, 'archive');
            await page.goto(`${base}/?view=meetings`);
            await page.getByRole('button', { name: 'Запланировать занятие', exact: true }).click();
            await centered(page, 'meeting editor');
            await page.goto(`${base}/demo`);
            await page.getByRole('button', { name: 'Подробнее', exact: true }).click();
            await centered(page, 'meeting details');
            if (width < 1024) {
                await page.getByRole('link', { name: 'Продолжить обучение', exact: true }).click();
                await page.getByRole('button', { name: 'Программа курса', exact: true }).click();
                await centered(page, 'course program');
            }
            assert.deepEqual(errors, []);
            console.log(`PASS: dialogs centered, preview switches, no overflow: ${theme} ${width}px`);
        } finally { await context.close(); }
    }
});
