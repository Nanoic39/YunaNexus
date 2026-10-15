#!/usr/bin/env python3
"""Initialize production paths and secrets on the mounted data disk; never print secret values."""
import os,sys,secrets,base64,subprocess,json,shutil
from pathlib import Path
ROOT=Path('/YunaNexus/platform')
if not os.path.ismount('/YunaNexus'): raise SystemExit('Data disk is not mounted')
os.umask(0o077)
for part in ['ops','config','secrets','runtime','runtime/home','tmp','cache','logs','bin','releases/backend','releases/frontend','backups','data/sqlinit']:
 (ROOT/part).mkdir(parents=True,exist_ok=True)
def write(path,text,mode=0o600):
 p=ROOT/path;p.write_text(text,encoding='utf8');p.chmod(mode)
def env(path,values): write(path,''.join(f'{k}={v}\n' for k,v in values.items()))
if (ROOT/'secrets/compose.env').exists(): raise SystemExit('Secrets already initialized; refusing to overwrite')
rand=lambda:secrets.token_urlsafe(36)
passwords={s:rand() for s in ['mysql','redis','nacos','admin','permission']}
key=subprocess.check_output(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:3072'],stderr=subprocess.DEVNULL)
private=base64.b64encode(subprocess.check_output(['openssl','pkcs8','-topk8','-nocrypt','-outform','DER'],input=key)).decode()
public=base64.b64encode(subprocess.check_output(['openssl','pkey','-pubout','-outform','DER'],input=key)).decode()
aes=base64.b64encode(secrets.token_bytes(32)).decode();hmac=base64.b64encode(secrets.token_bytes(32)).decode()
env('secrets/compose.env',{'MYSQL_ROOT_PASSWORD':passwords['mysql'],'NACOS_AUTH_TOKEN':base64.b64encode(secrets.token_bytes(48)).decode(),'NACOS_AUTH_IDENTITY_VALUE':rand()})
write('secrets/redis.conf',f'bind 0.0.0.0\nprotected-mode yes\nrequirepass {passwords["redis"]}\nappendonly yes\nmaxmemory 48mb\nmaxmemory-policy noeviction\ndir /data\n')
os.chown(ROOT/'secrets/redis.conf',0,999);(ROOT/'secrets/redis.conf').chmod(0o640)
write('secrets/credentials.json',json.dumps(passwords))
init=[]
for name in ['meta','auth','gateway','user','permission','file','notice','audit','analytics']:
 data=ROOT/'data'/name;data.mkdir(exist_ok=True);os.chown(data,10001,10001);data.chmod(0o750)
 common={'DB_HOST':'mysql','DB_PORT':'3306','DB_USERNAME':'yn_'+name,'DB_PASSWORD':rand(),'DB_NAME':'yunanexus_'+name,'NACOS_ADDR':'nacos:8848','NACOS_USERNAME':'nacos','NACOS_PASSWORD':passwords['nacos'],'REDIS_HOST':'redis','REDIS_PORT':'6379','REDIS_PASSWORD':passwords['redis'],'ROCKETMQ_NAMESERVER':'namesrv:9876'}
 if name not in ['gateway','analytics']:
  dbs=['yunanexus_'+name+'_'+str(i) for i in range(2)] if name in ['user','file','audit'] else ['yunanexus_'+name]
  init.append(f"CREATE USER IF NOT EXISTS 'yn_{name}'@'%' IDENTIFIED BY '{common['DB_PASSWORD']}';")
  for db in dbs:init.append(f"GRANT SELECT,INSERT,UPDATE,DELETE ON `{db}`.* TO 'yn_{name}'@'%';")
 if name in ['auth','gateway']:
  common.update(JWT_PUBLIC_KEY_V1=public,EXTERNAL_ID_AES_KEY_V1=aes,EXTERNAL_ID_HMAC_KEY_V1=hmac)
 if name=='auth':common.update(JWT_PRIVATE_KEY_V1=private,AUTH_ADMIN_TOKEN=passwords['admin'],AUTH_MOBILE_ENABLED='false',AUTH_CAPTCHA_DEV_RETURN='false')
 if name=='permission':common['PERMISSION_ADMIN_TOKEN']=passwords['permission']
 if name=='notice':common['NOTICE_ADMIN_TOKEN']=passwords['admin']
 if name=='file':common['FILE_STORAGE_BASE_DIR']='/app/data/storage'
 if name=='audit':common['AUDIT_ARCHIVE_BACKUP_DIR']='/app/data/archive'
 if name=='analytics':common['ANALYTICS_CLICKHOUSE_ENABLED']='false'
 env('secrets/'+name+'.env',common)
 config={'spring':{},'server':{'forward-headers-strategy':'framework'},'management':{'endpoints':{'web':{'exposure':{'include':'health,info'}}},'endpoint':{'health':{'show-details':'never'}}}}
 config['spring']['cloud']={'nacos':{'discovery':{'group':'YUNANEXUS_PRD'},'config':{'enabled':False}}}
 config['spring']['cloud']['openfeign']={'client':{'config':{'default':{'connectTimeout':3000,'readTimeout':5000}}}}
 # JSON is valid YAML and avoids a runtime YAML dependency.
 if name in ['auth','meta','notice','permission']:config['spring']['datasource']={'hikari':{'minimum-idle':1,'maximum-pool-size':4}}
 if name=='auth':config['yunanexus']={'auth':{'security':{'public-origin':'https://103.39.66.135','allow-local-http':False,'cors-allowed-origins':['https://103.39.66.135','https://103.39.66.135:8443']}}}
 if name=='gateway':config['yunanexus']={'gateway':{'permission':{'enabled':True,'check-url':'http://permission:8300/internal/permission/check','permit-paths':['/api/v1/permission/me','/api/v1/auth/me','/api/v1/auth/logout','/api/v1/auth/logout-all']}}}
 write('config/'+name+'.yaml',json.dumps(config),0o644)
for source in (ROOT/'ops/sql').glob('*.sql'):shutil.copy2(source,ROOT/'data/sqlinit'/source.name)
write('data/sqlinit/zz-users.sql','\n'.join(init)+'\n',0o640)
shutil.copy2(ROOT/'ops/portal-client.sql', ROOT/'data/sqlinit/zzz-client.sql')
for f in (ROOT/'data/sqlinit').iterdir():os.chown(f,999,999);f.chmod(0o640)
os.chown(ROOT/'data/sqlinit',999,999);(ROOT/'data/sqlinit').chmod(0o750)
for name,uid in [('redis',999),('nacos',1000),('rocketmq',3000),('mysql',999)]:
 d=ROOT/'data'/name;d.mkdir(exist_ok=True);os.chown(d,uid,uid);d.chmod(0o750)
for name,uid in [('nacos',1000),('broker',3000),('namesrv',3000)]:
 d=ROOT/'logs'/name;d.mkdir(exist_ok=True);os.chown(d,uid,uid);d.chmod(0o750)
for f in ['docker.json','broker.conf','nginx.conf']:shutil.copy2(ROOT/'ops'/f,ROOT/'config'/f);(ROOT/'config'/f).chmod(0o644)
tls=ROOT/'secrets/tls';tls.mkdir(exist_ok=True);os.chown(tls,0,101);tls.chmod(0o750)
subprocess.run(['openssl','req','-x509','-newkey','rsa:3072','-nodes','-days','180','-subj','/CN=103.39.66.135','-addext','subjectAltName=IP:103.39.66.135,DNS:yuna.nanoic.cc,DNS:console.yuna.nanoic.cc','-keyout',str(tls/'server.key'),'-out',str(tls/'server.crt')],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
for f in tls.iterdir():os.chown(f,0,101);f.chmod(0o640)
print('Production directories and secrets initialized; secrets are only in /YunaNexus/platform/secrets')
