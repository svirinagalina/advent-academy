import { chromium } from 'playwright';
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { createDecipheriv, createCipheriv, randomBytes } from 'node:crypto';
const fixture=JSON.parse(readFileSync(process.env.ADVENT_TEST_FIXTURE || '/tmp/advent-sprint-fixture.json','utf8'));
const base=process.env.ADVENT_TEST_ORIGIN || 'http://localhost:13007';
assert.ok(['localhost','127.0.0.1'].includes(new URL(base).hostname), 'Only an isolated local test server is supported');
assert.ok(fixture.email.endsWith('@example.test'), 'Use synthetic test accounts');
const browser=await chromium.launch({executablePath:process.env.CHROME_PATH || '/usr/bin/google-chrome',headless:true,args:['--no-sandbox']});
const context=await browser.newContext({viewport:{width:1440,height:1000}});
const page=await context.newPage();
const errors=[];page.on('pageerror',e=>errors.push(e.message));
const login=async(email=fixture.email)=>{
 await page.goto(base);await page.getByLabel('Email',{exact:true}).fill(email);await page.getByLabel('Пароль',{exact:true}).fill(fixture.password);await page.getByRole('button',{name:'Войти',exact:true}).click();
};
try{
 await login();await page.getByRole('heading',{name:'Привет, Мария!'}).waitFor();
 assert.equal(await page.locator('.demo-tools').count(),0);
 assert.equal(await page.evaluate(()=>localStorage.getItem('advent-academy-demo-v1')),null);
 const cookie=(await context.cookies()).find(c=>c.name==='advent_session');assert.ok(cookie?.httpOnly);assert.equal(cookie.sameSite,'Lax');
 const sessionResponse=await context.request.get(`${base}/api/session`);assert.equal(sessionResponse.status(),200);assert.equal((await sessionResponse.json()).firstName,'Мария');
 const csrf=await context.request.post(`${base}/api/backend/learning/courses/${fixture.course}/lessons/${fixture.lessons[0]}/complete`,{headers:{Origin:'https://foreign.example'}});assert.equal(csrf.status(),403);
 const unknown=await context.request.get(`${base}/api/backend/users`);assert.equal(unknown.status(),404);
 await page.getByRole('link',{name:'Продолжить обучение',exact:true}).click();
 await page.getByRole('heading',{name:'Реальный материал',exact:true}).waitFor();
 await page.getByRole('button',{name:'Завершить урок',exact:true}).click();
 await page.getByText('Урок завершён',{exact:true}).waitFor();
 assert.equal((await (await context.request.get(`${base}/api/backend/learning/courses/${fixture.course}`)).json()).completed,1);
 await page.screenshot({path:'/tmp/advent-sprint-desktop.png',fullPage:true});
 await page.setViewportSize({width:390,height:844});
 await page.screenshot({path:'/tmp/advent-sprint-mobile.png',fullPage:true});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth),true);
 // Force only the encrypted BFF expiration timestamp into the past, retaining real Java tokens.
 const latest=(await context.cookies()).find(c=>c.name==='advent_session');const b=Buffer.from(latest.value,'base64url');const key=Buffer.from(process.env.ADVENT_TEST_SESSION_KEY || 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=','base64');const decipher=createDecipheriv('aes-256-gcm',key,b.subarray(0,12));decipher.setAuthTag(b.subarray(12,28));const data=JSON.parse(Buffer.concat([decipher.update(b.subarray(28)),decipher.final()]).toString());data.expiresAt=0;const iv=randomBytes(12);const cipher=createCipheriv('aes-256-gcm',key,iv);const encrypted=Buffer.concat([cipher.update(JSON.stringify(data)),cipher.final()]);const expired=Buffer.concat([iv,cipher.getAuthTag(),encrypted]).toString('base64url');
 await context.addCookies([{...latest,value:expired}]);
 const refreshes=await Promise.all([1,2].map(()=>context.request.get(`${base}/api/session`,{headers:{Cookie:`advent_session=${expired}`}})));assert.deepEqual(refreshes.map(r=>r.status()),[200,200]);
 await page.reload();await page.getByText('Урок завершён',{exact:true}).waitFor();
 await page.getByRole('button',{name:'Меню пользователя Мария'}).click();await page.getByRole('link',{name:'Выйти',exact:true}).click();await page.getByRole('heading',{name:'Войти в кабинет'}).waitFor();
 assert.equal((await context.cookies()).some(c=>c.name==='advent_session'),false);assert.equal((await context.request.get(`${base}/api/session`)).status(),401);
 await login();await page.getByRole('heading',{name:'Привет, Мария!'}).waitFor();await page.goto(`${base}/?view=lesson&course=${fixture.course}&lesson=${fixture.lessons[0]}`);await page.getByText('Урок завершён',{exact:true}).waitFor();
 await page.goto(`${base}/?view=lesson&course=${fixture.course}&lesson=${fixture.lessons[2]}`);await page.getByText('Урок недоступен',{exact:true}).waitFor();
 await page.goto(`${base}/?view=lesson&course=${fixture.course}&lesson=${fixture.lessons[1]}`);await page.getByRole('heading',{name:'Реальный материал'}).waitFor();await page.getByRole('button',{name:'Завершить урок',exact:true}).click();await page.getByText('Урок завершён',{exact:true}).waitFor();
 await page.setViewportSize({width:1440,height:1000});await page.getByRole('link',{name:'Главная',exact:true}).click();await page.locator('.attention-section .assignment-row').waitFor();
 await page.goto(`${base}/?view=lesson&course=${fixture.course}&lesson=${fixture.lessons[2]}`);await page.getByRole('heading',{name:fixture.assignmentTitle || 'Обязательное задание'}).waitFor();assert.equal(await page.getByRole('button',{name:'Завершить урок'}).count(),0);
 await page.getByRole('button',{name:'Меню пользователя Мария'}).click();await page.getByRole('link',{name:'Выйти',exact:true}).click();await page.getByRole('heading',{name:'Войти в кабинет'}).waitFor();await login(fixture.otherEmail);await page.getByRole('heading',{name:'Курсы пока не назначены'}).waitFor();
 assert.equal((await context.request.get(`${base}/api/backend/learning/courses/${fixture.course}`)).status(),403);
 await page.goto(`${base}/demo`);await page.getByRole('heading',{name:'Привет, Александр!'}).waitFor();await page.getByRole('link',{name:'Продолжить обучение',exact:true}).click();assert.equal(new URL(page.url()).pathname,'/demo');
 assert.deepEqual(errors,[]);console.log('PASS: browser login, real content/progress, mobile layout, refresh concurrency, logout/relogin, locked/assignment/foreign course, demo isolation, CSRF and API allowlist.');
} finally {await browser.close();}
