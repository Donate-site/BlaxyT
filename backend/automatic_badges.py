"""Public badge compatibility by TikTok UID. No local identity or role is granted.

MargyT publishes a complete catalogue. TikTok You offers individual lookups;
those are queued only for IDs requested by clients, with bounded workers and cache.
No viewer ID, private holder proof, Telegram ID or browsing relationship is stored.
"""
import hashlib
import json
import queue
import re
import threading
import time
from pathlib import Path
from public_sources import CATALOGUES, parse_catalogue

UID=re.compile(r'[0-9]{6,24}\Z')
MAX_AGE=86400


class AutomaticBadges:
    def __init__(self,database,imports):
        self.database,self.imports=database,imports
        self.queue=queue.Queue(maxsize=128)
        self.lock=threading.Lock()
        self.pending=set()
        self.started=False
        self.images={}
        self.seed={'catalogue':[],'users':{},'special':[]}

    def prepare(self):
        seed=Path(__file__).with_name('you_catalogue.json')
        if seed.is_file():self.seed=json.loads(seed.read_text(encoding='utf-8'))
        with self.database() as db:
            db.execute('''CREATE TABLE IF NOT EXISTS public_badge_snapshots(
                key TEXT PRIMARY KEY, checked INTEGER NOT NULL DEFAULT 0,
                attempted INTEGER NOT NULL DEFAULT 0, payload TEXT NOT NULL DEFAULT '{}')''')

    @staticmethod
    def identifier(source,key):
        return 'import:'+source+':'+hashlib.sha256(json.dumps([source,key],ensure_ascii=False).encode()).hexdigest()[:24]

    def metadata(self,source,row):
        title,description,colour=row['title'],row['description'],row['colour']
        if not all(isinstance(v,str) for v in (title,description,colour)):raise ValueError('Invalid metadata')
        if colour!='app' and not re.fullmatch(r'#?[0-9a-fA-F]{6}|',colour):colour=''
        url=row['url']
        with self.lock:image=self.images.get(url)
        if image is None:
            image=self.imports.image(url)
            with self.lock:
                if len(self.images)>2048:self.images.clear()
                self.images[url]=image
        return {'id':self.identifier(source,row['key']),'source':source,'title':title[:160],
                'description':description[:1200],'colour':colour,'image':image}

    def save(self,key,catalogue,users):
        now=int(time.time())
        body=json.dumps({'catalogue':catalogue,'users':users},ensure_ascii=False,separators=(',',':'))
        with self.database() as db:
            db.execute('INSERT INTO public_badge_snapshots VALUES(?,?,?,?) ON CONFLICT(key) DO UPDATE SET '
                       'checked=excluded.checked,attempted=excluded.attempted,payload=excluded.payload',(key,now,now,body))
            # Confirmed positive records are durable; viewing more accounts must not erase a badge.

    def refresh_catalogue(self,source):
        data=json.loads(self.imports.fetch(source.catalogue,8_000_000))
        catalogue=[];users={}
        for row,owners in parse_catalogue(source,data):
            badge=self.metadata(source.key,row);catalogue.append(badge)
            for uid in owners:users.setdefault(uid,[]).append(badge['id'])
        self.save(source.key,catalogue,users)
        if source.key=='margyt' and getattr(self,'frames',None) is not None:
            self.frames.ingest(data)
        return len(catalogue),len(users)

    def refresh_margyt(self):
        return self.refresh_catalogue(CATALOGUES[0])

    def refresh_you(self,uid):
        rows=self.imports.you(uid)
        if len(rows)>8:raise ValueError('Too many badges')
        catalogue=[self.metadata('you',row) for row in rows]
        self.save('you:'+uid,catalogue,{uid:[r['id'] for r in catalogue]} if catalogue else {})

    def request(self,uids):
        if not isinstance(uids,list) or len(uids)>12 or any(not isinstance(u,str) or not UID.fullmatch(u) for u in uids):
            raise ValueError('Invalid UID batch')
        accepted=[]
        for uid in dict.fromkeys(uids):
            key='you:'+uid;now=int(time.time())
            with self.lock:
                if uid in self.pending:accepted.append(uid);continue
                if self.queue.full():continue
                with self.database() as db:
                    row=db.execute('SELECT * FROM public_badge_snapshots WHERE key=?',(key,)).fetchone()
                    if row:
                        has_badge=bool(json.loads(row['payload']).get('users'))
                        ttl=1800 if has_badge else 21600
                        if row['checked']>now-ttl or row['attempted']>now-120:
                            accepted.append(uid);continue
                    db.execute('INSERT INTO public_badge_snapshots(key,attempted) VALUES(?,?) '
                        'ON CONFLICT(key) DO UPDATE SET attempted=excluded.attempted',(key,now))
                self.pending.add(uid);self.queue.put_nowait(uid);accepted.append(uid)
        return {'ok':True,'accepted':accepted}

    def worker(self):
        while True:
            uid=self.queue.get()
            try:self.refresh_you(uid)
            except Exception:pass # Network failure must not replace a confirmed badge with absence.
            finally:
                with self.lock:self.pending.discard(uid)
                self.queue.task_done()
            time.sleep(1) # At most two independent lookups per second across workers.

    def catalogue_loop(self):
        while True:
            delay=600
            for source in CATALOGUES:
                try:self.refresh_catalogue(source)
                except Exception:delay=60
            try:
                with self.database() as db:
                    own=[r[0] for r in db.execute('SELECT uid FROM identities LIMIT 128')]
                for i in range(0,len(own),12):self.request(own[i:i+12])
            except Exception:pass
            time.sleep(delay)

    def start(self):
        with self.lock:
            if self.started:return
            self.started=True
        for n in range(2):threading.Thread(target=self.worker,daemon=True,name='public-badge-'+str(n)).start()
        threading.Thread(target=self.catalogue_loop,daemon=True,name='public-badge-catalogue').start()

    def feed(self,db):
        catalogue={b['id']:dict(b) for b in self.seed['catalogue']}
        users={uid:list(ids) for uid,ids in self.seed['users'].items()}
        special=set(self.seed.get('special',[]))
        for row in db.execute('SELECT key,payload FROM public_badge_snapshots WHERE checked>0 ORDER BY key'):
            data=json.loads(row['payload'])
            if row['key'].startswith('you:'):
                uid=row['key'][4:]
                # The shop lookup does not enumerate staff roles. It cannot revoke a staff
                # badge confirmed by the application's full catalogue.
                if uid in special:
                    continue
                existing=users.get(uid,[])
                users[uid]=[bid for bid in existing if not bid.startswith('import:you:')]
            for badge in data.get('catalogue',[]):
                # A legacy shop response used a synthetic default icon. It must not
                # overwrite the original shared artwork for the entire catalogue.
                if badge['id']==self.identifier('you','supporter') and badge['id'] in catalogue:continue
                catalogue[badge['id']]=badge
            for uid,ids in data.get('users',{}).items():users.setdefault(uid,[]).extend(ids)
        users={uid:list(dict.fromkeys(ids)) for uid,ids in users.items() if ids}
        # Catalogue discovery is independent from ownership. Newly announced
        # badges are immediately understood by existing APKs; only users grants display them.
        return [catalogue[bid] for bid in sorted(catalogue)],users
