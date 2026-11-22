"""Isolated source-registry and helper-grant migration checks."""
import os,json,sqlite3,tempfile,time
from pathlib import Path
from public_sources import CATALOGUES,parse_catalogue

home=Path(tempfile.mkdtemp(prefix='blaxyt-017-check-'));os.environ['SHOP_DB']=str(home/'shop.sqlite3')
with sqlite3.connect(os.environ['SHOP_DB']) as db:
    db.execute("CREATE TABLE grants(uid TEXT NOT NULL,badge TEXT NOT NULL CHECK(badge IN ('developer','supporter','ambassador')),expires INTEGER NOT NULL DEFAULT 0,revoked INTEGER NOT NULL DEFAULT 0,updated INTEGER NOT NULL,PRIMARY KEY(uid,badge))")
    db.execute("INSERT INTO grants VALUES('123456789123','supporter',123,0,456)")
import shop
shop.prepare();shop.prepare()
checks=0
def check(ok):
    global checks
    assert ok;checks+=1
with shop.database() as db:
    check(tuple(db.execute('SELECT * FROM grants').fetchone())==('123456789123','supporter',123,0,456))
    db.execute("INSERT INTO grants VALUES('123456789123','helper',0,0,789)")
    check(db.execute('PRAGMA integrity_check').fetchone()[0]=='ok')
source=CATALOGUES[1];uid='123456789123'
fixture={'badges':[{'id':'helper','users':[uid],'title':'Helper','text':'Original source','colour':'#123456'}]}
check(parse_catalogue(source,fixture)[0][0]['url'].endswith('/note.png'))
shop.IMPORTS.fetch=lambda *a,**k:json.dumps(fixture).encode()
shop.IMPORTS.image=lambda url:'import/'+'a'*64+'.png'
shop.AUTOMATIC.seed={'catalogue':[],'users':{},'special':[]}
check(shop.AUTOMATIC.refresh_catalogue(source)==(1,1))
with shop.database() as db:
    catalogue,users=shop.AUTOMATIC.feed(db)
check(users[uid]==[shop.AUTOMATIC.identifier('tiktokmi','helper')])
check(catalogue[0]['source']=='tiktokmi')
check(shop.AUTOMATIC.identifier('margyt','helper')!=users[uid][0])
for bad in ['../../secret','https://localhost/a.png','/a.png']:
    fixture['badges'][0]['image']=bad
    try:shop.AUTOMATIC.refresh_catalogue(source)
    except ValueError:checks+=1
    else:raise AssertionError('Unsafe image accepted')
    with shop.database() as db:check(shop.AUTOMATIC.feed(db)[1]==users)
fixture['badges'][0].pop('image');fixture['badges'][0]['users']=[]
shop.AUTOMATIC.refresh_catalogue(source)
with shop.database() as db:check(uid not in shop.AUTOMATIC.feed(db)[1])
with shop.database() as db:check(db.execute("SELECT count(*) FROM grants WHERE badge='helper'").fetchone()[0]==1)
print(str(checks)+' source validation, namespace isolation, removal and helper migration checks passed.')
