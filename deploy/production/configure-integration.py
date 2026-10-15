#!/usr/bin/env python3
"""Configure real web/admin integration and bind an explicitly named existing administrator."""
import argparse,json,re,subprocess,secrets,os,urllib.request,http.cookiejar
from pathlib import Path
R=Path('/YunaNexus/platform')
p=argparse.ArgumentParser();p.add_argument('--admin-user',required=True);p.add_argument('--check-only',action='store_true');p.add_argument('--create-missing',action='store_true');a=p.parse_args()
if not re.fullmatch(r'[A-Za-z0-9_.-]{4,64}',a.admin_user):raise ValueError('Invalid username')
def sql(statement):
 result=subprocess.run([str(R/'ops/compose.sh'),'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B'],input=statement,text=True,capture_output=True)
 if result.returncode:raise RuntimeError('Database configuration failed')
 return result.stdout.strip()
rows=sql("SELECT user_id,status FROM yunanexus_auth.auth_user WHERE username='"+a.admin_user+"';")
if not rows:
 if not a.create_missing or a.check_only:raise RuntimeError('Requested administrator account does not exist; no account created')
 secret_path=R/'secrets/admin-bootstrap.json'
 if secret_path.exists():raise RuntimeError('Bootstrap credential already exists; inspect locally before retrying')
 account={'username':a.admin_user,'password':secrets.token_urlsafe(32)}
 fd=os.open(secret_path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'w') as output:json.dump(account,output)
 opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
 base='http://103.39.66.135:3000/api/v1/auth'
 with opener.open(base+'/csrf',timeout=15) as response:csrf=json.load(response)['data']
 request=urllib.request.Request(base+'/register',data=json.dumps(account).encode(),headers={'Content-Type':'application/json',csrf['headerName']:csrf['token'],'Origin':'http://103.39.66.135:3000'})
 with opener.open(request,timeout=20) as response:result=json.load(response)
 if result.get('code')!=200:raise RuntimeError('Administrator registration failed; inspect bootstrap credential locally')
 rows=sql("SELECT user_id,status FROM yunanexus_auth.auth_user WHERE username='"+a.admin_user+"';")
 print('Administrator account created; initial credential only in secrets/admin-bootstrap.json',flush=True)
user_id,status=rows.split('\t')
if not user_id.isdigit() or status!='1':raise RuntimeError('Administrator account must be active')
print('Requested administrator account exists and is active',flush=True)
if a.check_only:raise SystemExit(0)
sql("""START TRANSACTION;
INSERT INTO yunanexus_permission.perm_role(role_code,role_name,data_scope,status) VALUES('admin','平台管理员',1,1) ON DUPLICATE KEY UPDATE role_code='admin',status=1;
INSERT INTO yunanexus_permission.perm_permission(perm_code,resource,action,description,status) VALUES('system:root','/**','*','根管理员平台权限',1) ON DUPLICATE KEY UPDATE resource='/**',action='*',status=1;
INSERT IGNORE INTO yunanexus_permission.perm_role_permission(role_id,permission_id) SELECT r.id,p.id FROM yunanexus_permission.perm_role r CROSS JOIN yunanexus_permission.perm_permission p WHERE BINARY r.role_code='admin' AND p.perm_code='system:root';
INSERT IGNORE INTO yunanexus_permission.perm_user_role(user_id,role_id) SELECT """+user_id+""",id FROM yunanexus_permission.perm_role WHERE BINARY role_code='admin';
COMMIT;
"""+(R/'ops/portal-client.sql').read_text())
for name in ['permission','notice']:
 path=R/('config/'+name+'.yaml');config=json.loads(path.read_text())
 config.setdefault('yunanexus',{})['admin']={'verification-url':'http://auth:8100/internal/auth/admin/check'}
 path.write_text(json.dumps(config,indent=2)+'\n');path.chmod(0o644)
path=R/'config/gateway.yaml';config=json.loads(path.read_text())
paths=config['yunanexus']['gateway']['permission'].setdefault('permit-paths',[])
for item in ['/api/v1/notice/inbox','/api/v1/notice/inbox/*','/api/v1/notice/read/*','/api/v1/user/profile','/api/v1/user/tags','/api/v1/user/badges']:
 if item not in paths:paths.append(item)
path.write_text(json.dumps(config,indent=2)+'\n');path.chmod(0o644)
print('Admin role bound; internal verification, own inbox and fixed SSO callback configured')
