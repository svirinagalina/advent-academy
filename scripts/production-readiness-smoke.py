#!/usr/bin/env python3
"""Exercise production profiles, TLS edge and recovery on an owned disposable local deployment."""
import base64
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import signal
import socket
import ssl
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT=Path(__file__).resolve().parents[1]
class Run:
    def __init__(self):
        self.project='advent-readiness-'+time.strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:6]
        self.artifacts=ROOT/'reports/architecture-readiness'/self.project
        self.artifacts.mkdir(parents=True,mode=0o700)
        self.temporary=tempfile.TemporaryDirectory(prefix=self.project+'-',dir=os.environ.get("ADVENT_TEST_TMPDIR"))
        self.folder=Path(self.temporary.name)
        self.env=os.environ.copy()
        self.started=False
        self.compose=['docker','compose','--env-file',os.devnull,'-p',self.project,'-f',str(ROOT/'deploy/production/compose.yml'),'-f',str(self.folder/'test.yml')]
    def command(self,args,name,timeout=900,input=None):
        print(name,flush=True)
        with (self.artifacts/(name+'.log')).open('w') as output:
            process=subprocess.Popen(args,cwd=ROOT,env=self.env,stdin=subprocess.PIPE if input is not None else None,
                                     stdout=output,stderr=subprocess.STDOUT,start_new_session=True)
            try:
                process.communicate(input=input,timeout=timeout)
                if process.returncode:raise RuntimeError(f'{name} failed; see {name}.log')
            finally:
                try:os.killpg(process.pid,signal.SIGTERM)
                except ProcessLookupError:pass
                try:process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid,signal.SIGKILL);process.wait()
    def capture(self,args):
        return subprocess.check_output(args,cwd=ROOT,env=self.env,text=True,timeout=30).strip()
    def execute(self):
        self.command(['python3','deploy/production/generate-local-secrets.py',str(self.folder/'secrets')],'secrets')
        self.password='Local-Readiness!'+secrets.token_hex(12)
        p=self.folder/'secrets/user/bootstrap-password';p.write_text(self.password);p.chmod(0o444)
        tls=self.folder/'tls';tls.mkdir()
        self.command(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(tls/'key.pem'),'-out',str(tls/'cert.pem'),'-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost'],'tls')
        with socket.socket() as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
        self.origin=f'https://localhost:{port}'
        self.env.update(DEPLOYMENT_NAME=self.project,SECRETS_DIR=str(self.folder/'secrets'),TLS_DIR=str(tls),PUBLIC_HOST='localhost',PUBLIC_ORIGIN=self.origin,HTTPS_PORT=str(port),JWT_KEY_ID='local-readiness-v1',SMTP_HOST='smtp-fixture',SMTP_USERNAME='isolated',EMAIL_FROM='academy@example.test',TURN_URL='stun:127.0.0.1:3478',TURN_USERNAME='isolated',TURN_CREDENTIAL='isolated',SIGNALING_URL=f'wss://localhost:{port}/ws/meetings',BOOTSTRAP_ENABLED='true',BOOTSTRAP_EMAIL='bootstrap@example.test')
        (self.folder/'test.yml').write_text('''services:
  user-service:
    environment:
      SMTP_PORT: 1025
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH: "false"
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED: "false"
  smtp-fixture:
    build:
      context: '''+str(ROOT)+'''
      dockerfile: scripts/fixtures/smtp/Dockerfile
    ports: ["127.0.0.1::8025"]
    networks: [application]
''')
        self.command([str(ROOT/'gradlew'),':user-service:bootJar',':content-service:bootJar',':meeting-service:bootJar',':api-gateway:bootJar','--no-daemon'],'backend-build')
        self.started=True
        self.command(self.compose+['up','-d','--build','--wait','--wait-timeout','180'],'compose-up',900)
        # Validate ownership before any direct database checks.
        pg=self.capture(self.compose+['ps','-q','postgres'])
        info=json.loads(self.capture(['docker','inspect',pg]))[0]
        assert info['Config']['Labels']['com.docker.compose.project']==self.project
        context=ssl._create_unverified_context()
        self.jar=http.cookiejar.CookieJar()
        self.http=urllib.request.build_opener(urllib.request.HTTPSHandler(context=context),urllib.request.HTTPCookieProcessor(self.jar))
        def call(path,body=None,method=None):
            data=None if body is None else json.dumps(body).encode()
            req=urllib.request.Request(self.origin+path,data=data,method=method,headers={'Content-Type':'application/json','Origin':self.origin})
            try:
                with self.http.open(req,timeout=30) as r:return r.status,r.headers,r.read()
            except urllib.error.HTTPError as e:return e.code,e.headers,e.read()
        for _ in range(30):
            try:
                if call('/api/health')[0]==200:break
            except urllib.error.URLError:pass
            time.sleep(1)
        else:raise RuntimeError('TLS edge not ready')
        status,_,raw=call('/.well-known/jwks.json');assert status==200
        jwks=json.loads(raw);assert jwks['keys'][0]['alg']=='RS256' and 'd' not in jwks['keys'][0]
        login=call('/api/session',{'email':'bootstrap@example.test','password':self.password}); assert login[0]==200,(login[0],login[2])
        assert json.loads(call('/api/session')[2])['email']=='bootstrap@example.test'
        user={'requestId':str(uuid.uuid4()),'email':'learner@example.test','firstName':'Local','lastName':'Learner','roles':['STUDENT']}
        status,_,raw=call('/api/backend/users/admin',user);assert status==200,(status,raw)
        smtp='http://'+self.capture(self.compose+['port','smtp-fixture','8025'])
        messages=json.load(urllib.request.urlopen(smtp+'/messages'))
        assert any('learner@example.test' in str(m) for m in messages)
        # Every service migrated with a different runtime identity and a public-only resource key mount.
        runtime_roles=self.capture(self.compose+['exec','-T','postgres','psql','-U','postgres','-d','academy','-Atc',"SELECT count(*) FROM pg_roles WHERE rolname IN('academy_user','academy_content','academy_meeting')"])
        assert runtime_roles=='3'
        self.command(['bash','deploy/production/backup.sh',str(self.folder/'backup.dump')],'backup')
        self.command(['bash','deploy/production/restore.sh',str(self.folder/'backup.dump'),'academy_restore_smoke'],'restore')
        original=self.capture(self.compose+['exec','-T','postgres','psql','-U','postgres','-d','academy','-Atc','SELECT count(*) FROM identity.users'])
        restored=self.capture(self.compose+['exec','-T','postgres','psql','-U','postgres','-d','academy_restore_smoke','-Atc','SELECT count(*) FROM identity.users'])
        assert original==restored and int(original)>0
        self.command(self.compose+['up','-d','--no-deps','--scale','frontend=2','--wait','frontend'],'two-frontends')
        self.command(self.compose+['restart','edge'],'edge-reload')
        fixture={'postgres':pg,'sessionKey':(self.folder/'secrets/frontend/session-key').read_text().strip(),'origin':self.origin,'email':'bootstrap@example.test','password':self.password,'project':self.project,'smtp':smtp}
        fixture_path=self.folder/'fixture.json';fixture_path.write_text(json.dumps(fixture));fixture_path.chmod(0o600)
        self.env.update(ADVENT_READINESS_FIXTURE=str(fixture_path),ADVENT_TEST_ARTIFACTS=str(self.artifacts/'browser'))
        self.command(['node','frontend/scripts/readiness-smoke.mjs'],'browser')
        # Spoofed forwarding headers cannot escape the real edge address quota.
        quotas=[]
        for index in range(22):
            quotas.append(call('/api/session',{'email':f'absent-{index}@example.test','password':'irrelevant-local-password'})[0])
        assert 429 in quotas, quotas
        client_script='''const https=require('node:https');
const req=https.request('https://edge:8443/api/session',{method:'POST',rejectUnauthorized:false,headers:{'Origin':ORIGIN,'Content-Type':'application/json','X-Advent-Client-IP':'203.0.113.9'}},r=>{console.log(r.statusCode);r.resume();});
req.on('error',e=>{console.error(e.message);process.exitCode=1});req.end(JSON.stringify({email:'second-client@example.test',password:'irrelevant-local-password'}));
'''.replace('ORIGIN',json.dumps(self.origin))
        frontend=self.capture(self.compose+['ps','-q','frontend']).splitlines()[0]
        independent=subprocess.check_output(['docker','exec','-i',frontend,'node','-'],input=client_script,text=True,timeout=30).strip()
        assert independent=='401', independent
        with (self.artifacts/'duplicate-meeting-node.log').open('w') as output:
            duplicate=subprocess.run(self.compose+['run','--rm','--no-deps','meeting-service'],cwd=ROOT,env=self.env,stdout=output,stderr=subprocess.STDOUT,timeout=90)
        assert duplicate.returncode != 0 and 'Another meeting transport node' in (self.artifacts/'duplicate-meeting-node.log').read_text()
        self.command(self.compose+['stop','content-service'],'stop-content')
        assert call('/api/health')[0]==503, 'Frontend readiness must follow its backend dependencies'
        self.command(self.compose+['up','-d','--wait','content-service'],'recover-content')
        for _ in range(10):
            if call('/api/health')[0]==200:break
            time.sleep(1)
        else:raise RuntimeError('Readiness did not recover')
        (self.artifacts/'checks.json').write_text(json.dumps({'tls':True,'rsa':True,'bootstrap':True,'smtp':True,'separateDbRoles':True,'backupRestore':True,'readinessTracksBackendFailure':True,'twoBffReplicas':True,'independentEdgeQuotas':True,'duplicateMeetingNodeRejected':True,'externalTurnTested':False,'realDeviceCapacityTested':False},indent=2))
    def cleanup(self):
        if self.started:
            for args,name in [(['logs','--no-color'],'services'),(['down','--volumes','--remove-orphans','--rmi','local'],'cleanup')]:
                self.command(self.compose+args,name,timeout=180)
        self.temporary.cleanup()
def main():
    run=Run();passed=False
    print('Artifacts: '+str(run.artifacts),flush=True)
    def interrupt(signum,_frame):raise KeyboardInterrupt(f'signal {signum}')
    signal.signal(signal.SIGTERM,interrupt)
    try:run.execute();passed=True
    finally:
        signal.signal(signal.SIGTERM,signal.SIG_IGN)
        signal.signal(signal.SIGINT,signal.SIG_IGN)
        run.cleanup()
        (run.artifacts/'result.json').write_text(json.dumps({'passed':passed,'project':run.project})+'\n')
        print('PASS' if passed else 'FAIL',flush=True)
if __name__=='__main__':main()
