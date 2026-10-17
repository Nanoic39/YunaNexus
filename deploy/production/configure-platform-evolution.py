#!/usr/bin/env python3
"""Additive auth schema and authenticated gateway route; all files stay in /YunaNexus."""
import json,os,subprocess,yaml
from pathlib import Path
R=Path('/YunaNexus/platform')
if not os.path.ismount('/YunaNexus'):raise SystemExit('Data disk is not mounted')
os.umask(0o077)
for name in ['20261017-application-category.sql','20261017-points.sql','20261017-application-access.sql']:
 statement=(R/'ops'/name).read_text()
 subprocess.run([str(R/'ops/compose.sh'),'exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'],input=statement,text=True,check=True)
p=R/'config/gateway.yaml';config=yaml.safe_load(p.read_text())
paths=config.setdefault('yunanexus',{}).setdefault('gateway',{}).setdefault('permission',{}).setdefault('permit-paths',[])
route='/api/v1/auth/application/permissions/check'
if route not in paths:paths.append(route)
tmp=p.with_suffix('.evolution.tmp');tmp.write_text(json.dumps(config,ensure_ascii=False,indent=2)+'\n');tmp.chmod(0o644);tmp.replace(p)
print('Application classification, points ledger and isolated RBAC schema configured')
