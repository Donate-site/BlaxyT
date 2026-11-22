"""Independent blaxy IDs; public profile proofs and private app approval of Telegram linking."""
import hashlib
import hmac
import json
import re
import secrets
import threading
import time
import urllib.request

UID = re.compile(r'[0-9]{6,24}\Z')
NAME = re.compile(r'[A-Za-z0-9_.]{1,32}\Z')
CODE = re.compile(r'blaxy-[a-z0-9]{16}\Z')
NOTICE = ('Не удаляйте код blaxy-… из описания профиля. Можно добавить свой текст — главное, '
          'чтобы код остался. Без кода значки скроются; после его возвращения и проверки '
          'появятся снова. Покупки сохранятся.')


class LinkError(Exception):
    def __init__(self, text, status=400):
        super().__init__(text)
        self.status = status


def read_profile(name):
    if not NAME.fullmatch(name): raise LinkError('Неверный ник TikTok.')
    request = urllib.request.Request('https://www.tiktok.com/@'+name,
        headers={'User-Agent':'Mozilla/5.0', 'Accept-Language':'en-US,en;q=0.9'})
    try:
        with urllib.request.urlopen(request, timeout=8) as response:
            raw = response.read(2_000_001)
        if len(raw)>2_000_000: raise ValueError()
        text=raw.decode('utf-8')
        match=re.search(r'<script[^>]+id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>',text,re.S)
        if not match: raise ValueError()
        user=json.loads(match.group(1))['__DEFAULT_SCOPE__']['webapp.user-detail']['userInfo']['user']
        uid=str(user['id']); handle=user['uniqueId']; bio=user['signature']
        if not UID.fullmatch(uid) or not isinstance(bio,str) or not isinstance(handle,str): raise ValueError()
        if handle.lower()!=name.lower(): raise ValueError()
        return uid, handle, bio
    except Exception:
        # An unavailable/challenged profile must never be interpreted as an empty bio.
        raise LinkError('TikTok пока не дал прочитать профиль. Попробуйте позже; покупки сохранены.',503) from None


def contains_code(bio, code):
    return re.search(r'(?<![A-Za-z0-9_-])'+re.escape(code)+r'(?![A-Za-z0-9_-])',bio) is not None


