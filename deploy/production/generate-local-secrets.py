#!/usr/bin/env python3
"""Generate disposable LOCAL test secrets; production keys must come from the operator's secret store."""
import base64
import os
from pathlib import Path
import secrets
import subprocess
import sys

root = Path(sys.argv[1]).resolve()
root.mkdir(mode=0o700, parents=True, exist_ok=False)
for part in ('db-init','user','content','meeting','frontend','edge'):
    (root/part).mkdir(mode=0o755)
def put(part, name, value):
    path = root/part/name
    path.write_text(value+'\n'); path.chmod(0o444)
put('db-init','admin-password', secrets.token_urlsafe(40))
for service in ('user','content','meeting'):
    for suffix,property_name in (('','spring.datasource.password'),('-migration','spring.flyway.password')):
        value=secrets.token_urlsafe(40)
        put('db-init',service+suffix+'-password',value);put(service,property_name,value)
bff,edge,enrollment=(secrets.token_urlsafe(40) for _ in range(3))
put('user','security.bff-request-key',bff);put('frontend','bff-key',bff)
put('frontend','edge-key',edge);put('edge','edge-key',edge)
put('user','enrollment.validation-key',enrollment);put('content','enrollment.validation-key',enrollment)
put('user','security.mfa.encryption.keys.v1',base64.b64encode(secrets.token_bytes(32)).decode())
put('user','spring.mail.password','isolated-local-smtp')
put('meeting','meeting.ice-servers[0].credential','isolated-local-turn')
put('frontend','session-key',base64.b64encode(secrets.token_bytes(32)).decode())
private=root/'user/jwt-private.pem'
subprocess.run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:3072','-out',str(private)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
private.chmod(0o444)
public=subprocess.check_output(['openssl','pkey','-in',str(private),'-pubout'],stderr=subprocess.DEVNULL).decode().strip()
for service in ('user','content','meeting'):put(service,'jwt-public.pem',public)
print(root)
