import {chromium} from 'playwright';
import assert from 'node:assert/strict';
const base=process.env.ADVENT_TEST_ORIGIN||'http://127.0.0.1:13014';
assert.ok(['127.0.0.1','localhost'].includes(new URL(base).hostname));
const browser=await chromium.launch({executablePath:'/usr/bin/google-chrome',headless:true,args:['--no-sandbox']});
try{
 const context=await browser.newContext({colorScheme:'light',viewport:{width:390,height:844}});
 const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(`${base}/demo`);await page.getByRole('button',{name:'Включить тёмную тему'}).waitFor();
 await page.emulateMedia({colorScheme:'dark'});await page.getByRole('button',{name:'Включить светлую тему'}).waitFor();
 assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'dark');
 await page.getByRole('button',{name:'Включить светлую тему'}).click();
 await page.getByRole('link',{name:'Продолжить обучение',exact:true}).click();await page.getByRole('heading',{name:'Условные операторы',exact:true}).waitFor();
 assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'light');assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 await page.screenshot({path:'/tmp/learner-lesson-mobile-light.png',fullPage:true});
 await page.getByRole('button',{name:'Включить тёмную тему'}).click();await page.screenshot({path:'/tmp/learner-lesson-mobile-dark.png',fullPage:true});
 await page.reload();await page.getByRole('button',{name:'Включить светлую тему'}).waitFor();assert.equal(await page.evaluate(()=>localStorage.getItem('advent-academy.theme')),'dark');
 await context.close();
 const noStorage=await browser.newContext({colorScheme:'light'});await noStorage.addInitScript(()=>{Storage.prototype.getItem=()=>{throw new Error('Storage unavailable');};Storage.prototype.setItem=()=>{throw new Error('Storage unavailable');};});
 const offline=await noStorage.newPage();offline.on('pageerror',e=>errors.push(e.message));await offline.goto(`${base}/demo`);await offline.getByRole('button',{name:'Включить тёмную тему'}).click();await offline.emulateMedia({colorScheme:'light'});assert.equal(await offline.evaluate(()=>document.documentElement.dataset.theme),'dark');await offline.getByRole('button',{name:'Включить светлую тему'}).click();assert.equal(await offline.evaluate(()=>document.documentElement.dataset.theme),'light');await noStorage.close();
 assert.deepEqual(errors,[]);console.log('PASS: automatic system theme, manual persistence, mobile lesson in both themes, no overflow, unavailable storage fallback, no page errors');
}finally{await browser.close();}
