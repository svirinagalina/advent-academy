// Public pages use real server rendering; only the optional login fixture needs a backend.
import assert from 'node:assert/strict';
import { readFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { withSmokeBrowser } from './smoke-browser.mjs';
const base = process.env.ADVENT_TEST_ORIGIN || 'http://127.0.0.1:13026';
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname));
const pages = [
    ['/', 'Java: с нуля — к практике. С опытом — к глубине.'],
    ['/courses', 'Выбери направление и свой уровень'],
    ['/courses/java', 'Два маршрута. Одна цель — понимать свою работу.'],
    ['/courses/java/beginner', 'Java с нуля: начни с понимания.'],
    ['/courses/java/advanced', 'Знаешь Java. Научись разбираться в системе.'],
    ['/practice', 'Практика, в которой нужно разобраться'],
    ['/practice/event-redelivery', 'Одно событие. Две обработки. Как не повторить операцию?'],
    ['/about', 'Академия новая. Опыт — уже есть.'],
    ['/contacts', 'Давай обсудим обучение'],
    ['/privacy', 'Документы'],
];
const screenshots = resolve(process.env.ADVENT_TEST_ARTIFACTS || '../reports/public-site', 'public-site');
mkdirSync(screenshots, { recursive: true });
await withSmokeBrowser('public-site', async browser => {
    const context = await browser.newContext({ reducedMotion: 'reduce' });
    const page = await context.newPage();
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    let publicApiCalls = 0;
    page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/')) publicApiCalls++; });
    for (const theme of ['dark', 'light']) for (const width of [320, 390, 768, 1024, 1440]) {
        await page.emulateMedia({ colorScheme: theme, reducedMotion: 'reduce' });
        await page.setViewportSize({ width, height: 1000 });
        for (const [path, heading] of pages) {
            await page.goto(base + path);
            await page.getByRole('heading', { name: heading, exact: true }).waitFor();
            assert.equal(await page.locator('h1').count(), 1, path);
            assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `Overflow: ${path} ${theme} ${width}`);
            assert.equal(await page.locator('a[href="#"]').count(), 0, 'No dead-end links');
        }
        console.log(`PASS: public routes, headings and no page overflow: ${theme} ${width}px`);
    }
    assert.equal(publicApiCalls, 0, 'Public pages do not require authenticated APIs');
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.goto(base + '/');
    const coursesMenu = page.getByRole('button', { name: 'Курсы', exact: true });
    await coursesMenu.focus(); await page.keyboard.press('Enter');
    await page.locator('#public-course-menu').waitFor();
    await page.keyboard.press('Escape');
    assert.ok(await coursesMenu.evaluate(el => el === document.activeElement));
    await page.setViewportSize({ width: 390, height: 844 });
    const menu = page.getByRole('button', { name: 'Открыть меню', exact: true });
    await menu.click(); await page.getByRole('dialog').waitFor();
    assert.equal(await page.evaluate(() => document.body.style.overflow), 'hidden');
    await page.keyboard.press('Escape');
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    assert.ok(await menu.evaluate(el => el === document.activeElement));
    await menu.click();
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    assert.equal(await page.evaluate(() => document.body.style.overflow), '');
    await page.goto(base + '/courses');
    await page.getByLabel('Направление', { exact: true }).selectOption('go');
    await page.getByRole('heading', { name: 'Go-разработка', exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: 'Поступить на курс', exact: true }).count(), 1, 'Go has no enrollment action');
    await page.getByLabel('Подготовка', { exact: true }).selectOption('advanced');
    await page.getByRole('heading', { name: 'По этим параметрам программ пока нет' }).waitFor();
    await page.getByRole('button', { name: 'Сбросить фильтры', exact: true }).first().click();
    await page.getByLabel('Направление', { exact: true }).selectOption('java');
    await page.getByLabel('Подготовка', { exact: true }).selectOption('advanced');
    await page.getByRole('link', { name: 'Подробнее', exact: true }).click();
    await page.getByRole('heading', { name: 'Знаешь Java. Научись разбираться в системе.', exact: true }).waitFor();
    await page.getByRole('navigation', { name: 'Хлебные крошки' }).getByRole('link', { name: 'Курсы', exact: true }).click();
    assert.equal(await page.getByLabel('Направление', { exact: true }).inputValue(), 'java');
    assert.equal(await page.getByLabel('Подготовка', { exact: true }).inputValue(), 'advanced');
    await page.goto(base + '/courses/java/advanced');
    assert.ok(await page.locator('#module-4').evaluate(el => el.open));
    await page.getByText('Чужой код, Git и ревью', { exact: true }).click();
    assert.ok(await page.locator('#module-1').evaluate(el => el.open));
    assert.ok(await page.locator('#module-4').evaluate(el => el.open));
    const apply = page.getByRole('main').getByRole('button', { name: 'Поступить на курс', exact: true }).first();
    await apply.click();
    const dialog = page.getByRole('dialog');
    assert.equal(await dialog.getByLabel('Маршрут Java', { exact: true }).inputValue(), 'advanced');
    assert.ok(await dialog.getByLabel('Email', { exact: true }).isDisabled());
    assert.ok(await dialog.getByRole('button', { name: 'Отправить заявку', exact: true }).isDisabled());
    await dialog.getByLabel('Маршрут Java', { exact: true }).selectOption('beginner');
    await page.emulateMedia({ colorScheme: 'dark' });
    assert.equal(await dialog.getByLabel('Маршрут Java', { exact: true }).inputValue(), 'beginner');
    await page.keyboard.press('Escape'); await dialog.waitFor({ state: 'hidden' });
    assert.ok(await apply.evaluate(el => el === document.activeElement));
    for (const [name, path] of [['home','/'],['advanced','/courses/java/advanced'],['catalog','/courses'],['practice','/practice/event-redelivery']]) {
        for (const theme of ['dark', 'light']) for (const width of [1440, 390]) {
            await context.clearCookies();
            await page.emulateMedia({ colorScheme: theme });
            await page.setViewportSize({ width, height: 1000 });
            await page.goto(base + path);
            await page.getByRole('heading', { level: 1 }).waitFor();
            await page.screenshot({ path: resolve(screenshots, `${name}-${theme}-${width}.png`), fullPage: true });
        }
    }
    await page.goto(base + '/');
    await page.evaluate(() => document.documentElement.style.fontSize = '200%');
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), '200% text reflow');
    await page.goto(base + '/missing-public-page');
    await page.getByRole('heading', { name: 'Страница не найдена', exact: true }).waitFor();
    await context.close();
    const noJs = await browser.newContext({ javaScriptEnabled: false });
    const plain = await noJs.newPage();
    for (const [path, heading] of pages) {
        await plain.goto(base + path); await plain.getByRole('heading', { name: heading, exact: true }).waitFor();
        assert.ok((await plain.locator('main').innerText()).length > 100, `No-JS content: ${path}`);
    }
    await plain.goto(base + '/courses/java/advanced');
    await plain.locator('#module-1 summary').click();
    assert.ok(await plain.locator('#module-1').evaluate(el => el.open));
    await noJs.close();
    if (process.env.ADVENT_EDITOR_FIXTURE) {
        const fixture = JSON.parse(readFileSync(process.env.ADVENT_EDITOR_FIXTURE, 'utf8'));
        assert.ok(fixture.users.editor.email.endsWith('@example.test'));
        const real = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
        const login = await real.newPage();
        await login.goto(base + '/');
        await login.getByRole('link', { name: 'Войти', exact: true }).click();
        await login.waitForURL(base + '/login');
        assert.equal(new URL(login.url()).pathname, '/login');
        await login.getByLabel('Email', { exact: true }).fill(fixture.users.editor.email);
        await login.getByLabel('Пароль', { exact: true }).fill(fixture.password);
        await login.getByRole('button', { name: 'Войти', exact: true }).click();
        await login.waitForURL(base + '/cabinet');
        assert.equal((await real.request.get(base + '/api/session')).status(), 200);
        await login.getByRole('link', { name: 'Управление курсами', exact: true }).click();
        await login.getByRole('heading', { name: 'Курсы', exact: true }).waitFor();
        await real.close();
        console.log('PASS: public login button -> existing login -> real BFF session -> cabinet -> course management');
    }
    assert.deepEqual(errors, []);
    console.log('PASS: menus, focus return, breakpoint change, URL filters, module disclosure, honest application state, no-JS content and text reflow');
});
