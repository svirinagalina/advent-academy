import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import { withSmokeBrowser } from './smoke-browser.mjs';

const fixture = JSON.parse(readFileSync(process.env.ADVENT_EDITOR_FIXTURE, 'utf8'));
const base = process.env.ADVENT_TEST_ORIGIN;
const project = process.env.ADVENT_TEST_PROJECT;
assert.ok(/^advent-(editor|user-admin)-e2e-/.test(project));
assert.ok(['localhost', '127.0.0.1'].includes(new URL(base).hostname));

await withSmokeBrowser('instructor', async browser => {
    async function session(who) {
        const context = await browser.newContext({ permissions: ['camera', 'microphone'] });
        await context.addInitScript(() => {
            window.__meetingPeers = [];
            const Native = window.RTCPeerConnection;
            window.RTCPeerConnection = new Proxy(Native, { construct(target, args) {
                const peer = new target(...args); window.__meetingPeers.push(peer); return peer;
            }});
        });
        const result = await context.request.post(base + '/api/session', {
            headers: { Origin: base }, data: { email: fixture.users[who].email, password: fixture.password }
        });
        assert.equal(result.status(), 200); return context;
    }
    const owner = await session('editor'), teacher = await session('foreign'), student = await session('student');
    async function api(context, path, method = 'GET', data, expected = 200) {
        const response = await context.request.fetch(base + '/api/backend/' + path, {
            method, headers: { Origin: base }, data
        });
        const text = await response.text(); assert.equal(response.status(), expected, `${path}: ${text}`);
        return text ? JSON.parse(text) : null;
    }
    const course = await api(owner, 'courses', 'POST', { title: 'Instructor delegation regression' }, 201);
    const component = await api(owner, 'components', 'POST', { title: 'Delegated module' }, 201);
    const module = await api(owner, `components/${component.id}/lessons`, 'POST', { title: 'Required review' }, 201);
    const lesson = module.lessons[0].id;
    await api(owner, `lessons/${lesson}/assignment/configuration`, 'PUT', { required: true, title: 'Task', description: 'Review the API', version: 0 });
    await api(owner, `lessons/${lesson}/content/draft`, 'PUT', { markdown: '# Delegated lesson', version: 0 });
    await api(owner, `lessons/${lesson}/content/draft/publish`, 'POST', { version: 1 });
    await api(owner, `components/${component.id}/publish`, 'POST');
    await api(owner, `courses/${course.id}/components`, 'POST', { componentId: component.id }, 201);
    await api(owner, `courses/${course.id}/publish`, 'POST');
    await api(owner, `learning/courses/${course.id}/students/${fixture.users.student.id}`, 'PUT');
    await api(teacher, `users/teaching/courses/${course.id}/students`, 'GET', undefined, 403);
    const before = await api(owner, `courses/${course.id}`);
    await api(teacher, `courses/${course.id}/instructor`, 'PUT', { instructorId: fixture.users.foreign.id, version: before.version }, 403);
    const page = await owner.newPage(); await page.goto(`${base}/?view=manage&course=${course.id}`);
    for (const width of [1440, 390, 320]) {
        await page.setViewportSize({ width, height: 900 });
        await page.getByRole('button', { name: 'Назначить преподавателя', exact: true }).click();
        const dialog = page.getByRole('dialog'); await dialog.waitFor();
        assert.equal(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth), true);
        await page.keyboard.press('Escape'); await dialog.waitFor({ state: 'hidden' });
    }
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.getByRole('button', { name: 'Назначить преподавателя', exact: true }).click();
    await page.getByLabel('Имя или email преподавателя').fill(fixture.users.foreign.email);
    await page.getByRole('button', { name: 'Найти преподавателей', exact: true }).click();
    await page.getByRole('radio').check();
    await page.getByRole('button', { name: 'Назначить выбранного', exact: true }).click();
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    assert.equal((await api(owner, `courses/${course.id}`)).ownerId, fixture.users.foreign.id);
    await api(owner, `courses/${course.id}/instructor`, 'PUT', { instructorId: fixture.users.foreign.id, version: before.version }, 409);
    const roster = await api(teacher, `users/teaching/courses/${course.id}/students`);
    assert.ok(roster.some(person => person.id === fixture.users.student.id));
    const submission = await api(student, `learning/courses/${course.id}/lessons/${lesson}/submissions`, 'POST', {
        requestId: randomUUID(), url: 'https://example.test/submission', comment: 'Ready'
    });
    await api(teacher, `teaching/submissions/${submission.attempts[0].id}/review`, 'POST', { status: 'accepted', feedback: 'Approved', version: 0 });
    assert.equal((await api(student, `learning/courses/${course.id}`)).completed, 1);
    console.log('PASS: real UI delegation, scoped roster, student submission and instructor review, version conflict');

    const meeting = await api(teacher, 'meetings/scheduled', 'POST', {
        requestId: randomUUID(), courseId: course.id, title: 'Delegated live meeting',
        startsAt: new Date(Date.now() + 60000).toISOString(), endsAt: new Date(Date.now() + 3600000).toISOString(),
        participants: [fixture.users.student.id]
    });
    await api(teacher, `meetings/scheduled/${meeting.id}/start`, 'POST', { version: meeting.version });
    const pages = await Promise.all([teacher.newPage(), student.newPage()]);
    for (const p of pages) { await p.goto(base + '/?view=meetings'); await p.getByRole('button', { name: 'Подключиться', exact: true }).click(); }
    async function connected(p) {
        for (let attempt = 0; attempt < 60; attempt++) {
            const live = await p.evaluate(async () => {
                const videos = [...document.querySelectorAll('.call-grid video')];
                if (videos.length !== 2 || !videos.every(video => video.readyState >= 2 && video.getVideoPlaybackQuality().totalVideoFrames > 3)) return false;
                const peers = window.__meetingPeers.filter(peer => peer.connectionState === 'connected');
                if (peers.length !== 1) return false;
                const stats = await peers[0].getStats();
                return [...stats.values()].some(stat => stat.type === 'inbound-rtp' && stat.kind === 'audio' && stat.packetsReceived > 0);
            });
            if (live) return;
            await new Promise(resolve => setTimeout(resolve, 500));
        }
        throw new Error('Live video frames and inbound audio RTP missing after 30 seconds');
    }
    await Promise.all(pages.map(connected));
    const transport = `${project}-meeting-service-1`;
    const metadata = JSON.parse(execFileSync('docker', ['inspect', transport], { encoding: 'utf8' }))[0];
    assert.equal(metadata.Config.Labels['com.docker.compose.project'], project);
    execFileSync('docker', ['restart', transport], { stdio: 'pipe' });
    for (let i = 0; i < 40; i++) {
        if ((await teacher.request.get(base + '/api/backend/meetings/config')).ok()) break;
        await new Promise(resolve => setTimeout(resolve, 500));
    }
    for (const p of pages) { await p.getByRole('button', { name: 'Переподключиться', exact: true }).click(); }
    await Promise.all(pages.map(connected));
    console.log('PASS: two-browser video/audio and simultaneous reconnect after actual meeting-service restart');

    await page.getByRole('button', { name: 'Назначить преподавателя', exact: true }).click();
    await page.getByRole('button', { name: 'Снять назначение и передать курс себе', exact: true }).click();
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    await api(teacher, `users/teaching/courses/${course.id}/students`, 'GET', undefined, 403);
    await api(teacher, `meetings/scheduled/${meeting.id}/capabilities`, 'POST', undefined, 403);
    for (const p of pages) {
        for (let i = 0; i < 45; i++) {
            if (await p.evaluate(() => window.__meetingPeers.every(peer => peer.connectionState === 'closed'))) break;
            await new Promise(resolve => setTimeout(resolve, 500));
        }
        assert.equal(await p.evaluate(() => window.__meetingPeers.every(peer => peer.connectionState === 'closed')), true);
    }
    console.log('PASS: removal of course delegation refuses REST and closes existing scheduled signaling/media in the app');
}, ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream', '--autoplay-policy=no-user-gesture-required']);
