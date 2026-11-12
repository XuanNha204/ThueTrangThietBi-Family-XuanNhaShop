"""Create a source release without dev data, credentials or generated artifacts."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib,json,datetime,os
root=Path(__file__).resolve().parent.parent
destination=root/'.dev/releases'
destination.mkdir(parents=True,exist_ok=True)
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
archive=destination/f'XuanNhaShop-vps-{stamp}.zip'
excluded={'.dev','.git','node_modules','target','dist','test-results','playwright-report','secrets','backups','__pycache__'}
manifest={}
with ZipFile(archive,'w',ZIP_DEFLATED) as z:
 for directory,dirs,names in os.walk(root,followlinks=False):
  dirs[:]=[d for d in dirs if d not in excluded and not (Path(directory)/d).is_symlink()]
  for name in names:
   f=Path(directory)/name
   if not f.is_file() or f.is_symlink():continue
   rel=f.relative_to(root)
   if f.name=='TaiLieu.md' or f.name.startswith('.env') and f.name!='.env.example':continue
   if f.suffix in {'.log','.tsbuildinfo','.pem','.key','.p12'} or f.name=='.backup.lock':continue
   if rel.parts[0] not in {'backend','web-ui','deploy','scripts','docs','design-system'} and f.name not in {'README.md','AGENTS.md','CHANGELOG.md','package.json','package-lock.json','.gitignore','.dockerignore','.gitattributes'}:continue
   z.write(f,rel.as_posix());manifest[rel.as_posix()]=hashlib.sha256(f.read_bytes()).hexdigest()
 z.writestr('release-manifest.json',json.dumps(manifest,indent=2,ensure_ascii=False))
print(json.dumps({'archive':str(archive),'files':len(manifest),'sha256':hashlib.sha256(archive.read_bytes()).hexdigest()}))
