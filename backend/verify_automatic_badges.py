"""Isolated public badge tests: no live grants, identities or payments modified."""
import json,os,tempfile,time
from pathlib import Path
os.environ['SHOP_DB']=str(Path(tempfile.mkdtemp(prefix='blaxyt-auto-check-'))/'shop.sqlite3')
import shop
from automatic_badges import AutomaticBadges,MAX_AGE
service=shop.AUTOMATIC;shop.prepare();service.seed={'catalogue':[],'users':{},'special':[]};checks=0
alice='123456789123';bob='987654321123';now=int(time.time())
source={'badges':[{'id':'owner','image':'owner.png?v=a1','title':'Owner','text':'Source owner','colour':'8DD1B0','users':[alice]},
                  {'id':'developer','image':'dev.png','title':'Developer','text':'Source developer','colour':'','users':[bob]}]}
shop.IMPORTS.fetch=lambda *a,**k:json.dumps(source).encode()
shop.IMPORTS.image=lambda url:'import/'+'a'*64+'.png'
def check(value):
    global checks
    assert value;checks+=1
def feed():
    with shop.database() as db:return service.feed(db)
check(service.refresh_margyt()==(2,2))
catalogue,users=feed();ownerid=service.identifier('margyt','owner')
check(users[alice]==[ownerid])
check(any(b['id']==ownerid and b['title']=='Owner' and b['source']=='margyt' for b in catalogue))
check(all(b['id'].startswith('import:') for b in catalogue))
source['badges'].append({'id':'party_hat','image':'party_hat.png?v=51cdeaac','title_ru':'Праздничный колпак','text_ru':'Побывал на днюхе у Нарезанного','colour':'F2B33D','users':[]})
check(service.refresh_margyt()==(3,2))
hat=service.identifier('margyt','party_hat')
check(any(b['id']==hat and b['title']=='Праздничный колпак' for b in feed()[0]))
check(all(hat not in ids for ids in feed()[1].values()))
source['badges'][-1]['users']=[bob];service.refresh_margyt()
check(hat in feed()[1][bob] and hat not in feed()[1][alice])
source['badges'][-1]['users']=[];service.refresh_margyt()
check(all(hat not in ids for ids in feed()[1].values()))
with shop.database() as db:
    check(db.execute('SELECT count(*) FROM identities').fetchone()[0]==0)
    check(db.execute('SELECT count(*) FROM grants').fetchone()[0]==0)
    check(db.execute('SELECT count(*) FROM payments').fetchone()[0]==0)
    db.execute("INSERT INTO grants VALUES(?,'developer',0,0,?)",(alice,now))
source['badges'][0]['users'].append('bad_uid')
try:service.refresh_margyt()
except ValueError:checks+=1
else:raise AssertionError('Malformed source accepted')
check(feed()[1]==users)
source['badges'][0]['users']=[alice]
source['badges'][0]['title']='Owner updated';service.refresh_margyt()
check(feed()[1][alice]==[ownerid])
source['badges'][0]['users']=[];service.refresh_margyt();check(alice not in feed()[1])
with shop.database() as db:check(db.execute('SELECT badge FROM grants WHERE uid=?',(alice,)).fetchone()[0]=='developer')
shop.IMPORTS.you=lambda uid:[{'key':'supporter','title':'Supporter','description':'Original','colour':'','url':'builtin:you'}]
service.refresh_you(alice);check(alice in feed()[1])
check(service.request([alice])['accepted']==[alice] and service.queue.empty())
with shop.database() as db:db.execute("UPDATE public_badge_snapshots SET checked=?,attempted=0 WHERE key=?",(now-1900,'you:'+alice))
check(service.request([alice])['accepted']==[alice] and service.queue.qsize()==1)
check(service.request([alice])['accepted']==[alice] and service.queue.qsize()==1)
def offline(uid):raise OSError('Source down')
shop.IMPORTS.you=offline
try:service.refresh_you(alice)
except OSError:checks+=1
check(alice in feed()[1])
shop.IMPORTS.you=lambda uid:[];service.refresh_you(alice);check(alice not in feed()[1])
check(service.request([bob])['accepted']==[bob])
for invalid in [['owner'],['http://localhost'],[123],['1']*13]:
    try:service.request(invalid)
    except ValueError:checks+=1
    else:raise AssertionError('Invalid queue request accepted')
with shop.database() as db:db.execute('UPDATE public_badge_snapshots SET checked=?',(now-MAX_AGE-1,))
check(bob in feed()[1]) # Confirmed badges survive source/network outages.
check(service.queue.maxsize==128)
with shop.database() as db:check(db.execute('PRAGMA integrity_check').fetchone()[0]=='ok')
print(str(checks)+' automatic source, Owner visibility, cache, revocation and role-separation checks passed.')
