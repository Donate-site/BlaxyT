"""Isolated ownership/authority checks. No live accounts or payments are modified."""
import hashlib,json,os,tempfile,time
from pathlib import Path
os.environ['SHOP_DB']=str(Path(tempfile.mkdtemp(prefix='blaxyt-import-check-'))/'shop.sqlite3')
import shop
from identity import LinkError
from badge_import import BadgeImports
shop.prepare();service=shop.IMPORTS;uid='123456789123';other='987654321123'
code='blaxy-'+'a'*16;holder='S'*40;now=int(time.time());checks=0
with shop.database() as db:
    db.execute('INSERT INTO link_sessions(code,uid,name,secret_hash,created,verified) VALUES(?,?,?,?,?,1)',
               (code,uid,'fixture',hashlib.sha256(holder.encode()).hexdigest(),now))
    db.execute('INSERT INTO identities(uid,code,name,bio_present,checked,next_check) VALUES(?,?,?,1,?,?)',
               (uid,code,'fixture',now,now+600))
    db.execute("INSERT INTO grants VALUES(?,'supporter',0,0,?)",(uid,now))

def check(value):
    global checks
    assert value;checks+=1
def deny(call,status):
    global checks
    try:call()
    except LinkError as e:assert e.status==status,(str(e),e.status,status)
    else:raise AssertionError('Unexpected success')
    checks+=1
def reset():
    with shop.database() as db:db.execute('DELETE FROM import_attempts')
rows=[{'key':'owner','title':'Owner','description':'Original description','colour':'AB12EF','url':'builtin:you'}]
service.margyt=lambda current: rows if current==uid else []
deny(lambda:service.transfer(other,code,holder,'margyt'),403)
deny(lambda:service.transfer(uid,code,'X'*40,'margyt'),403)
with shop.database() as db:db.execute('UPDATE link_sessions SET verified=0')
deny(lambda:service.transfer(uid,code,holder,'margyt'),403)
with shop.database() as db:db.execute('UPDATE link_sessions SET verified=1')
check(service.transfer(uid,code,holder,'margyt')['count']==1)
deny(lambda:service.transfer(uid,code,holder,'margyt'),429)
with shop.database() as db:
    catalogue,users=service.feed(db);bid=users[uid][0]
    check(bid.startswith('import:margyt:') and catalogue[0]['title']=='Owner' and catalogue[0]['source']=='margyt')
    check(db.execute('SELECT badge FROM grants WHERE uid=?',(uid,)).fetchone()[0]=='supporter')
    check(db.execute('SELECT count(*) FROM grants').fetchone()[0]==1)
    check(db.execute('SELECT count(*) FROM payments').fetchone()[0]==0)
deny(lambda:shop.LINKS.admin_status(uid,code,holder),403)
reset();check(service.transfer(uid,code,holder,'margyt')['count']==1)
with shop.database() as db:check(db.execute('SELECT count(*) FROM imported_badges').fetchone()[0]==1)
shop.LINKS.badges(uid,code,holder,['supporter',bid],[bid])
with shop.database() as db:
    pref=db.execute('SELECT * FROM badge_preferences').fetchone();check(json.loads(pref['hidden'])==[bid])
deny(lambda:shop.LINKS.badges(uid,code,holder,['owner'],[]),400)
rows[0]['description']='Updated description';reset();service.transfer(uid,code,holder,'margyt')
with shop.database() as db:check(db.execute('SELECT count(*) FROM imported_badges').fetchone()[0]==1)
def broken(current):raise RuntimeError('Source down')
service.margyt=broken;reset();deny(lambda:service.transfer(uid,code,holder,'margyt'),503)
with shop.database() as db:check(db.execute('SELECT count(*) FROM imported_badges').fetchone()[0]==1)
service.margyt=lambda current:[];reset();check(service.transfer(uid,code,holder,'margyt')['count']==0)
with shop.database() as db:check(db.execute('SELECT count(*) FROM imported_badges').fetchone()[0]==1)
with shop.database() as db:db.execute("UPDATE identities SET name='renamed'")
with shop.database() as db:check(uid in service.feed(db)[1])
with shop.database() as db:db.execute('UPDATE identities SET bio_present=0')
with shop.database() as db:check(not service.feed(db)[1])
reset();deny(lambda:service.transfer(uid,code,holder,'margyt'),403)
for url in ['http://ttyou.app/a','https://localhost/a','https://127.0.0.1/a','https://ttyou.app@evil.test/a','https://ttyou.app:444/a']:
    deny(lambda:BadgeImports.fetch(url),503)
check(service.read_image('../../shop.sqlite3') is None)
service.fetch=lambda *a,**k:json.dumps({'uid':other,'taken':True,'badge':{}}).encode()
try:service.you(uid)
except ValueError:checks+=1
else:raise AssertionError('Mismatched source UID accepted')
with shop.database() as db:check(db.execute('PRAGMA integrity_check').fetchone()[0]=='ok')
print(str(checks)+' import ownership, authority, failure preservation, UID and URL checks passed.')
