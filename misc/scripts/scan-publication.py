#!/usr/bin/env python3
"""Scan files selected for publication. Report locations only, never credential values."""
import argparse,re,subprocess,sys
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--staged',action='store_true');p.add_argument('--root',default='.');args=p.parse_args()
root=Path(args.root).resolve()
cmd=['git','-C',str(root),'diff','--cached','--name-only','--diff-filter=ACMR','-z'] if args.staged else ['git','-C',str(root),'ls-files','--cached','--others','--exclude-standard','-z']
files=set(subprocess.check_output(cmd).decode().split('\0'))
patterns=[('private-key',re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----')),('github-token',re.compile(rb'\b(?:gh[pousr]_[A-Za-z0-9_]{30,}|github_pat_[A-Za-z0-9_]{40,})\b')),('aws-access-key',re.compile(rb'\bAKIA[0-9A-Z]{16}\b'))]
forbidden=['application-local.yaml','application-local.yml','.env','id_rsa','id_ed25519']
findings=[]
for name in sorted(files):
 if not name:continue
 path=root/name
 if not path.is_file():continue
 if (name.startswith('yunanexus-') and '/data/' in name) or path.name in forbidden or any(x in path.parts for x in ['.idea','.rocketmq','node_modules','target','secrets']):
  findings.append((name,0,'private-or-generated-file'));continue
 data=path.read_bytes()
 if path.name.endswith(('.tar.gz','.hprof','.log')):findings.append((name,0,'generated-artifact'));continue
 if len(data)>5*1024*1024:findings.append((name,0,'oversize-file'));continue
 if b'\x00' in data:continue
 # Test fixtures and secret-generation programs may name markers but must not contain a real key block.
 for label,pattern in patterns:
  for match in pattern.finditer(data):
   if label=='private-key' and b'-----END ' not in data[match.end():]:continue
   findings.append((name,data[:match.start()].count(b'\n')+1,label))
for name,line,label in findings:print(f'{name}:{line}: {label}')
print(f'Scanned {len(files)-1} publication candidates; findings={len(findings)}')
sys.exit(bool(findings))
