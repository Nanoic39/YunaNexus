#!/usr/bin/env python3
"""Publish a CI-validated immutable release using the job's built-in GitHub token."""
import os,json,urllib.request,pathlib,subprocess
repo=os.environ['GITHUB_REPOSITORY'];sha=os.environ['GITHUB_SHA'];token=os.environ['GITHUB_TOKEN'];kind=os.environ['RELEASE_KIND']
headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','User-Agent':'YunaNexus-CI','X-GitHub-Api-Version':'2022-11-28'}
def api(path,data=None):
 req=urllib.request.Request('https://api.github.com'+path,headers=headers,data=json.dumps(data).encode() if data is not None else None)
 return json.load(urllib.request.urlopen(req,timeout=60))
tag='deploy-'+kind+'-'+sha
existing=api('/repos/'+repo+'/releases?per_page=20')
if any(r['tag_name']==tag for r in existing):raise SystemExit('Release already exists; immutable deployment artifact must not be replaced')
release=api('/repos/'+repo+'/releases',{'tag_name':tag,'target_commitish':sha,'name':tag,'body':'Production build '+sha,'draft':True,'prerelease':True})
for name in [kind+'.tar.gz','SHA256SUMS']:
 path=pathlib.Path('release')/name
 # curl streams large files without loading an entire backend archive into Python memory.
 env=dict(os.environ);env['UPLOAD_URL']=release['upload_url'].split('{')[0]+'?name='+name
 result=subprocess.run(['curl','--fail','--silent','--show-error','--retry','2','-X','POST','--config','-','-H','Content-Type: application/octet-stream','--data-binary','@'+str(path),env['UPLOAD_URL']],input=('header = \"Authorization: Bearer '+token+'\"\n').encode(),capture_output=True)
 if result.returncode:raise SystemExit('Release asset upload failed')
req=urllib.request.Request('https://api.github.com/repos/'+repo+'/releases/'+str(release['id']),headers=headers,data=json.dumps({'draft':False}).encode(),method='PATCH')
urllib.request.urlopen(req,timeout=60).close()
print('Published',tag)
