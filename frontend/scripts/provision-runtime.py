import json, urllib.request, urllib.error, subprocess, os
from pathlib import Path
# This fixture may only modify the tmpfs-backed database of the isolated test project.
project=os.environ.get('ADVENT_TEST_PROJECT','advent-sprint1')
assert project in ('advent-sprint1','advent-sprint2','advent-sprint3')
container=project+'-postgres-1'
inspection=json.loads(subprocess.check_output(['docker','inspect',container]))[0]
assert inspection['Config']['Labels']['com.docker.compose.project']==project
assert '/var/lib/postgresql/data' in inspection['HostConfig']['Tmpfs']
base='http://localhost:'+{'advent-sprint1':'18090','advent-sprint2':'18091','advent-sprint3':'18092'}[project]
def req(method,path,body=None,token=None,raw=None,ctype='application/json'):
 data=raw if raw is not None else json.dumps(body).encode() if body is not None else None
 headers={'Content-Type':ctype}
 if token: headers['Authorization']='Bearer '+token
 r=urllib.request.Request(base+path,data=data,headers=headers,method=method)
 with urllib.request.urlopen(r,timeout=20) as response:
  b=response.read();return json.loads(b) if b else None
password='Sprint-Strong-Password-2026!'
users=[]
accounts=[('sprint-student@example.test','Мария'),('sprint-editor@example.test','Редактор'),('sprint-other@example.test','Другой')]
if project in ('advent-sprint2','advent-sprint3'): accounts.extend([('sprint-foreign-teacher@example.test','Другой преподаватель'),('sprint-peer@example.test','Второй ученик')])
for email,name in accounts:
 user=req('POST','/api/auth/register',{'email':email,'password':password,'firstName':name,'lastName':'Проверка'})
 users.append(user)
subprocess.run(['docker','exec',container,'psql','-U','sprint','-d','sprint','-c',"UPDATE users SET email_verified=true,status='ACTIVE' WHERE email LIKE 'sprint-%@example.test'; INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u CROSS JOIN roles r WHERE u.email='sprint-editor@example.test' AND r.name='ADMIN' ON CONFLICT DO NOTHING;"],check=True,capture_output=True)
tokens=[req('POST','/api/auth/login',{'email':u['email'],'password':password}) for u in users]
editor=tokens[1]['accessToken']
component=req('POST','/api/components',{'title':'Основы Java','description':'Проверочный модуль'},editor)
lessons=[]
lesson_names=['Переменные','Условные операторы','Циклы']
if project in ('advent-sprint2','advent-sprint3'): lesson_names.append('Массивы')
for name in lesson_names:
 component=req('POST',f"/api/components/{component['id']}/lessons",{'title':name},editor)
 lessons.append(component['lessons'][-1]['id'])
for lid in lessons:
 boundary='sprint-boundary'
 raw=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="lesson.md"\r\nContent-Type: text/markdown\r\n\r\n# Реальный материал\n\nТекст из PostgreSQL.\n\n```java\nint age = 18;\n```\n\r\n--{boundary}--\r\n').encode()
 req('POST',f'/api/lessons/{lid}/content',token=editor,raw=raw,ctype=f'multipart/form-data; boundary={boundary}')
 req('POST',f'/api/lessons/{lid}/content/publish',token=editor)
req('PUT',f'/api/lessons/{lessons[2]}/requirements',{'requiredAssignment':True},editor)
if project in ('advent-sprint2','advent-sprint3'):
 req('PUT',f'/api/lessons/{lessons[2]}/assignment',{'title':'Проверка високосного года','description':'Напишите программу, которая определяет, является ли указанный год високосным. Учтите делимость на 100 и 400.'},editor)
req('POST',f"/api/components/{component['id']}/publish",token=editor)
course=req('POST','/api/courses',{'title':'Java: первый курс','description':'Учебный сценарий спринта'},editor)
req('POST',f"/api/courses/{course['id']}/components",{'componentId':component['id']},editor)
req('POST',f"/api/courses/{course['id']}/publish",token=editor)
req('PUT',f"/api/learning/courses/{course['id']}/students/{users[0]['id']}",token=editor)
if project in ('advent-sprint2','advent-sprint3'): req('PUT',f"/api/learning/courses/{course['id']}/students/{users[4]['id']}",token=editor)
direction=req('POST','/api/directions',{'title':'Программирование','description':'Каталог'},editor)
req('POST',f"/api/directions/{direction['id']}/courses",{'courseId':course['id']},editor)
if project in ('advent-sprint2','advent-sprint3'):
 # The creator becomes a regular instructor; exercise the owner branch, not admin bypass.
 subprocess.run(['docker','exec',container,'psql','-U','sprint','-d','sprint','-c',"DELETE FROM user_roles ur USING users u,roles r WHERE ur.user_id=u.id AND ur.role_id=r.id AND u.email='sprint-editor@example.test' AND r.name='ADMIN'; INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u CROSS JOIN roles r WHERE u.email IN ('sprint-editor@example.test','sprint-foreign-teacher@example.test') AND r.name='INSTRUCTOR' ON CONFLICT DO NOTHING;"],check=True,capture_output=True)
fixture='/tmp/advent-sprint3-fixture.json' if project=='advent-sprint3' else '/tmp/advent-sprint2-fixture.json' if project=='advent-sprint2' else '/tmp/advent-sprint-fixture.json'
Path(fixture).write_text(json.dumps({'email':users[0]['email'],'password':password,'otherEmail':users[2]['email'],'assignmentTitle':'Проверка високосного года' if project in ('advent-sprint2','advent-sprint3') else 'Обязательное задание','teacherEmail':users[1]['email'],'peerEmail':'sprint-peer@example.test','foreignTeacherEmail':'sprint-foreign-teacher@example.test','course':course['id'],'lessons':lessons,'student':users[0]['id'],'peer':users[4]['id'] if len(users)>4 else None}))
print('Synthetic users and published course provisioned through real gateway; temporary DB only.')
