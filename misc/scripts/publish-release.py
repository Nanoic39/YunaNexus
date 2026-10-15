#!/usr/bin/env python3
"""Stream CI-validated artifacts to an immutable GitHub release; never print tokens."""
import os,json,urllib.request,urllib.error,urllib.parse,pathlib,http.client,re,sys

def main():
 repo=os.environ['GITHUB_REPOSITORY'];sha=os.environ['GITHUB_SHA'];token=os.environ['GITHUB_TOKEN'];kind=os.environ['RELEASE_KIND']
 if not re.fullmatch('[a-f0-9]{40}',sha) or kind not in ['backend','frontend']:raise ValueError('Invalid release identity')
 headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','Content-Type':'application/json','User-Agent':'YunaNexus-CI','X-GitHub-Api-Version':'2022-11-28'}
 def api(path,data=None,method=None):
  req=urllib.request.Request('https://api.github.com'+path,headers=headers,data=json.dumps(data).encode() if data is not None else None,method=method)
  return json.load(urllib.request.urlopen(req,timeout=60))
 tag='deploy-'+kind+'-'+sha
 existing=api('/repos/'+repo+'/releases?per_page=20')
 matches=[r for r in existing if r['tag_name']==tag]
 if matches and not matches[0]['draft']:raise RuntimeError('Immutable release already exists')
 print('Creating deployment release',tag,flush=True)
 release=matches[0] if matches else api('/repos/'+repo+'/releases',{'tag_name':tag,'target_commitish':sha,'name':tag,'body':'Production build '+sha,'draft':True,'prerelease':True})
 present={a['name'] for a in release['assets']}
 for name in [kind+'.tar.gz','SHA256SUMS']:
  if name in present:continue
  path=pathlib.Path('release')/name
  url=urllib.parse.urlsplit(release['upload_url'].split('{')[0]+'?name='+name)
  if url.scheme!='https' or url.hostname!='uploads.github.com':raise ValueError('Unexpected asset upload host')
  print('Uploading',name,path.stat().st_size,'bytes',flush=True)
  conn=http.client.HTTPSConnection(url.hostname,timeout=180,blocksize=1024*1024)
  upload_headers={**headers,'Content-Type':'application/octet-stream','Content-Length':str(path.stat().st_size)}
  with path.open('rb') as stream:conn.request('POST',url.path+'?'+url.query,body=stream,headers=upload_headers)
  response=conn.getresponse();data=response.read();status=response.status;conn.close()
  if status>=400:raise RuntimeError('Asset upload HTTP '+str(status)+' '+data.decode(errors='replace')[:500])
 api('/repos/'+repo+'/releases/'+str(release['id']),{'draft':False},'PATCH')
 print('Published',tag,flush=True)

if __name__=='__main__':
 try:main()
 except urllib.error.HTTPError as error:
  detail=error.read().decode(errors='replace')[:1000]
  message=('GitHub release API HTTP '+str(error.code)+' '+detail).replace('%','%25').replace('\r','%0D').replace('\n','%0A')
  print('::error::'+message,flush=True);sys.exit(1)
 except Exception as error:
  message=(type(error).__name__+': '+str(error)).replace('%','%25').replace('\r','%0D').replace('\n','%0A')
  print('::error::'+message,flush=True);sys.exit(1)
