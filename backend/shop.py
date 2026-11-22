"""BlaxyT Stars shop and read-only badge feed. No upstream bot/services required.

Python 3.11+, standard library. Sales are explicitly disabled until configured.
The bot issues only supporter badges, identified by stable TikTok numeric UID.
"""
import contextlib
import hashlib
import http.client
import json
import logging
import os
import re
import secrets
import sqlite3
import socket
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from identity import Identity, LinkError, NOTICE
from badge_import import BadgeImports
from automatic_badges import AutomaticBadges
from avatar_frames import AvatarFrames
from app_updates import AppUpdates
from voice import Voices, MAX_BODY as VOICE_BODY

TOKEN = os.environ.get('TELEGRAM_BOT_TOKEN', '')
DB = Path(os.environ.get('SHOP_DB', 'shop.sqlite3'))
PRICE = int(os.environ.get('BADGE_PRICE_STARS') or 0)
DAYS = int(os.environ.get('BADGE_DURATION_DAYS') or 0)
SUPPORT = os.environ.get('SUPPORT_CONTACT', '')
PUBLIC = os.environ.get('PUBLIC_BADGES_URL', '')
LIVE = os.environ.get('SALES_ENABLED', '').lower() == 'true'
LAST_POLL = 0
LAST_ERROR = ''
UID = re.compile(r'[0-9]{6,24}\Z')
LOG = logging.getLogger('blaxyt-shop')


@contextlib.contextmanager
def database():
    db = sqlite3.connect(DB, timeout=15)
    db.row_factory = sqlite3.Row
    try:
        with db:
            yield db
    finally:
        db.close()

LINKS = Identity(database)
IMPORTS = BadgeImports(database, LINKS, DB.parent / 'badge-images')
AUTOMATIC = AutomaticBadges(database, IMPORTS)
FRAMES = AvatarFrames(database, LINKS, IMPORTS)
AUTOMATIC.frames = FRAMES
VOICES = Voices(database, LINKS, DB.parent / 'voices.sqlite3')
UPDATES = AppUpdates(DB.parent / 'updates')


def prepare():
    DB.parent.mkdir(parents=True, exist_ok=True)
    FRAMES.prepare()
    with database() as db:
        db.executescript('''
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS orders (
          id TEXT PRIMARY KEY, buyer INTEGER NOT NULL, uid TEXT NOT NULL,
          price INTEGER NOT NULL, days INTEGER NOT NULL, created INTEGER NOT NULL,
          status TEXT NOT NULL DEFAULT 'pending');
        CREATE TABLE IF NOT EXISTS payments (
          charge TEXT PRIMARY KEY, order_id TEXT NOT NULL UNIQUE,
          buyer INTEGER NOT NULL, uid TEXT NOT NULL, expires INTEGER NOT NULL,
          refunded INTEGER NOT NULL DEFAULT 0);
        CREATE TABLE IF NOT EXISTS state (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS refunds(charge TEXT PRIMARY KEY);
        CREATE TABLE IF NOT EXISTS grants (
          uid TEXT NOT NULL, badge TEXT NOT NULL CHECK(badge IN ('developer','supporter','ambassador','helper')),
          expires INTEGER NOT NULL DEFAULT 0, revoked INTEGER NOT NULL DEFAULT 0,
          updated INTEGER NOT NULL, PRIMARY KEY(uid,badge));
        ''')
        # Expand the operator-only grants catalogue without changing existing ownership.
        grants_sql=db.execute("SELECT sql FROM sqlite_master WHERE name='grants'").fetchone()[0]
        if "'helper'" not in grants_sql:
            db.execute("BEGIN IMMEDIATE")
            db.execute("ALTER TABLE grants RENAME TO grants_before_helper")
            db.execute("""CREATE TABLE grants(
              uid TEXT NOT NULL, badge TEXT NOT NULL CHECK(badge IN ('developer','supporter','ambassador','helper')),
              expires INTEGER NOT NULL DEFAULT 0, revoked INTEGER NOT NULL DEFAULT 0,
              updated INTEGER NOT NULL, PRIMARY KEY(uid,badge))""")
            db.execute("INSERT INTO grants SELECT uid,badge,expires,revoked,updated FROM grants_before_helper")
            db.execute("DROP TABLE grants_before_helper")
        if 'checkout_id' not in [r[1] for r in db.execute('PRAGMA table_info(orders)')]:
            db.execute('ALTER TABLE orders ADD COLUMN checkout_id TEXT')
    LINKS.prepare()
    IMPORTS.prepare()
    AUTOMATIC.prepare()
    VOICES.prepare()
    if os.name != 'nt':
        DB.chmod(0o600)


