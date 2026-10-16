// Real React UI; API responses are synthetic and no backend data is changed.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { withSmokeBrowser } from './smoke-browser.mjs';
const base = process.env.ADVENT_TEST_ORIGIN || 'http://127.0.0.1:13026';
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(base).hostname));
await withSmokeBrowser('ui-ux', async browser => {
    const context = await browser.newContext();
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    let learningRequests = 0, logoutRequests = 0, failLearning = true, failLogout = true;
    const profile = { id: 'audit', firstName: 'Редактор', lastName: 'Тест', email: 'ui@example.test', roles: ['ADMIN'] };
    await context.route('**/api/**', async route => {
        const request = route.request(), path = new URL(request.url()).pathname;
        if (path === '/api/session') {
            if (request.method() === 'DELETE') {
                logoutRequests++;
                await new Promise(resolve => setTimeout(resolve, 150));
                return route.fulfill({ status: failLogout ? 503 : 204, ...(failLogout ? { json: { message: 'Не удалось выйти. Повторите попытку.' } } : {}) });
            }
            return route.fulfill({ json: profile });
        }
        if (path.includes('/learning/')) {
            learningRequests++;
            return route.fulfill(failLearning ? { status: 503, json: { message: 'Учебный API недоступен' } } : { json: [] });
        }
        return route.fulfill({ json: { items: [], total: 0, page: 0, size: 20 } });
    });
    for (const view of ['manage', 'users']) {
        await page.goto(`${base}/?view=${view}`);
        await page.getByRole('heading', { name: view === 'manage' ? 'Курсы' : 'Пользователи', exact: true }).waitFor();
    }
    assert.equal(learningRequests, 0, 'Administrative screens must not request learner data');
    await page.goto(`${base}/cabinet`);
    await page.getByRole('alert').filter({ hasText: 'Учебный API недоступен' }).waitFor();
    await page.goto(`${base}/?view=manage`);
    await page.getByRole('heading', { name: 'Курсы', exact: true }).waitFor();
    await page.setViewportSize({ width: 390, height: 900 });
    await page.getByRole('link', { name: 'Кабинет ученика', exact: true }).click();
    await page.getByRole('alert').filter({ hasText: 'Учебный API недоступен' }).waitFor();
    await page.goBack();
    await page.getByRole('heading', { name: 'Курсы', exact: true }).waitFor();
    for (const theme of ['light', 'dark']) for (const width of [320, 390, 768, 1024, 1440]) {
        await page.emulateMedia({ colorScheme: theme });
        await page.setViewportSize({ width, height: 900 });
        await page.getByRole('link', { name: 'Кабинет ученика', exact: true }).filter({ visible: true }).waitFor();
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `Management overflow: ${theme} ${width}`);
    }
    const create = page.getByRole('button', { name: '+ Создать курс', exact: true });
    await create.click();
    await page.getByLabel('Название курса', { exact: true }).fill('Сохранить мой ввод');
    const dismiss = dialog => dialog.dismiss();
    page.on('dialog', dismiss);
    await page.keyboard.press('Escape');
    assert.equal(await page.getByLabel('Название курса', { exact: true }).inputValue(), 'Сохранить мой ввод');
    await page.getByRole('button', { name: 'Отмена', exact: true }).click();
    assert.equal(await page.getByRole('dialog').count(), 1);
    page.off('dialog', dismiss);
    page.once('dialog', dialog => dialog.accept());
    await page.keyboard.press('Escape');
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    await create.click();
    assert.equal(await page.getByLabel('Название курса', { exact: true }).inputValue(), '');
    await page.keyboard.press('Escape');
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    await page.getByRole('button', { name: 'Выйти', exact: true }).click();
    await page.getByRole('button', { name: 'Выходим…', exact: true }).waitFor();
    assert.ok(await page.getByRole('button', { name: 'Выходим…', exact: true }).isDisabled());
    await page.getByRole('alert').filter({ hasText: 'Не удалось выйти' }).waitFor();
    assert.equal(logoutRequests, 1);
    failLogout = false;
    await page.getByRole('button', { name: 'Выйти', exact: true }).click();
    await page.getByRole('heading', { name: 'Войти в кабинет', exact: true }).waitFor();
    failLearning = false;
    for (const role of ['ADMIN', 'CONTENT_EDITOR', 'INSTRUCTOR', 'STUDENT']) {
        profile.roles = [role];
        for (const theme of ['light', 'dark']) for (const width of [320, 390, 768, 1024, 1440]) {
            await page.emulateMedia({ colorScheme: theme });
            await page.setViewportSize({ width, height: 900 });
            await page.goto(`${base}/?view=meetings`);
            await page.getByRole('heading', { name: 'Онлайн-занятия', exact: true }).waitFor();
            assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `Role header overflow: ${role} ${theme} ${width}`);
            const box = await page.getByRole('button', { name: 'Меню пользователя Редактор', exact: true }).boundingBox();
            assert.ok(box.x >= 0 && box.x + box.width <= width, `Profile outside viewport: ${role} ${width}`);
        }
    }
    await context.close();
    const recovery = await browser.newContext();
    const form = await recovery.newPage();
    let payload, status = 429;
    await recovery.route('**/api/recovery', async route => {
        payload = route.request().postDataJSON();
        await route.fulfill({ status, json: {} });
    });
    await form.goto(`${base}/auth/confirm-email`);
    await form.getByLabel('Email', { exact: true }).fill('pending@example.test');
    const resend = form.getByRole('button', { name: 'Отправить письмо подтверждения', exact: true });
    await resend.click();
    await form.getByRole('alert').filter({ hasText: 'Слишком много попыток' }).waitFor();
    assert.equal(payload.action, 'resend');
    assert.equal(payload.email, 'pending@example.test');
    assert.equal(await form.getByLabel('Email', { exact: true }).inputValue(), 'pending@example.test');
    status = 200;
    await resend.click();
    await form.getByRole('status').filter({ hasText: 'письмо подтверждения' }).waitFor();
    assert.equal(await form.getByRole('link', { name: 'Запросить новую ссылку сброса пароля' }).count(), 0);
    status = 400;
    await form.goto('about:blank');
    await form.goto(`${base}/auth/confirm-email#token=expired`);
    await form.getByRole('button', { name: 'Подтвердить email', exact: true }).click();
    await form.getByRole('alert').filter({ hasText: 'Ссылка недействительна' }).waitFor();
    await form.getByRole('button', { name: 'Запросить новое письмо подтверждения', exact: true }).click();
    await form.getByLabel('Email', { exact: true }).waitFor();
    await recovery.close();
    // Render the actual standalone meeting CSS without starting a second service.
    const meeting = await browser.newContext();
    const staticPage = await meeting.newPage();
    await meeting.route('**/audit-meeting', route => route.fulfill({ contentType: 'text/html', body: readFileSync('../meeting-service/src/main/resources/static/index.html', 'utf8') }));
    await meeting.route('**/styles.css', route => route.fulfill({ contentType: 'text/css', body: readFileSync('../meeting-service/src/main/resources/static/styles.css', 'utf8') }));
    await meeting.route('**/js/**', route => route.abort());
    await meeting.route('**/vendor/**', route => route.abort());
    await staticPage.goto(`${base}/audit-meeting`);
    const contrast = await staticPage.locator('#create-room').evaluate(el => {
        const luminance = color => color.match(/[\d.]+/g).slice(0, 3).map(Number).map(v => v / 255).map(v => v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4).reduce((sum, v, i) => sum + v * [.2126, .7152, .0722][i], 0);
        const style = getComputedStyle(el), a = luminance(style.color), b = luminance(style.backgroundColor);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    });
    assert.ok(contrast >= 4.5, `Meeting button contrast: ${contrast}`);
    await meeting.close();
    if (process.env.ADVENT_TEST_SMTP && process.env.ADVENT_EDITOR_FIXTURE) {
        const gateway = process.env.API_GATEWAY_URL, smtp = process.env.ADVENT_TEST_SMTP;
        for (const url of [gateway, smtp]) assert.ok(['localhost', '127.0.0.1'].includes(new URL(url).hostname));
        const fixture = JSON.parse(readFileSync(process.env.ADVENT_EDITOR_FIXTURE, 'utf8'));
        assert.ok(fixture.users.editor.email.endsWith('@example.test'));
        const real = await browser.newContext(), pending = await real.newPage();
        const email = `verification-${crypto.randomUUID()}@example.test`, password = `Verify-2026!${crypto.randomUUID()}`;
        const registered = await real.request.post(gateway + '/api/auth/register', { data: { email, password, firstName: 'UI', lastName: 'Verification' } });
        assert.equal(registered.status(), 201);
        await pending.goto(`${base}/auth/confirm-email`);
        await pending.getByLabel('Email', { exact: true }).fill(email);
        await pending.getByRole('button', { name: 'Отправить письмо подтверждения', exact: true }).click();
        await pending.getByRole('status').filter({ hasText: 'письмо подтверждения' }).waitFor();
        const letters = await (await real.request.get(smtp + '/messages')).json();
        const verification = letters.filter(m => m.to === email && m.subject.startsWith('Verify your email')).at(-1);
        assert.ok(verification, 'Real verification email missing');
        const link = new URL(verification.text.match(/https?:\/\/\S+/)[0]);
        assert.equal(link.origin, base);
        assert.equal(link.pathname, '/auth/confirm-email');
        await pending.goto('about:blank');
        await pending.goto(link.href);
        await pending.getByRole('button', { name: 'Подтвердить email', exact: true }).click();
        await pending.getByRole('status').filter({ hasText: 'Email подтверждён' }).waitFor();
        const login = await real.request.post(base + '/api/session', { headers: { Origin: base }, data: { email, password } });
        assert.equal(login.status(), 200);
        await real.close();
        console.log('PASS: real verification resend via BFF, SMTP delivery, confirmation and login');
    }
    assert.deepEqual(errors, []);
    console.log('PASS: administrative isolation, responsive role headers, mobile return, dirty form protection, logout recovery, email verification resend, meeting contrast');
});
