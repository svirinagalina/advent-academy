import { chromium } from 'playwright';
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';
const fixture = JSON.parse(readFileSync(process.env.ADVENT_TEST_FIXTURE || '/tmp/advent-sprint3-fixture.json','utf8'));
const base = process.env.ADVENT_TEST_ORIGIN || 'http://localhost:13011';
const project = process.env.ADVENT_TEST_PROJECT || 'advent-sprint3';
assert.equal(project, 'advent-sprint3');
assert.ok(['localhost','127.0.0.1'].includes(new URL(base).hostname));
assert.ok(fixture.email.endsWith('@example.test'));
const inspect = name => JSON.parse(execFileSync('docker',['inspect',name],{encoding:'utf8'}))[0];
assert.ok('/var/lib/postgresql/data' in inspect(project+'-postgres-1').HostConfig.Tmpfs);
// Replay the additive migration beside populated user/content schemas and roll it back.
const migration=readFileSync(new URL('../../meeting-service/src/main/resources/db/migration/V1__scheduled_meetings.sql',import.meta.url),'utf8');
execFileSync('docker',['exec','-i',project+'-postgres-1','psql','-v','ON_ERROR_STOP=1','-U','sprint','-d','sprint'],{input:`BEGIN; CREATE TEMP TABLE before_counts AS SELECT (SELECT count(*) FROM public.users) AS users, (SELECT count(*) FROM content.courses) AS courses; CREATE SCHEMA meeting_upgrade_check; SET LOCAL search_path=meeting_upgrade_check; ${migration} DO $$ BEGIN IF (SELECT users FROM pg_temp.before_counts) <> (SELECT count(*) FROM public.users) OR (SELECT courses FROM pg_temp.before_counts) <> (SELECT count(*) FROM content.courses) THEN RAISE EXCEPTION 'Existing data changed'; END IF; END $$; ROLLBACK;`,stdio:['pipe','pipe','pipe']});
console.log('PASS: additive migration beside populated user/content schemas, existing counts preserved, replay rolled back.');
function restart(service) {
 const name=project+'-'+service+'-1'; assert.equal(inspect(name).Config.Labels['com.docker.compose.project'],project);
 execFileSync('docker',['restart',name],{stdio:'pipe'});
}
const browser = await chromium.launch({executablePath:process.env.CHROME_PATH || '/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--use-fake-device-for-media-stream','--use-fake-ui-for-media-stream']});
const contexts = await Promise.all(Array.from({length:5},()=>browser.newContext({permissions:['camera','microphone'],viewport:{width:1440,height:1000}})));
for (const context of contexts) await context.addInitScript(() => {
 window.__meetingPeers = [];
 const Native = window.RTCPeerConnection;
 window.RTCPeerConnection = new Proxy(Native, { construct(target, args) { const peer = new target(...args); window.__meetingPeers.push(peer); return peer; } });
});
const pages = await Promise.all(contexts.map(c=>c.newPage()));
const [host, student, peer, foreign, other] = pages;
const errors=[]; pages.forEach(p=>p.on('pageerror',e=>errors.push(e.message)));
const route='/api/backend/meetings/scheduled';
const title='Групповой разбор Java '+Date.now();
async function login(p,email,name) {
 await p.goto(base + "/cabinet"); await p.getByLabel('Email',{exact:true}).fill(email); await p.getByLabel('Пароль',{exact:true}).fill(fixture.password);
 await p.getByRole('button',{name:'Войти',exact:true}).click(); await p.getByRole('button',{name:`Меню пользователя ${name}`,exact:true}).waitFor();
}
async function request(p,method,path,data,status=200) {
 const r=await p.request.fetch(base+path,{method,headers:{Origin:base},data});
 assert.equal(r.status(),status,`${method} ${path}: ${await r.text()}`); return status===200 ? r.json() : null;
}
async function refresh(p) { await p.getByRole('button',{name:'Обновить',exact:true}).click(); await p.getByText('Обновляем расписание…',{exact:true}).waitFor({state:'hidden'}); }
async function connected(p,count=3) {
 await p.waitForFunction(async n=>{
  const videos=[...document.querySelectorAll('.call-grid video')];
  if (!(videos.length===n && videos.every(v=>v.readyState>=2 && v.srcObject?.getVideoTracks().some(t=>t.readyState==='live') && v.getVideoPlaybackQuality().totalVideoFrames>3))) return false;
  const peers=window.__meetingPeers.filter(p=>p.connectionState==='connected');
  if(peers.length!==n-1) return false;
  const stats=await Promise.all(peers.map(p=>p.getStats()));
  return stats.every(report=>[...report.values()].some(s=>s.type==='inbound-rtp' && s.kind==='audio' && s.packetsReceived>0));
 },count,{timeout:30000});
}
async function ready() {
 for(let i=0;i<40;i++) {
  const r=await host.request.get(base+'/api/backend/meetings/config'); if(r.ok()) return;
  await new Promise(resolve=>setTimeout(resolve,500));
 }
 throw new Error('Meeting readiness timeout');
}
async function rawJoin(p,admission) {
 return p.evaluate(({url,admission})=>new Promise((resolve,reject)=>{
  const socket=new WebSocket(url); const timer=setTimeout(()=>{socket.close();reject(new Error('JOIN timeout'));},5000);
  socket.onopen=()=>socket.send(JSON.stringify({version:1,type:'JOIN',roomId:admission.roomId,capability:admission.capability}));
  socket.onmessage=e=>{clearTimeout(timer);socket.close();resolve(JSON.parse(e.data));}; socket.onerror=reject;
 }),{url:'ws://localhost:18092/ws/meetings',admission});
}
try {
 await login(host,fixture.teacherEmail,'Редактор'); await login(student,fixture.email,'Мария'); await login(peer,fixture.peerEmail,'Второй ученик');
 await login(foreign,fixture.foreignTeacherEmail,'Другой преподаватель'); await login(other,fixture.otherEmail,'Другой');
 await host.goto(base+'/?view=meetings'); await host.getByRole('button',{name:'Запланировать занятие'}).click();
 await host.getByLabel('Название',{exact:true}).fill(title);
 await host.getByRole('checkbox').first().waitFor();
 const checks=host.getByRole('checkbox'); assert.equal(await checks.count(),2); await checks.nth(0).check(); await checks.nth(1).check();
 await host.getByRole('button',{name:'Сохранить',exact:true}).click(); await host.getByRole('dialog').waitFor({state:'hidden'});
 await host.getByRole('heading',{name:title,exact:true}).waitFor();
 let listing=await request(host,'GET',route+'/teaching'); let meeting=listing.items.find(m=>m.title===title); assert.ok(meeting); assert.equal(meeting.participantCount,3);
 assert.equal((await request(other,'GET',route)).total,0);
 await request(foreign,'GET',`/api/backend/users/teaching/courses/${fixture.course}/students`,undefined,403);
 await request(student,'GET',`/api/backend/users/teaching/courses/${fixture.course}/students`,undefined,403);
 await request(host,'GET',route+'/teaching?size=0',undefined,400);
 await request(foreign,'POST',`${route}/${meeting.id}/start`,{version:0},403);
 await request(student,'POST',`${route}/${meeting.id}/start`,{version:0},403);
 await request(other,'GET',`${route}/${meeting.id}`,undefined,403);
 await request(student,'POST',`${route}/${meeting.id}/capabilities`,undefined,409);
 assert.deepEqual((await request(student,'GET',`${route}/${meeting.id}`)).participants,[]);
 await request(host,'POST',`${route}/${meeting.id}/start`,{version:0});
 await request(other,'POST',`${route}/${meeting.id}/capabilities`,undefined,403);
 const csrf=await host.request.post(base+route,{headers:{Origin:'https://foreign.example'},data:{}}); assert.equal(csrf.status(),403);
 await refresh(host); await host.getByRole('button',{name:'Подключиться',exact:true}).click();
 await student.goto(base+'/?view=meetings'); await student.getByRole('button',{name:'Подключиться',exact:true}).click();
 await peer.goto(base+'/?view=meetings'); await peer.getByRole('button',{name:'Подключиться',exact:true}).click();
 await Promise.all([host,student,peer].map(p=>connected(p)));
 await host.getByRole('button',{name:'Выключить микрофон'}).click(); assert.equal(await host.getByRole('button',{name:'Включить микрофон'}).getAttribute('aria-pressed'),'false'); assert.equal(await host.evaluate(()=>document.querySelector('.call-grid video').srcObject.getAudioTracks()[0].enabled),false);
 await host.getByRole('button',{name:'Включить микрофон'}).click(); await host.getByRole('button',{name:'Выключить камеру'}).click(); assert.equal(await host.evaluate(()=>document.querySelector('.call-grid video').srcObject.getVideoTracks()[0].enabled),false); await host.getByRole('button',{name:'Включить камеру'}).click();
 await host.screenshot({path:'/tmp/advent-sprint3-group-call.png',fullPage:true});
 console.log('PASS: real three-browser WebRTC mesh, all participants receive video frames and audio RTP, microphone/camera toggles.');
 restart('meeting-service'); await ready();
 const restored=await request(host,'GET',route+'/teaching'); assert.equal(restored.items.find(m=>m.id===meeting.id).status,'ACTIVE');
 for(const p of [host,student,peer]) { await p.getByRole('button',{name:'Переподключиться',exact:true}).waitFor(); await p.getByRole('button',{name:'Переподключиться',exact:true}).click(); }
 await Promise.all([host,student,peer].map(p=>connected(p)));
 console.log('PASS: persistent schedule, new capabilities and three-party reconnection after real service restart.');
 const admission=await request(student,'POST',`${route}/${meeting.id}/capabilities`);
 await request(host,'POST',`${route}/${meeting.id}/cancel`,{version:1});
 for(const p of [host,student,peer]) { await p.getByText('Занятие завершено или отменено.',{exact:true}).waitFor(); assert.equal(await p.locator('.call-grid video').count(),0); await p.getByRole('button',{name:'Выйти из занятия',exact:true}).click(); }
 assert.equal((await rawJoin(student,admission)).type,'ERROR');
 await request(student,'POST',`${route}/${meeting.id}/capabilities`,undefined,409);
 await request(host,'POST',`${route}/${meeting.id}/start`,{version:2},409);
 assert.equal((await request(host,'GET',route+'/teaching?tab=past')).items.find(m=>m.id===meeting.id).status,'CANCELLED');
 const body={requestId:crypto.randomUUID(),courseId:fixture.course,title:'Перенос',startsAt:new Date(Date.now()+60000).toISOString(),endsAt:new Date(Date.now()+3600000).toISOString(),participants:[fixture.student,fixture.peer]};
 const draft=await request(host,'POST',route,body);
 const edits=await Promise.all(['Перенос A','Перенос B'].map(title=>host.request.put(`${base}${route}/${draft.id}`,{headers:{Origin:base},data:{...body,title,version:0}})));
 assert.deepEqual(edits.map(r=>r.status()).sort(),[200,409]); const accepted=await edits.find(r=>r.status()===200).json();
 const retry=await request(host,'POST',route,body); assert.equal(retry.id,draft.id); assert.equal(retry.title,accepted.title);
 await request(host,'POST',`${route}/${draft.id}/cancel`,{version:1});
 const expired=await request(host,'POST',route,{...body,requestId:crypto.randomUUID(),title:'Истечение',startsAt:new Date(Date.now()-60000).toISOString(),endsAt:new Date(Date.now()+2500).toISOString()});
 await request(host,'POST',`${route}/${expired.id}/start`,{version:0});
 await new Promise(resolve=>setTimeout(resolve,3500));
 assert.equal((await request(host,'GET',route+'/teaching?tab=past')).items.find(m=>m.id===expired.id).status,'FINISHED');
 await host.getByRole('button',{name:'Завершённые',exact:true}).click(); await host.getByRole('heading',{name:title,exact:true}).waitFor();
 await host.setViewportSize({width:390,height:844}); assert.equal(await host.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true); await host.screenshot({path:'/tmp/advent-sprint3-meetings-mobile.png',fullPage:true});
 const boundary=await request(host,'POST',route,{...body,requestId:crypto.randomUUID(),title:'Проверка недоступного курса',startsAt:new Date(Date.now()-60000).toISOString(),endsAt:new Date(Date.now()+600000).toISOString()});
 await request(host,'POST',`${route}/${boundary.id}/start`,{version:0});
 const contentName=project+'-content-service-1'; assert.equal(inspect(contentName).Config.Labels['com.docker.compose.project'],project);
 try {
  execFileSync('docker',['stop',contentName],{stdio:'pipe'});
  await request(student,'POST',`${route}/${boundary.id}/capabilities`,undefined,502);
  await request(host,'GET',`/api/backend/users/teaching/courses/${fixture.course}/students`,undefined,502);
 } finally { execFileSync('docker',['start',contentName],{stdio:'pipe'}); }
 for(let i=0;i<40;i++) { if((await host.request.get(base+'/api/backend/teaching/courses')).ok()) break; await new Promise(resolve=>setTimeout(resolve,500)); }
 await request(host,'POST',`${route}/${boundary.id}/finish`,{version:1});
 console.log('PASS: unavailable authoritative course service denies admission and roster, recovery succeeds.');
 assert.deepEqual(errors,[]);
 console.log('PASS: teacher/student/foreign ownership, participant privacy, CSRF, cancellation revocation, stale admission, optimistic race, idempotent creation, expiry, history, mobile layout.');
} catch(error) { console.error('Browser alerts:', await host.getByRole('alert').allTextContents()); await host.screenshot({path:'/tmp/advent-sprint3-failure.png',fullPage:true}); throw error; } finally { await browser.close(); }
