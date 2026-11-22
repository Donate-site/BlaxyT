"""Isolated payment/link regression checks: temporary SQLite, fake Telegram, no real Stars."""
import json
import tempfile
import time
import unittest
import threading
import urllib.request
import urllib.error
from pathlib import Path
from unittest.mock import patch
import identity
import shop


class Flow(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();shop.DB=Path(self.temp.name)/'test.sqlite3'
        shop.LIVE=True;shop.PRICE=15;shop.DAYS=0
        shop.LINKS=identity.Identity(shop.database);shop.prepare()
        self.calls=[]
        self.api=patch.object(shop,'api',side_effect=lambda method,**data:self.calls.append((method,data)) or {})
        self.api.start()
        self.uid='7662448315568931873';self.name='androidtexhhh';self.bio=''
        self.profile=patch.object(identity,'read_profile',side_effect=lambda name:(self.uid,self.name,self.bio))
        self.profile.start()
        self.keys=shop.LINKS.create(self.uid,self.name)
        self.code=self.keys['code'];self.holder=self.keys['holder']

    def tearDown(self):
        self.profile.stop();self.api.stop();self.temp.cleanup()

    def verify(self):
        self.bio='Мой текст 🙂 '+self.code+' ещё описание'
        return shop.LINKS.check(self.uid,self.code,self.holder,self.name)

    def link(self):
        self.verify();shop.LINKS.request_telegram(self.code,42,'@buyer')
        status=shop.LINKS.status(self.uid,self.code,self.holder)
        shop.LINKS.approve(self.uid,self.code,self.holder,status['pending']['nonce'])

    def order(self):
        shop.invoice(42,42,self.uid)
        data=[x[1] for x in self.calls if x[0]=='sendInvoice'][-1]
        self.assertEqual(data['currency'],'XTR');self.assertEqual(data['prices'][0]['amount'],15)
        return data['payload']

    def pay(self,key,charge='charge-1',amount=15):
        shop.paid({'from':{'id':42},'chat':{'id':42},'successful_payment':{
            'invoice_payload':key,'telegram_payment_charge_id':charge,'total_amount':amount,'currency':'XTR'}})

    def test_permanent_code_and_private_secret(self):
        self.assertRegex(self.code,r'^blaxy-[a-z0-9]{16}$')
        self.verify()
        self.assertEqual(shop.LINKS.status(self.uid,self.code,self.holder)['code'],self.code)
        self.assertEqual(shop.LINKS.create(self.uid,self.name,self.code,self.holder)['code'],self.code)
        with self.assertRaises(identity.LinkError):shop.LINKS.status(self.uid,self.code,'x'*43)

    def test_copied_code_cannot_authorize_telegram(self):
        self.verify();shop.LINKS.request_telegram(self.code,43,'@outsider')
        pending=shop.LINKS.status(self.uid,self.code,self.holder)['pending']
        with self.assertRaises(identity.LinkError):shop.LINKS.approve(self.uid,self.code,'x'*43,pending['nonce'])
        self.assertIsNone(shop.LINKS.by_telegram(43))
        shop.LINKS.approve(self.uid,self.code,self.holder,pending['nonce'],False)
        self.assertNotIn('pending',shop.LINKS.status(self.uid,self.code,self.holder))

    def test_account_id_mismatch(self):
        self.bio=self.code
        with patch.object(identity,'read_profile',return_value=('123456789','else',self.code)):
            with self.assertRaises(identity.LinkError):shop.LINKS.check(self.uid,self.code,self.holder,self.name)
        self.assertFalse(shop.LINKS.status(self.uid,self.code,self.holder)['verified'])

    def test_code_token_boundaries(self):
        self.assertTrue(identity.contains_code('hello ('+self.code+') world',self.code))
        self.assertFalse(identity.contains_code(self.code+'abc',self.code))

    def test_checkout_identity_and_replay(self):
        self.link();key=self.order()
        query={'id':'q1','invoice_payload':key,'from':{'id':43},'total_amount':15,'currency':'XTR'}
        shop.checkout(query);self.assertFalse(self.calls[-1][1]['ok'])
        query['from']['id']=42;shop.checkout(query);self.assertTrue(self.calls[-1][1]['ok'])
        shop.checkout(query);self.assertTrue(self.calls[-1][1]['ok'])
        query['id']='different';shop.checkout(query);self.assertFalse(self.calls[-1][1]['ok'])

    def test_previous_price_invoice_rejected_but_old_receipt_honored(self):
        self.link();key=self.order()
        with shop.database() as db:db.execute('UPDATE orders SET price=100 WHERE id=?',(key,))
        shop.checkout({'id':'old-price','invoice_payload':key,'from':{'id':42},'total_amount':100,'currency':'XTR'})
        self.assertFalse(self.calls[-1][1]['ok'])
        self.pay(key,amount=100)
        with shop.database() as db:self.assertTrue(shop.active(db,self.uid))

    def test_payment_idempotency_and_refund(self):
        self.link();key=self.order();self.pay(key);self.pay(key)
        with shop.database() as db:
            self.assertEqual(db.execute('SELECT count(*) FROM payments').fetchone()[0],1)
            self.assertTrue(shop.active(db,self.uid))
        shop.refunded({'refunded_payment':{'telegram_payment_charge_id':'charge-1'}})
        self.pay(key)
        with shop.database() as db:self.assertFalse(shop.active(db,self.uid))

    def test_wrong_payment_amount_does_not_grant(self):
        self.link();key=self.order()
        with self.assertRaises(RuntimeError):self.pay(key,amount=99)
        with shop.database() as db:self.assertFalse(shop.active(db,self.uid))

    def test_refund_before_payment_never_grants(self):
        self.link();key=self.order()
        shop.refunded({'refunded_payment':{'telegram_payment_charge_id':'charge-1'}});self.pay(key)
        with shop.database() as db:self.assertFalse(shop.active(db,self.uid))

    def test_removed_bio_hides_without_destroying_purchase(self):
        self.link();key=self.order();self.pay(key)
        self.bio='Только мой текст';shop.LINKS.audit_one(shop.LINKS.by_telegram(42))
        with shop.database() as db:
            self.assertFalse(shop.LINKS.visible(db,self.uid));self.assertTrue(shop.active(db,self.uid))
        self.bio='Вернул '+self.code;shop.LINKS.audit_one(shop.LINKS.by_telegram(42))
        with shop.database() as db:self.assertTrue(shop.LINKS.visible(db,self.uid))

    def test_tiktok_failure_does_not_erase_verification(self):
        self.link()
        with patch.object(identity,'read_profile',side_effect=identity.LinkError('Unavailable',503)):
            with self.assertRaises(identity.LinkError):shop.LINKS.audit_one(shop.LINKS.by_telegram(42))
        with shop.database() as db:self.assertTrue(shop.LINKS.visible(db,self.uid))

    def test_gradient_free_without_purchase(self):
        self.verify();shop.LINKS.gradient(self.uid,self.code,self.holder,['FF00FF','0011FF'])
        with shop.database() as db:
            self.assertFalse(shop.active(db,self.uid))
            self.assertEqual(json.loads(db.execute('SELECT colours FROM profile_styles').fetchone()[0]),['FF00FF','0011FF'])
        with self.assertRaises(identity.LinkError):shop.LINKS.gradient(self.uid,self.code,'x'*43,['FF00FF','0011FF'])

    def test_fresh_device_cannot_claim_without_new_bio(self):
        self.link();other=shop.LINKS.create(self.uid,self.name)
        with self.assertRaises(identity.LinkError):shop.LINKS.check(self.uid,other['code'],other['holder'],self.name)
        self.assertIsNotNone(shop.LINKS.by_telegram(42))

    def test_hide_and_order_do_not_grant_badges(self):
        self.verify();shop.LINKS.badges(self.uid,self.code,self.holder,['supporter','developer'],['developer'])
        with shop.database() as db:
            self.assertFalse(shop.active(db,self.uid))
            self.assertEqual(json.loads(db.execute('SELECT hidden FROM badge_preferences').fetchone()[0]),['developer'])
        with self.assertRaises(identity.LinkError):shop.LINKS.badges(self.uid,self.code,'x'*43,[],[])

    def test_blocked_recipient_does_not_stop_bot(self):
        with patch.object(shop,'api',side_effect=shop.TelegramError('sendMessage',403,'bot was blocked')):
            self.assertIsNone(shop.say(42,'reply'))

    def test_expired_checkout_does_not_block_following_updates(self):
        self.link();key=self.order()
        query={'id':'expired','invoice_payload':key,'from':{'id':42},'total_amount':15,'currency':'XTR'}
        with patch.object(shop,'api',side_effect=shop.TelegramError('answerPreCheckoutQuery',400,'query is too old')):
            shop.checkout(query)
        with shop.database() as db:
            self.assertEqual(db.execute('SELECT status FROM orders WHERE id=?',(key,)).fetchone()[0],'expired')
            self.assertFalse(shop.active(db,self.uid))

    def test_expired_callback_does_not_stop_bot(self):
        with patch.object(shop,'api',side_effect=shop.TelegramError('answerCallbackQuery',400,'query is too old')):
            shop.handle({'callback_query':{'id':'old','from':{'id':42},'data':'unused',
                         'message':{'chat':{'id':42,'type':'private'}}}})

    def test_http_app_contract_and_public_privacy(self):
        self.link();key=self.order();self.pay(key)
        server=shop.LimitedHTTP(('127.0.0.1',0),shop.Feed)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        base='http://127.0.0.1:'+str(server.server_port)
        try:
            with urllib.request.urlopen(base+'/v1/badges') as response:
                feed=json.load(response)
                self.assertEqual(response.headers['Cache-Control'],'no-store')
            self.assertEqual(feed['badges'][self.uid]['supporter'],0)
            raw=json.dumps(feed)
            self.assertNotIn(self.holder,raw);self.assertNotIn('telegram',raw);self.assertNotIn('charge-1',raw)
            body=json.dumps({'uid':self.uid,'code':self.code,'holder':self.holder}).encode()
            request=urllib.request.Request(base+'/v1/link/status',data=body,headers={'Content-Type':'application/json'})
            with urllib.request.urlopen(request) as response:
                status=json.load(response)
                self.assertTrue(status['linked']);self.assertEqual(status['code'],self.code)
        finally:server.shutdown();server.server_close();thread.join()


if __name__=='__main__':unittest.main(verbosity=2)