class TelegramError(RuntimeError):
    def __init__(self,method,code,description):
        self.code=code
        safe=str(description).replace(TOKEN,'[redacted]') if TOKEN else str(description)
        super().__init__(method+': Telegram '+str(code)+' '+safe[:180])


class TelegramIPv4(http.client.HTTPSConnection):
    """Only this bot's transport uses IPv4; system DNS, routes and other services are unchanged."""
    def connect(self):
        failure=None
        for _,kind,proto,_,address in socket.getaddrinfo(self.host,self.port,socket.AF_INET,socket.SOCK_STREAM):
            raw=socket.socket(socket.AF_INET,kind,proto);raw.settimeout(self.timeout)
            try:
                if self.source_address:raw.bind(self.source_address)
                raw.connect(address)
                # Keep normal certificate/hostname verification and SNI.
                self.sock=self._context.wrap_socket(raw,server_hostname=self.host)
                return
            except OSError as error:
                failure=error;raw.close()
        raise failure or OSError('No IPv4 address for Telegram')


def api(method, **payload):
    source=os.environ.get('TELEGRAM_SOURCE_IP','')
    link=TelegramIPv4('api.telegram.org',timeout=2 if method=='answerPreCheckoutQuery' else 35 if method=='getUpdates' else 5,
                      source_address=(source,0) if source else None)
    try:
        link.connect()
        link.sock.settimeout(35 if method=='getUpdates' else 6)
        link.request('POST','/bot'+TOKEN+'/'+method,body=json.dumps(payload).encode(),
                     headers={'Content-Type':'application/json'})
        response=link.getresponse();value=json.load(response)
        if response.status>=400 or not value.get('ok'):
            raise TelegramError(method,value.get('error_code',response.status),value.get('description','Request rejected'))
        return value['result']
    except (OSError,http.client.HTTPException) as error:
        raise RuntimeError(method+': Telegram connection '+type(error).__name__) from None
    finally:link.close()


def say(chat, text, **extra):
    try:return api('sendMessage', chat_id=chat, text=text, **extra)
    except TelegramError as error:
        if error.code==403:
            LOG.info('Recipient blocked delivery; stored payments are retained')
            return None
        raise


def active(db, uid):
    return db.execute('SELECT 1 FROM payments WHERE uid=? AND refunded=0 '
                      'AND (expires=0 OR expires>?) LIMIT 1', (uid, int(time.time()))).fetchone()


