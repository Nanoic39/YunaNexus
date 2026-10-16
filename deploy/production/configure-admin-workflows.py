#!/usr/bin/env python3
"""Additive production migration; credentials stay in /YunaNexus/platform/secrets."""
from pathlib import Path
import os,subprocess,gzip,datetime,yaml
R=Path('/YunaNexus/platform');os.umask(0o077)
if not os.path.ismount('/YunaNexus'):raise RuntimeError('Data disk is not mounted')
smtp=R/'secrets/smtp.env'
if not smtp.is_file() or smtp.stat().st_mode & 0o077:raise RuntimeError('Provide root-only smtp.env first')
compose=R/'ops/compose.sh'
backup=R/'backups'/('admin-workflows-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'.sql.gz')
with backup.open('xb') as target:
 with gzip.GzipFile(fileobj=target,mode='wb') as compressed:
  p=subprocess.Popen([str(compose),'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --skip-lock-tables --databases yunanexus_auth yunanexus_permission yunanexus_user_0'],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL)
  while chunk:=p.stdout.read(1024*1024):compressed.write(chunk)
  if p.wait()!=0:raise RuntimeError('Backup failed; migration not applied')
backup.chmod(0o600)
sql=(R/'ops/20261016-admin-workflows.sql').read_text()
subprocess.run([str(compose),'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'],input=sql,text=True,check=True,stdout=subprocess.DEVNULL)
subprocess.run([str(compose),'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'],input="INSERT IGNORE INTO yunanexus_permission.perm_user_role(user_id,role_id) SELECT u.user_id,r.id FROM yunanexus_auth.auth_user u CROSS JOIN yunanexus_permission.perm_role r WHERE u.username='nanoic' AND r.role_code='super_admin' AND r.status=1;",text=True,check=True,stdout=subprocess.DEVNULL)
for name in ['user','notice','gateway']:
 path=R/'config'/(name+'.yaml');config=yaml.safe_load(path.read_text()) or {}
 if name=='user':config.setdefault('yunanexus',{}).setdefault('admin',{})['verification-url']='http://auth:8100/internal/auth/admin/check'
 if name=='notice':config.setdefault('spring',{})['mail']={'host':'${SMTP_HOST:}','port':'${SMTP_PORT:465}','username':'${SMTP_USERNAME:}','password':'${SMTP_PASSWORD:}','properties':{'mail.smtp.auth':True,'mail.smtp.ssl.enable':True,'mail.smtp.ssl.checkserveridentity':True,'mail.smtp.connectiontimeout':5000,'mail.smtp.timeout':10000,'mail.smtp.writetimeout':10000}}
 if name=='gateway':
  gate=config.setdefault('yunanexus',{}).setdefault('gateway',{})
  if 'permit-paths' in gate and '/api/v1/auth/password-reset/**' not in gate['permit-paths']:gate['permit-paths'].append('/api/v1/auth/password-reset/**')
 path.write_text(yaml.safe_dump(config,allow_unicode=True,sort_keys=False));path.chmod(0o644)
# Keep the existing compose configuration and only add the dedicated mail env file.
path=R/'ops/compose.yaml';s=path.read_text();needle='    - /YunaNexus/platform/secrets/notice.env\n'
if '/YunaNexus/platform/secrets/smtp.env' not in s:
 if needle not in s:raise RuntimeError('Unexpected compose layout')
 path.write_text(s.replace(needle,needle+'    - /YunaNexus/platform/secrets/smtp.env\n'))
path=R/'config/nginx.conf';s=path.read_text()
if 'proxy_set_header X-Real-IP' not in s:path.write_text(s.replace('  proxy_connect_timeout 3s;','  proxy_set_header X-Real-IP $remote_addr;\n  proxy_connect_timeout 3s;'))
print('Backup and additive migration completed; nanoic super_admin bound; private SMTP configuration linked',flush=True)
