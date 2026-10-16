import json,urllib.request,subprocess,uuid,zipfile
from pathlib import Path
container='advent-course-api-postgres-1'
x=json.loads(subprocess.check_output(['docker','inspect',container]))[0]
assert x['Config']['Labels']['com.docker.compose.project']=='advent-course-api' and '/var/lib/postgresql/data' in x['HostConfig']['Tmpfs']
base='http://127.0.0.1:18094'
run=uuid.uuid4().hex[:8]
password='Management-Strong-2026!'
users={}
for name in ['editor','student','foreign','blocked']:
 email=f'management-{run}-{name}@example.test'
 data={'email':email,'password':password,'firstName':{'editor':'Иван','student':'Мария','foreign':'Другой','blocked':'Заблокированный'}[name],'lastName':'Проверка'}
 r=urllib.request.Request(base+'/api/auth/register',data=json.dumps(data).encode(),headers={'Content-Type':'application/json'},method='POST')
 users[name]=json.load(urllib.request.urlopen(r))
for u in users.values():uuid.UUID(u['id'])
ids=','.join("'"+u['id']+"'" for u in users.values())
sql=f"INSERT INTO roles(id,name,description,system_role,created_at,updated_at) VALUES (gen_random_uuid(),'CONTENT_EDITOR','Test editor',false,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT(name) DO NOTHING; UPDATE users SET status='ACTIVE',email_verified=true WHERE id IN ({ids}); UPDATE users SET status='BLOCKED' WHERE id='{users['blocked']['id']}'; INSERT INTO user_roles(user_id,role_id) SELECT '{users['editor']['id']}',id FROM roles WHERE name='CONTENT_EDITOR'; INSERT INTO user_roles(user_id,role_id) SELECT '{users['foreign']['id']}',id FROM roles WHERE name='INSTRUCTOR';"
subprocess.run(['docker','exec',container,'psql','-U','sprint','-d','sprint','-v','ON_ERROR_STOP=1','-c',sql],check=True,capture_output=True)
Path('/tmp/management-browser-fixture.json').write_text(json.dumps({'users':users,'password':password,'container':container}))
with zipfile.ZipFile('/tmp/management-material.zip','w') as z:z.writestr('lesson.md','# ZIP lesson\n\nReal material from archive.');z.writestr('assets/notes.txt','notes')
print('Synthetic browser fixture ready')