def invoice(chat, buyer, uid):
    if not LIVE:
        say(chat, 'Магазин готовится к запуску. Оплата пока выключена.')
        return
    linked = LINKS.by_telegram(buyer)
    if not linked or linked['uid'] != uid:
        raise LinkError('Сначала привяжите ID blaxyT через /link.')
    if not linked['bio_present']:
        raise LinkError('Код убран из описания TikTok. Верните его и проверьте в приложении.')
    if linked['checked']<time.time()-600:
        raise LinkError('Нажмите «Проверить код в профиле» в blaxyT, затем вернитесь к покупке.')
    with database() as db:
        if active(db, uid):
            say(chat, 'На этом ID blaxyT значок уже активен. Повторная покупка не нужна.')
            return
        key = secrets.token_urlsafe(24)
        db.execute('INSERT INTO orders(id,buyer,uid,price,days,created) VALUES(?,?,?,?,?,?)',
                   (key, buyer, uid, PRICE, DAYS, int(time.time())))
    period = 'без ограничения срока' if DAYS == 0 else f'на {DAYS} дней'
    api('sendInvoice', chat_id=chat, title='blaxyT · Заряд',
        description=f'Заряд {period}. ID: {linked["code"]}. '
                    'Виден в blaxyT при сохранении кода в описании TikTok. Не официальная галочка.',
        payload=key, provider_token='', currency='XTR',
        prices=[{'label': 'Значок Заряд', 'amount': PRICE}], start_parameter='buy')


def checkout(query):
    linked = LINKS.by_telegram(query['from']['id'])
    with database() as db:
        db.execute('BEGIN IMMEDIATE')
        order = db.execute('SELECT * FROM orders WHERE id=?', (query['invoice_payload'],)).fetchone()
        replay = bool(order and order['status']=='checkout' and order['checkout_id']==query['id'])
        valid = bool(LIVE and order and (order['status'] == 'pending' or replay)
                     and linked and linked['uid']==order['uid'] and linked['bio_present']
                     and linked['checked'] > time.time()-600
                     and order['buyer'] == query['from']['id']
                     and order['price'] == PRICE and order['price'] == query['total_amount'] and query['currency'] == 'XTR'
                     and time.time() - order['created'] < 1800 and not active(db, order['uid'])
                     and not db.execute("SELECT 1 FROM orders WHERE uid=? AND id<>? AND status='checkout' AND created>? LIMIT 1",
                                        (order['uid'],order['id'],int(time.time())-1800)).fetchone())
        # Reserve before confirming: a forwarded invoice cannot be purchased twice.
        if valid:
            db.execute("UPDATE orders SET status='checkout',checkout_id=? WHERE id=?", (query['id'],order['id']))
    args = {'pre_checkout_query_id': query['id'], 'ok': valid}
    if not valid:
        args['error_message'] = 'Заказ недоступен или устарел. Создайте новый через /buy.'
    try:api('answerPreCheckoutQuery', **args)
    except TelegramError as error:
        if error.code!=400:raise
        # Telegram has expired this query. Do not block later users behind it.
        with database() as db:
            db.execute("UPDATE orders SET status='expired' WHERE checkout_id=? AND status='checkout'",(query['id'],))
        LOG.warning('Expired checkout query; a new invoice is required')


def paid(message):
    payment = message['successful_payment']
    buyer = message['from']['id']
    charge = payment['telegram_payment_charge_id']
    with database() as db:
        db.execute('BEGIN IMMEDIATE')
        order = db.execute('SELECT * FROM orders WHERE id=?', (payment['invoice_payload'],)).fetchone()
        if not order or order['buyer'] != buyer or order['price'] != payment['total_amount'] or payment['currency'] != 'XTR':
            # A payment without a matching order must never silently grant a badge.
            raise RuntimeError('Payment requires operator review')
        existing = db.execute('SELECT * FROM payments WHERE charge=?', (charge,)).fetchone()
        if existing:
            if existing['order_id'] != order['id'] or existing['buyer'] != buyer:
                raise RuntimeError('Payment identity mismatch')
        else:
            expires = 0 if order['days'] == 0 else int(time.time()) + order['days'] * 86400
            was_refunded=bool(db.execute('SELECT 1 FROM refunds WHERE charge=?',(charge,)).fetchone())
            db.execute('INSERT INTO payments(charge,order_id,buyer,uid,expires,refunded) VALUES(?,?,?,?,?,?)',
                       (charge, order['id'], buyer, order['uid'], expires,int(was_refunded)))
            db.execute("UPDATE orders SET status='paid' WHERE id=?", (order['id'],))
    linked=LINKS.by_telegram(buyer)
    public_code=linked['code'] if linked and linked['uid']==order['uid'] else 'связанный профиль'
    say(message['chat']['id'], 'Оплата учтена для ' + public_code +
        '. Вернитесь в blaxyT: значки обновятся при открытии приложения. '
        'Для помощи: /paysupport. Номер заказа: ' + order['id'])


