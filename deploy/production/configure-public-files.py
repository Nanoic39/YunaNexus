#!/usr/bin/env python3
"""Apply publication schema and exact anonymous route to the project gateway only."""
import json,os,subprocess,yaml
from pathlib import Path
R=Path('/YunaNexus/platform')
if not os.path.ismount('/YunaNexus'):raise SystemExit('Data disk is not mounted')
os.umask(0o077)
statement=(R/'ops/20261017-public-files.sql').read_text()
subprocess.run([str(R/'ops/compose.sh'),'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'],input=statement,text=True,check=True)
p=R/'config/gateway.yaml';config=yaml.safe_load(p.read_text())
gateway=config.setdefault('yunanexus',{}).setdefault('gateway',{})
paths=gateway.setdefault('permit-paths',['/api/v1/status', '/api/v1/status/**', '/api/v1/file/public/assets/*', '/api/v1/file/public/share/info', '/api/v1/file/public/share/download', '/api/v1/auth/login', '/api/v1/auth/register', '/api/v1/auth/refresh', '/api/v1/auth/csrf', '/api/v1/auth/captcha', '/api/v1/auth/sso/**', '/api/v1/auth/password-reset/**', '/api/v1/auth/unlock-appeal', '/api/v1/auth/admin/**', '/api/v1/permission/admin/**', '/api/v1/notice/portal/admin/**', '/api/v1/notice/portal/content', '/api/v1/notice/portal/page', '/actuator/**'])
if '/api/v1/file/public/assets/*' not in paths:paths.append('/api/v1/file/public/assets/*')
tmp=p.with_suffix('.publication.tmp');tmp.write_text(json.dumps(config,ensure_ascii=False,indent=2)+'\n');tmp.chmod(0o644);tmp.replace(p)
print('Publication schema and anonymous asset route configured')
