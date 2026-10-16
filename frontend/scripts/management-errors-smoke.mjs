import {chromium} from 'playwright';
import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const f=JSON.parse(readFileSync('/tmp/management-browser-fixture.json','utf8'));
const base=process.env.ADVENT_TEST_ORIGIN||'http://127.0.0.1:13014';assert.ok(['localhost','127.0.0.1'].includes(new URL(base).hostname));assert.ok(f.users.editor.email.endsWith('@example.test'));
const browser=await chromium.launch({executablePath:'/usr/bin/google-chrome',headless:true,args:['--no-sandbox']});
try{
 const context=await browser.newContext();const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(`${base}/?view=manage`);await page.getByLabel('Email',{exact:true}).fill(f.users.editor.email);await page.getByLabel('Пароль',{exact:true}).fill(f.password);await page.getByRole('button',{name:'Войти',exact:true}).click();await page.getByRole('heading',{name:'Курсы',exact:true}).waitFor();
 const list=await context.request.get(`${base}/api/backend/components`);const component=(await list.json()).find(c=>c.status==='PUBLISHED');assert.ok(component);
 const create=await context.request.post(`${base}/api/backend/courses`,{headers:{Origin:base},data:{title:'Проверка восстановления ошибок',description:'Сетевые ошибки сохраняют состояние'}});assert.equal(create.status(),201);const id=(await create.json()).id;
 assert.equal((await context.request.post(`${base}/api/backend/courses/${id}/components`,{headers:{Origin:base},data:{componentId:component.id}})).status(),201);
 assert.equal((await context.request.post(`${base}/api/backend/courses/${id}/publish`,{headers:{Origin:base}})).status(),200);
 await page.goto(`${base}/?view=manage&course=${id}`);await page.getByRole('heading',{name:'Проверка восстановления ошибок'}).waitFor();await page.getByRole('tab',{name:'Ученики',exact:true}).click();
 const candidateRoute=`**/api/backend/users/teaching/courses/${id}/candidates?*`;
 await page.route(candidateRoute,route=>route.fulfill({status:502,contentType:'application/json',body:'{"message":"Temporary failure"}'}));await page.getByLabel('Имя или email').fill(f.users.student.email);await page.getByRole('button',{name:'Повторить поиск',exact:true}).waitFor();assert.equal(await page.getByLabel('Имя или email').inputValue(),f.users.student.email);await page.unroute(candidateRoute);await page.getByRole('button',{name:'Повторить поиск',exact:true}).click();await page.getByRole('button',{name:'Назначить на курс',exact:true}).waitFor();
 const archiveRoute=`**/api/backend/courses/${id}/archive`;
 await page.route(archiveRoute,route=>route.fulfill({status:502,contentType:'application/json',body:'{"message":"Temporary failure"}'}));await page.getByRole('button',{name:'Архивировать курс',exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:'Архивировать курс',exact:true}).click();await page.getByRole('dialog').getByRole('alert').waitFor();assert.equal((await (await context.request.get(`${base}/api/backend/courses/${id}`)).json()).status,'PUBLISHED');await page.unroute(archiveRoute);await page.getByRole('dialog').getByRole('button',{name:'Архивировать курс',exact:true}).click();await page.getByText('Курс в архиве. Программа доступна только для просмотра.',{exact:true}).waitFor();
 await page.goto(base + "/cabinet");await page.getByRole('heading',{name:'Курсы пока не назначены',exact:true}).waitFor();await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await page.getByRole('link',{name:'Управление курсами',exact:true}).click();await page.getByRole('heading',{name:'Курсы',exact:true}).waitFor();assert.deepEqual(errors,[]);console.log('PASS: candidate search error/retry preserves query; failed archive preserves status and shows an error inside the focused dialog; retry commits archive');
}finally{await browser.close();}