class Identity:
    def __init__(self, database):
        self.database=database
        self.audit_started=False
        self.audit_lock=threading.Lock()

    def prepare(self):
        with self.database() as db:
            db.executescript('''
            CREATE TABLE IF NOT EXISTS admin_targets(
              token_hash TEXT PRIMARY KEY, actor TEXT NOT NULL, actor_code TEXT NOT NULL,
              target TEXT NOT NULL, name TEXT NOT NULL, created INTEGER NOT NULL, expires INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS admin_audit(
              id INTEGER PRIMARY KEY AUTOINCREMENT, actor TEXT NOT NULL, target TEXT NOT NULL,
              name TEXT NOT NULL, badge TEXT NOT NULL, action TEXT NOT NULL, created INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS link_sessions(
              code TEXT PRIMARY KEY, uid TEXT NOT NULL, name TEXT NOT NULL,
              secret_hash TEXT NOT NULL, created INTEGER NOT NULL,
              verified INTEGER NOT NULL DEFAULT 0, last_attempt INTEGER NOT NULL DEFAULT 0);
            CREATE INDEX IF NOT EXISTS link_sessions_uid ON link_sessions(uid,created);
            CREATE TABLE IF NOT EXISTS profile_styles(uid TEXT PRIMARY KEY, colours TEXT NOT NULL, updated INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS badge_preferences(uid TEXT PRIMARY KEY, ordering TEXT NOT NULL, hidden TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS identities(
              uid TEXT PRIMARY KEY, code TEXT UNIQUE NOT NULL, name TEXT NOT NULL,
              bio_present INTEGER NOT NULL, checked INTEGER NOT NULL, next_check INTEGER NOT NULL,
              telegram INTEGER UNIQUE, pending_tg INTEGER, pending_name TEXT,
              pending_nonce TEXT, pending_until INTEGER NOT NULL DEFAULT 0);
            ''')

    def auth(self,db,uid,code,holder):
        if not isinstance(holder,str) or not 32<=len(holder)<=128: raise LinkError('Откройте подтверждение в приложении.',403)
        row=db.execute('SELECT * FROM link_sessions WHERE code=? AND uid=?',(code,uid)).fetchone()
        if not row or not hmac.compare_digest(row['secret_hash'],hashlib.sha256(holder.encode()).hexdigest()):
            raise LinkError('Откройте подтверждение в приложении.',403)
        if not row['verified'] and row['created']<time.time()-86400:
            raise LinkError('Код ещё не подтверждён и устарел. Создайте новый в приложении.',410)
        return row

    def create(self,uid,name,code='',holder=''):
        if not isinstance(uid,str) or not UID.fullmatch(uid): raise LinkError('Войдите в аккаунт TikTok.')
        name=name.lstrip('@').strip() if isinstance(name,str) else ''
        if not NAME.fullmatch(name): raise LinkError('Укажите свой ник TikTok.')
        now=int(time.time())
        with self.database() as db:
            if code and holder:
                try:
                    self.auth(db,uid,code,holder)
                    return {'code':code,'holder':holder}
                except LinkError as error:
                    if error.status!=410: raise
            count=db.execute('SELECT count(*) FROM link_sessions WHERE uid=? AND created>?',(uid,now-3600)).fetchone()[0]
            if count>=5: raise LinkError('Новые коды запрашиваются слишком часто. Попробуйте через час.',429)
            code='blaxy-'+secrets.token_hex(8); holder=secrets.token_urlsafe(32)
            db.execute('INSERT INTO link_sessions(code,uid,name,secret_hash,created) VALUES(?,?,?,?,?)',
                (code,uid,name,hashlib.sha256(holder.encode()).hexdigest(),now))
            db.execute('DELETE FROM link_sessions WHERE verified=0 AND created<?',(now-86400,))
        return {'code':code,'holder':holder}

    def check(self,uid,code,holder,name):
        now=int(time.time())
        with self.database() as db:
            session=self.auth(db,uid,code,holder)
            if now-session['last_attempt']<15: raise LinkError('Подождите 15 секунд перед повторной проверкой.',429)
            name=(name or session['name']).lstrip('@').strip()
            if not NAME.fullmatch(name): raise LinkError('Укажите свой ник TikTok.')
            db.execute('UPDATE link_sessions SET last_attempt=? WHERE code=?',(now,code))
        found,handle,bio=read_profile(name)
        if found!=uid: raise LinkError('Этот ник относится к другому аккаунту TikTok.')
        present=contains_code(bio,code)
        with self.database() as db:
            self.auth(db,uid,code,holder)
            old=db.execute('SELECT * FROM identities WHERE uid=?',(uid,)).fetchone()
            if not present:
                if old and old['code']==code:
                    db.execute('UPDATE identities SET bio_present=0,checked=?,next_check=? WHERE uid=?',(now,now+600,uid))
                raise_after=True
            else:
                raise_after=False
                if old and old['code']!=code:
                    # A new proof of bio control invalidates previous app secrets and Telegram binding.
                    db.execute('DELETE FROM link_sessions WHERE uid=? AND code<>?',(uid,code))
                    db.execute('DELETE FROM identities WHERE uid=?',(uid,))
                db.execute('INSERT INTO identities(uid,code,name,bio_present,checked,next_check) VALUES(?,?,?,1,?,?) '
                    'ON CONFLICT(uid) DO UPDATE SET name=excluded.name,bio_present=1,checked=excluded.checked,next_check=excluded.next_check',
                    (uid,code,handle,now,now+600))
                db.execute('UPDATE link_sessions SET verified=1,name=? WHERE code=?',(handle,code))
        if raise_after: raise LinkError('Код пока не найден в описании профиля. Оставьте его рядом со своим текстом.')
        return self.status(uid,code,holder)

    def status(self,uid,code,holder):
        with self.database() as db:
            self.auth(db,uid,code,holder)
            row=db.execute('SELECT * FROM identities WHERE uid=? AND code=?',(uid,code)).fetchone()
        result={'code':code,'verified':bool(row),'active':bool(row and row['bio_present'] and row['checked']>time.time()-86400),
            'linked':bool(row and row['telegram']),'notice':NOTICE}
        if row and row['pending_until']>time.time():
            result['pending']={'telegram_id':str(row['pending_tg']),'name':row['pending_name'],'nonce':row['pending_nonce']}
        return result

    # A developer badge is cosmetic. Only this verified, privately authenticated owner can grant.
    OWNER_UID = '7662448315568931873'
    OWNER_TELEGRAM = 6995362811
    ADMIN_BADGES = ('supporter','ambassador','developer','helper')

    def admin_auth(self,db,uid,code,holder):
        session=self.auth(db,uid,code,holder)
        row=db.execute('SELECT * FROM identities WHERE uid=? AND code=?',(uid,code)).fetchone()
        if (uid!=self.OWNER_UID or not session['verified'] or not row
                or row['telegram']!=self.OWNER_TELEGRAM or not row['bio_present']
                or row['checked']<time.time()-86400):
            raise LinkError('Управление доступно только владельцу blaxyT. Подтвердите свой профиль и Telegram.',403)
        return row

    def admin_status(self,uid,code,holder):
        with self.database() as db:
            self.admin_auth(db,uid,code,holder)
        return {'ok':True,'owner':True}

    def admin_resolve(self,uid,code,holder,name):
        name=name.strip().lstrip('@') if isinstance(name,str) else ''
        if not NAME.fullmatch(name): raise LinkError('Введите ник TikTok, например androidtexhhh.')
        now=int(time.time())
        with self.database() as db:
            self.admin_auth(db,uid,code,holder)
            count=db.execute('SELECT count(*) FROM admin_targets WHERE actor=? AND created>?',(uid,now-60)).fetchone()[0]
            if count>=10: raise LinkError('Слишком много запросов. Подождите минуту.',429)
        target,handle,_=read_profile(name)
        token=secrets.token_urlsafe(32)
        with self.database() as db:
            self.admin_auth(db,uid,code,holder)
            db.execute('DELETE FROM admin_targets WHERE expires<?',(now,))
            db.execute('INSERT INTO admin_targets VALUES(?,?,?,?,?,?,?)',
                (hashlib.sha256(token.encode()).hexdigest(),uid,code,target,handle,now,now+600))
            issued=[r['badge'] for r in db.execute('SELECT badge FROM grants WHERE uid=? AND revoked=0 AND (expires=0 OR expires>?)',(target,now))]
            visible=self.visible(db,target)
        return {'ok':True,'target_uid':target,'name':handle,'token':token,'manual_badges':issued,'active':visible}

    def admin_grant(self,uid,code,holder,token,badge,action):
        if badge not in self.ADMIN_BADGES or action not in ('grant','revoke'):
            raise LinkError('Выберите значок и действие.')
        if not isinstance(token,str) or not 32<=len(token)<=128: raise LinkError('Найдите профиль заново.')
        now=int(time.time())
        with self.database() as db:
            db.execute('BEGIN IMMEDIATE')
            self.admin_auth(db,uid,code,holder)
            digest=hashlib.sha256(token.encode()).hexdigest()
            target=db.execute('SELECT * FROM admin_targets WHERE token_hash=? AND actor=? AND actor_code=?',
                (digest,uid,code)).fetchone()
            if not target or target['expires']<now: raise LinkError('Поиск устарел или действие уже выполнено. Найдите профиль заново.',409)
            db.execute('INSERT INTO grants(uid,badge,expires,revoked,updated) VALUES(?,?,0,?,?) '
                'ON CONFLICT(uid,badge) DO UPDATE SET expires=0,revoked=excluded.revoked,updated=excluded.updated',
                (target['target'],badge,int(action=='revoke'),now))
            db.execute('INSERT INTO admin_audit(actor,target,name,badge,action,created) VALUES(?,?,?,?,?,?)',
                (uid,target['target'],target['name'],badge,action,now))
            db.execute('DELETE FROM admin_targets WHERE token_hash=?',(digest,))
            active=self.visible(db,target['target'])
        return {'ok':True,'name':target['name'],'badge':badge,'action':action,'active':active,
            'notice':('Значок выдан. Он появится после обновления данных в blaxyT.' if active else
                'Значок выдан. Получателю нужно подтвердить профиль blaxyT, чтобы он появился.')
                if action=='grant' else 'Ручная выдача снята. Оплаченный значок поддержки и история покупок сохраняются.'}

    def request_telegram(self,code,telegram,name):
        if not CODE.fullmatch(code): raise LinkError('Отправьте свой ID blaxy-… из настроек приложения.')
        now=int(time.time())
        with self.database() as db:
            row=db.execute('SELECT * FROM identities WHERE code=?',(code,)).fetchone()
            if not row or not row['bio_present']: raise LinkError('Сначала подтвердите этот код в приложении blaxyT.')
            if row['telegram']==telegram: return 'Этот Telegram уже привязан. Покупка значка: /buy.'
            already=db.execute('SELECT 1 FROM identities WHERE telegram=?',(telegram,)).fetchone()
            if already: raise LinkError('Этот Telegram уже связан с другим ID blaxyT. Для смены привязки обратитесь в /paysupport.')
            if row['telegram']: raise LinkError('Этот ID уже привязан. Для смены привязки обратитесь в /paysupport.')
            if row['pending_until']>now and row['pending_tg']!=telegram:
                raise LinkError('В приложении уже ожидается запрос. Его можно отклонить или дождаться истечения срока.')
            db.execute('UPDATE identities SET pending_tg=?,pending_name=?,pending_nonce=?,pending_until=? WHERE code=?',
                (telegram,name[:100],secrets.token_urlsafe(24),now+600,code))
        return 'Запрос отправлен. Откройте подтверждение аккаунта в blaxyT, нажмите «Обновить статус» и подтвердите свой Telegram.\n'+NOTICE

    def approve(self,uid,code,holder,nonce,accept=True):
        now=int(time.time())
        with self.database() as db:
            self.auth(db,uid,code,holder)
            row=db.execute('SELECT * FROM identities WHERE uid=? AND code=?',(uid,code)).fetchone()
            if not row or row['pending_until']<now or not isinstance(nonce,str) or not hmac.compare_digest(row['pending_nonce'] or '',nonce):
                raise LinkError('Запрос устарел. Отправьте код боту заново.')
            if accept:
                if not row['bio_present'] or row['checked']<now-86400: raise LinkError('Сначала ещё раз проверьте код в профиле.')
                conflict=db.execute('SELECT 1 FROM identities WHERE telegram=? AND uid<>?',(row['pending_tg'],uid)).fetchone()
                if conflict: raise LinkError('Этот Telegram уже привязан к другому ID.')
                db.execute('UPDATE identities SET telegram=? WHERE uid=?',(row['pending_tg'],uid))
            db.execute('UPDATE identities SET pending_tg=NULL,pending_name=NULL,pending_nonce=NULL,pending_until=0 WHERE uid=?',(uid,))
        return self.status(uid,code,holder)

    def by_telegram(self,telegram):
        with self.database() as db:
            row=db.execute('SELECT * FROM identities WHERE telegram=?',(telegram,)).fetchone()
            return dict(row) if row else None

    def gradient(self,uid,code,holder,colours):
        if not isinstance(colours,list) or (len(colours)!=0 and not 2<=len(colours)<=5):
            raise LinkError('Выберите от двух до пяти цветов.')
        if not all(isinstance(c,str) and re.fullmatch(r'[0-9a-fA-F]{6}',c) for c in colours):
            raise LinkError('Некорректный цвет.')
        now=int(time.time())
        with self.database() as db:
            self.auth(db,uid,code,holder)
            if not self.visible(db,uid): raise LinkError('Сначала подтвердите код в описании профиля.',403)
            previous=db.execute('SELECT updated FROM profile_styles WHERE uid=?',(uid,)).fetchone()
            if previous and previous['updated']>now-60: raise LinkError('Градиент можно менять раз в минуту.',429)
            db.execute('INSERT INTO profile_styles(uid,colours,updated) VALUES(?,?,?) '
                'ON CONFLICT(uid) DO UPDATE SET colours=excluded.colours,updated=excluded.updated',
                (uid,json.dumps([c.upper() for c in colours]),now))
        return {'ok':True}

    def badges(self,uid,code,holder,order,hidden):
        for values in (order,hidden):
            if not isinstance(values,list) or len(values)>68 or any(not isinstance(v,str) or (v not in ('developer','supporter','ambassador','helper') and not re.fullmatch(r'import:(margyt|you|tiktokmi):[a-f0-9]{24}',v)) for v in values) or len(set(values))!=len(values):
                raise LinkError('Некорректные настройки значков.')
        with self.database() as db:
            session=self.auth(db,uid,code,holder)
            identity=db.execute('SELECT code FROM identities WHERE uid=?',(uid,)).fetchone()
            if not session['verified'] or not identity or identity['code']!=code or not self.visible(db,uid):
                raise LinkError('Подтвердите код в описании профиля.',403)
            db.execute('INSERT INTO badge_preferences(uid,ordering,hidden) VALUES(?,?,?) '
                       'ON CONFLICT(uid) DO UPDATE SET ordering=excluded.ordering,hidden=excluded.hidden',
                       (uid,json.dumps(order),json.dumps(hidden)))
        return {'ok':True}

    def audit_one(self,row):
        now=int(time.time())
        # Reserve the next attempt first, including failures, to avoid hammering TikTok.
        with self.database() as db:
            db.execute('UPDATE identities SET next_check=? WHERE uid=? AND code=?',(now+600,row['uid'],row['code']))
        found,handle,bio=read_profile(row['name'])
        if found!=row['uid']: raise LinkError('Ник изменился. Обновите профиль в приложении.',503)
        with self.database() as db:
            db.execute('UPDATE identities SET bio_present=?,checked=? WHERE uid=? AND code=?',
                (int(contains_code(bio,row['code'])),now,row['uid'],row['code']))
        return contains_code(bio,row['code'])

    def audit_loop(self):
        while True:
            try:
                with self.database() as db:
                    rows=db.execute('SELECT * FROM identities WHERE next_check<=? ORDER BY next_check LIMIT 10',(int(time.time()),)).fetchall()
                for row in rows:
                    try: self.audit_one(row)
                    except LinkError: pass
            except Exception: pass
            time.sleep(30)

    def start(self):
        with self.audit_lock:
            if self.audit_started: return
            self.audit_started=True
            threading.Thread(target=self.audit_loop,daemon=True,name='bio-audit').start()

    @staticmethod
    def visible(db,uid,manual=False):
        row=db.execute('SELECT bio_present,checked FROM identities WHERE uid=?',(uid,)).fetchone()
        return manual if not row else bool(row['bio_present'] and row['checked']>time.time()-86400)