def refunded(message):
    payment = message['refunded_payment']
    with database() as db:
        db.execute('INSERT OR IGNORE INTO refunds(charge) VALUES(?)',(payment['telegram_payment_charge_id'],))
        db.execute('UPDATE payments SET refunded=1 WHERE charge=?', (payment['telegram_payment_charge_id'],))


def handle(update):
    if 'pre_checkout_query' in update:
        checkout(update['pre_checkout_query']); return
    if 'callback_query' in update:
        query = update['callback_query']
        try:api('answerCallbackQuery', callback_query_id=query['id'])
        except TelegramError as error:
            if error.code!=400:raise
        # Confirmation is bound to the same user's private chat.
        message = query.get('message', {})
        chat = message.get('chat', {})
        data = query.get('data', '')
        if chat.get('type') == 'private' and chat.get('id') == query['from']['id'] and data.startswith('buy:'):
            code = data[4:]
            linked=LINKS.by_telegram(query['from']['id'])
            if linked and linked['code']==code:
                try: invoice(chat['id'],query['from']['id'],linked['uid'])
                except LinkError as error: say(chat['id'],str(error))
        return
    message = update.get('message', {})
    if message.get('chat', {}).get('type') != 'private': return
    if 'successful_payment' in message: paid(message); return
    if 'refunded_payment' in message: refunded(message); return
    chat = message['chat']['id']
    parts = message.get('text', '').strip().split()
    command = parts[0].split('@')[0] if parts else ''
    if command == '/myid':
        say(chat,'Ваш Telegram ID: '+str(message['from']['id'])+'\nЭтот номер можно передать для создания вашего набора эмодзи. Это не пароль и не код входа.')
    elif command == '/link' or command.startswith('blaxy-') or (command=='/start' and len(parts)==2 and parts[1].startswith('blaxy-')):
        code=parts[1] if command in ('/link','/start') and len(parts)==2 else (parts[0] if command.startswith('blaxy-') else '')
        sender=message.get('from',{})
        name=('@'+sender['username']) if sender.get('username') else sender.get('first_name','Telegram')
        try: say(chat,LINKS.request_telegram(code,chat,name))
        except LinkError as error: say(chat,str(error))
    elif command == '/status':
        linked=LINKS.by_telegram(chat)
        if not linked: say(chat,'Telegram ещё не привязан. Отправьте код blaxy-… из настроек приложения.')
        else: say(chat,'Ваш ID: '+linked['code']+'\nTelegram привязан.\n'+
                      ('Код присутствует в профиле.' if linked['bio_present'] else 'Верните код в описание TikTok и нажмите проверку в приложении.')+'\n'+NOTICE)
    elif command == '/buy':
        if not LIVE:
            say(chat, 'Оплата ещё не включена. Сначала подключаем выдачу значков к приложению.')
        else:
            linked=LINKS.by_telegram(chat)
            if not linked:
                say(chat,'Сначала отправьте свой код blaxy-… и подтвердите Telegram в приложении.'); return
            period = 'без срока' if DAYS == 0 else f'{DAYS} дней'
            say(chat, f'Заряд для {linked["code"]}\nЦена: {PRICE} Stars · {period}\n'
                'Значок виден только в blaxyT.\n'+NOTICE+'\nУсловия: /terms. '
                'Нажимая кнопку, вы принимаете условия покупки.',
                reply_markup={'inline_keyboard': [[{'text': f'Принимаю условия · {PRICE} Stars', 'callback_data': 'buy:' + linked['code']}]]})
    elif command in ('/paysupport', '/support'):
        say(chat, 'Вопросы по оплате и возврату: ' + (SUPPORT or 'магазин ещё настраивается') +
            '. Укажите номер заказа; пароль TikTok не нужен.')
    elif command == '/terms':
        say(chat, f'{PRICE} Stars — разовая покупка значка «Заряд» для связанного профиля blaxyT без срока. '
            'Это не официальная галочка TikTok. Срок и цена указаны в счёте. '
            'Разработческий значок не продаётся. '+NOTICE+' Вопросы по выдаче/возврату: /paysupport. '
            'Telegram не отвечает за покупки в этом боте.')
    elif command == '/privacy':
        say(chat, 'Храним Telegram ID, ID blaxyT, TikTok ID и ник для проверки кода в публичном описании, сумму, номер заказа и '
            'идентификатор платежа для выдачи и возврата. Публичная выдача содержит только TikTok ID, '
            'тип и срок значка. Telegram ID и платежи не публикуются. '
            'При добавлении голосового сохраняем аудио, TikTok ID автора, ID ролика и время записи. '
            'Голосовые доступны подтверждённым пользователям BlaxyT по коду из комментария; это не личная переписка. '
            'Удалить своё аудио можно в приложении: «Мои голосовые». Текст комментария удаляется отдельно в TikTok. '
            'Пароль TikTok не запрашиваем. Связь: /support.')
    elif command == '/orders':
        with database() as db:
            rows = db.execute('SELECT order_id,uid,refunded FROM payments WHERE buyer=? LIMIT 20', (chat,)).fetchall()
        say(chat, '\n'.join(f"{r['order_id']} — Заряд" + (' · возврат' if r['refunded'] else '') for r in rows) or 'Покупок пока нет.')
    else:
        if not LIVE:
            say(chat, 'blaxyT · Заряд\nОтправьте ID blaxy-… из приложения и подтвердите Telegram там же. '
                'Оплата пока выключена.\n/status — привязка\n/orders — покупки\n/privacy — данные\n/paysupport — помощь')
        else:
            say(chat, f'blaxyT · Заряд\nОтправьте свой ID blaxy-… для привязки.\n/status — привязка\n/buy — Заряд за {PRICE} Stars\n/orders — покупки\n/terms — условия\n/privacy — данные\n/paysupport — помощь')


