"""Operator-only refund: run with the same environment as shop.py. Not a bot command."""
import sys
from shop import database, api


def main():
    if len(sys.argv) != 2:
        raise SystemExit('Usage: python refund.py ORDER_ID')
    with database() as db:
        payment=db.execute('SELECT * FROM payments WHERE order_id=?',(sys.argv[1],)).fetchone()
    if not payment: raise SystemExit('Order not found')
    if payment['refunded']: raise SystemExit('Already refunded')
    api('refundStarPayment', user_id=payment['buyer'], telegram_payment_charge_id=payment['charge'])
    with database() as db:
        db.execute('UPDATE payments SET refunded=1 WHERE charge=?',(payment['charge'],))
    print('Refund confirmed; grant revoked')


if __name__ == '__main__': main()
