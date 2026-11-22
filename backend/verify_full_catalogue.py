import hashlib,json,os,tempfile,time,zipfile
from pathlib import Path
os.environ['SHOP_DB']=str(Path(tempfile.mkdtemp(prefix='blaxyt-full-badges-'))/'shop.sqlite3')
import shop
from automatic_badges import AutomaticBadges
from badge_import import BadgeImports
from identity import LinkError
shop.prepare();service=shop.AUTOMATIC;checks=0
def check(value):
    global checks
    assert value;checks+=1
seed=service.seed
check(len(seed['users'])==4559)
check(len(seed['special'])==2)
check('6784748111338488838' in seed['users'])
check(service.identifier('you','supporter')!=service.identifier('margyt','supporter'))
with zipfile.ZipFile(Path(__file__).with_name('badge-seed-images.zip')) as archive:
    for item in archive.infolist():
        raw=archive.read(item);check(hashlib.sha256(raw).hexdigest()==item.filename.split('.')[0])
        check(raw.startswith(b'\x89PNG\r\n\x1a\n') or raw.startswith(b'\xff\xd8\xff') or raw[:4]==b'RIFF')
    check(all(b['image'][7:] in archive.namelist() for b in seed['catalogue']))
for i in range(300):
    uid=str(8000000000000000000+i)
    badge={'id':service.identifier('you','supporter'),'source':'you','title':'Supporter','description':'Source','colour':'','image':'you_supporter'}
    service.save('you:'+uid,[badge],{uid:[badge['id']]})
with shop.database() as db:
    check(db.execute("SELECT count(*) FROM public_badge_snapshots WHERE key LIKE 'you:%'").fetchone()[0]==300)
    db.execute('UPDATE public_badge_snapshots SET checked=1')
    catalogue,users=service.feed(db);check('8000000000000000000' in users)
    standard=next(b for b in catalogue if b['id']==service.identifier('you','supporter'))
    check(standard['image']==seed['builtin_images']['supporter'])
    check('6784748111338488838' in users)
    check(db.execute('SELECT count(*) FROM grants').fetchone()[0]==0)
for uid in seed['special']:
    service.save('you:'+uid,[],{})
    with shop.database() as db:check(service.identifier('you','developer') in service.feed(db)[1][uid])
for url in ('https://cdn.jsdelivr.net/gh/other/repo@main/test.png','https://localhost/a.png'):
    try:BadgeImports.fetch(url)
    except LinkError:checks+=1
    else:raise AssertionError('Unapproved source permitted')
print(str(checks)+' full catalogue, source isolation, native artwork and durable-cache checks passed.')