class LimitedHTTP(ThreadingHTTPServer):
    daemon_threads=True
    slots=threading.BoundedSemaphore(8)
    def process_request(self,request,address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request);return
        try:super().process_request(request,address)
        except Exception:self.slots.release();raise
    def process_request_thread(self,request,address):
        try:super().process_request_thread(request,address)
        finally:self.slots.release()


class Feed(BaseHTTPRequestHandler):
    def do_POST(self):
        voice = self.path in ('/v1/voice/upload','/v1/voice/read','/v1/voice/list','/v1/voice/delete')
        if not voice and self.path not in ('/v1/link/frame','/v1/link/create','/v1/link/check','/v1/link/status','/v1/link/approve','/v1/link/reject','/v1/link/gradient','/v1/link/badges','/v1/link/import-badges','/v1/link/admin-status','/v1/link/admin-resolve','/v1/link/admin-grant'):
            self.send_error(404); return
        try:
            self.connection.settimeout(10)
            length=int(self.headers.get('Content-Length','0'))
            if self.headers.get('Transfer-Encoding'): raise LinkError('Некорректный запрос.',400)
            if not 0<length<=(VOICE_BODY if self.path=='/v1/voice/upload' else 8192): raise LinkError('Некорректный запрос.',413)
            obj=json.loads(self.rfile.read(length))
            if not isinstance(obj,dict): raise LinkError('Некорректный запрос.')
            uid=obj.get('uid','');code=obj.get('code','');holder=obj.get('holder','');name=obj.get('name','')
            if not all(isinstance(x,str) for x in (uid,code,holder,name)): raise LinkError('Некорректный запрос.')
            action=self.path.rsplit('/',1)[1]
            if voice: body=VOICES.handle(action,obj)
            elif action=='create': body=LINKS.create(uid,name,code,holder)
            elif action=='check': body=LINKS.check(uid,code,holder,name)
            elif action=='status': body=LINKS.status(uid,code,holder)
            elif action=='gradient': body=LINKS.gradient(uid,code,holder,obj.get('colours',[]))
            elif action=='frame': body=FRAMES.choose(uid,code,holder,obj.get('frame',''))
            elif action=='import-badges': body=IMPORTS.transfer(uid,code,holder,obj.get('source',''))
            elif action=='admin-status': body=LINKS.admin_status(uid,code,holder)
            elif action=='admin-resolve': body=LINKS.admin_resolve(uid,code,holder,obj.get('target_name',''))
            elif action=='admin-grant': body=LINKS.admin_grant(uid,code,holder,obj.get('token',''),obj.get('badge',''),obj.get('action',''))
            elif action=='badges': body=LINKS.badges(uid,code,holder,obj.get('order',[]),obj.get('hidden',[]))
            else: body=LINKS.approve(uid,code,holder,obj.get('nonce',''),action=='approve')
            status=200
        except LinkError as error: body={'error':str(error)};status=error.status
        except (ValueError,KeyError,TypeError): body={'error':'Некорректный запрос.'};status=400
        except Exception: body={'error':'Сервис временно недоступен.'};status=503
        raw=json.dumps(body,ensure_ascii=False).encode()
        self.send_response(status);self.send_header('Content-Type','application/json; charset=utf-8')
        self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(len(raw)))
        self.end_headers();self.wfile.write(raw)

    def do_GET(self):
        if UPDATES.handle(self):return
        if self.path.startswith('/v1/external-badges?'):
            try:
                import urllib.parse
                query=urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query)
                if set(query)!={'uids'} or len(query['uids'])!=1:raise ValueError('Invalid query')
                result=AUTOMATIC.request(query['uids'][0].split(','));status=200
            except ValueError:result={'error':'Invalid UID batch'};status=400
            except Exception:result={'error':'Temporarily unavailable'};status=503
            raw=json.dumps(result).encode();self.send_response(status)
            self.send_header('Content-Type','application/json');self.send_header('Cache-Control','no-store')
            self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw);return
        if self.path.startswith('/v1/import-icon/'):
            name=self.path[len('/v1/import-icon/'):]
            raw=IMPORTS.read_image(name)
            if raw is None:self.send_error(404);return
            self.send_response(200)
            self.send_header('Content-Type',{'png':'image/png','jpg':'image/jpeg','webp':'image/webp'}[name.rsplit('.',1)[1]])
            self.send_header('X-Content-Type-Options','nosniff')
            self.send_header('Cache-Control','public, max-age=31536000, immutable')
            self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw);return
        if self.path == '/health':
            body = json.dumps({'ok': bool(LAST_POLL and time.time()-LAST_POLL < 120 and not LAST_ERROR),
                               'processing_error':bool(LAST_ERROR),
                               'sales_enabled': LIVE}).encode()
            self.send_response(200 if LAST_POLL and time.time()-LAST_POLL < 120 and not LAST_ERROR else 503)
            self.send_header('Content-Type','application/json')
            self.send_header('Content-Length',str(len(body)))
            self.end_headers(); self.wfile.write(body); return
        if self.path != '/v1/badges':
            self.send_error(404); return
        now = int(time.time())
        with database() as db:
            rows = db.execute('SELECT uid,expires FROM payments WHERE refunded=0 AND (expires=0 OR expires>?)', (now,)).fetchall()
            manual = db.execute('SELECT uid,badge,expires FROM grants WHERE revoked=0 '
                                'AND (expires=0 OR expires>?)', (now,)).fetchall()
            rows=[r for r in rows if LINKS.visible(db,r['uid'])]
            manual=[r for r in manual if LINKS.visible(db,r['uid'],manual=True)]
            gradients={r['uid']:json.loads(r['colours']) for r in db.execute('SELECT uid,colours FROM profile_styles')
                       if LINKS.visible(db,r['uid']) and len(json.loads(r['colours']))>=2}
            settings={r['uid']:{'order':json.loads(r['ordering']),'hidden':json.loads(r['hidden'])}
                      for r in db.execute('SELECT * FROM badge_preferences')}
            external_catalog,external_badges=AUTOMATIC.feed(db)
            avatar_frames=FRAMES.feed(db)
        grants = {}
        for row in rows:
            uid, end = row['uid'], row['expires']
            if uid not in grants or end == 0 or (grants[uid] != 0 and end > grants[uid]): grants[uid] = end
        badges = {uid: {'supporter': end} for uid, end in grants.items()}
        for row in manual:
            current = badges.setdefault(row['uid'], {})
            previous = current.get(row['badge'])
            if previous is None or row['expires'] == 0 or (previous != 0 and row['expires'] > previous):
                current[row['badge']] = row['expires']
        # Keep schema 1 compatibility until connected clients migrate to badges.
        supporters = {uid: items['supporter'] for uid, items in badges.items() if 'supporter' in items}
        body = json.dumps({'schema': 1, 'supporters': supporters, 'badges': badges, 'gradients':gradients,'badge_settings':settings,
                          'external_catalog':external_catalog,'external_badges':external_badges,'external_mode':'automatic',**avatar_frames}, ensure_ascii=False,separators=(',', ':')).encode()
        tag = '"' + hashlib.sha256(body).hexdigest() + '"'
        self.send_response(200); self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body))); self.send_header('ETag', tag)
        self.send_header('Cache-Control', 'no-store'); self.end_headers(); self.wfile.write(body)
    def log_message(self, *args): pass


def run():
    global LAST_POLL,LAST_ERROR
    if not TOKEN: raise SystemExit('Set TELEGRAM_BOT_TOKEN in the server environment')
    if LIVE and (PRICE <= 0 or DAYS < 0 or not SUPPORT or not PUBLIC.startswith('https://')
                 or os.environ.get('BADGE_DURATION_DAYS', '') == ''):
        raise SystemExit('Sales need price, duration, support contact and public HTTPS URL')
    prepare()
    LINKS.start()
    AUTOMATIC.start()
    api('getMe')
    webhook = api('getWebhookInfo')
    if webhook.get('url'):
        raise SystemExit('Existing webhook detected; refusing to replace another bot deployment')
    LOG.info('Bot authenticated; sales enabled: %s', LIVE)
    server = LimitedHTTP(('127.0.0.1', int(os.environ.get('LISTEN_PORT', '8087'))), Feed)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    with database() as db:
        saved = db.execute("SELECT value FROM state WHERE key='offset'").fetchone()
    offset = int(saved[0]) if saved else 0
    while True:
        try:
            updates = api('getUpdates', offset=offset, timeout=25,
                          allowed_updates=['message', 'callback_query', 'pre_checkout_query'])
            LAST_POLL = time.time()
            for update in updates:
                handle(update)
                offset = update['update_id'] + 1
                with database() as db:
                    db.execute("INSERT OR REPLACE INTO state VALUES('offset',?)", (str(offset),))
            LAST_ERROR=''
        except Exception as error:
            # Do not advance past an unprocessed payment. Retrying is idempotent.
            LAST_ERROR=type(error).__name__
            LOG.error('Update postponed: %s',str(error) if isinstance(error,RuntimeError) else type(error).__name__)
            time.sleep(3)


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO)
    run()
