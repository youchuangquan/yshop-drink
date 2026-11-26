import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  shopReady,
  remainingCapacity,
  visibleSlots,
  canReserve,
  reservationBody,
  sameRequest,
  cancelBody,
  holdStatusText,
  readScan,
  scanState,
  menuRows,
  checkoutBody,
  BILL_CHECKOUT_GAP,
  formatFen,
  presentSettlement,
  settlementText,
} from '../utils/hotpot.js'

const root = new URL('..', import.meta.url)

function source(path) {
  return readFileSync(new URL(path, root), 'utf8')
}

const slot = { slotId: 7, shopId: 3, capacity: 4, occupied: 1 }

test('remaining capacity comes only from the slot that was returned', () => {
  assert.equal(remainingCapacity(slot), 3)
  assert.equal(remainingCapacity({ capacity: 2, occupied: 2 }), 0)
  assert.equal(remainingCapacity({ capacity: 1, occupied: 2 }), -1)
  assert.equal(remainingCapacity({ capacity: null, occupied: 0 }), null)
  assert.equal(remainingCapacity({ capacity: 1.5, occupied: 0 }), null)
  assert.equal(remainingCapacity(null), null)
})

test('reservation needs login, a matching shop, and remaining capacity, and does not name a table', () => {
  assert.equal(canReserve(slot, false, 3), false)
  assert.equal(canReserve(slot, true, 9), false)
  assert.equal(canReserve({ ...slot, occupied: 4 }, true, 3), false)
  assert.equal(canReserve(slot, true, 3), true)
  const body = reservationBody(3, slot, 'abcd1234')
  assert.deepEqual(body, { shopId: 3, slotId: 7, requestKey: 'abcd1234' })
  assert.equal('signCode' in body, false)
  assert.equal('tableId' in body, false)
  assert.equal(reservationBody(3, slot, 'short'), null)
  assert.equal(reservationBody(3, { ...slot, occupied: 4 }, 'abcd1234'), null)
  assert.deepEqual(visibleSlots([slot, { slotId: 8, shopId: 9, capacity: 1, occupied: 0 }], 3), [slot])
})

test('a repeated reservation keeps the same request key until it succeeds', () => {
  const first = { slotId: 7, key: 'abcd1234', done: false }
  assert.equal(sameRequest(first, 7), true)
  assert.equal(sameRequest(first, 8), false)
  assert.equal(sameRequest({ ...first, done: true }, 7), false)
  assert.deepEqual(cancelBody(11), { holdId: 11 })
  assert.equal(Object.keys(cancelBody(11)).join(','), 'holdId')
  assert.equal(cancelBody(0), null)
  assert.equal(holdStatusText('HELD'), '已占名额')
  assert.equal(holdStatusText('CANCELLED'), '已取消')
})

test('an idle scan shows the returned sign and menu, and never occupies the table', () => {
  const idle = scanState({ signCode: 'A12', occupied: false })
  assert.deepEqual(idle, { kind: 'idle', signCode: 'A12', showMenu: true, occupy: false })
  const busy = scanState({ signCode: 'A12', occupied: true })
  assert.equal(busy.showMenu, false)
  assert.equal(busy.occupy, false)
  assert.equal(scanState({ signCode: 'A12' }).kind, 'invalid')
  assert.equal(scanState({ occupied: false }).kind, 'invalid')
  assert.deepEqual(readScan('https://shop.example/t?shopId=3&signCode=A12'), { shopId: '3', signCode: 'A12' })
  assert.deepEqual(readScan('A12', 3), { shopId: 3, signCode: 'A12' })
  assert.equal(readScan('https://shop.example/t', 3), null)
  assert.equal(readScan('A12', null), null)
  assert.equal(shopReady(0), false)
  assert.equal(shopReady('3'), true)
})

test('menu rows keep returned prices and drop blank rows', () => {
  const rows = menuRows([
    {
      id: 1,
      name: '锅底',
      goodsList: [
        { id: 5, storeName: '肥牛', storeInfo: '', image: '', price: 28 },
        { id: 6, storeName: '未标价', price: null },
        { storeName: '没有编号', price: 1 },
      ],
    },
    { id: 2, name: '空分类', goodsList: [] },
  ])
  assert.equal(rows.length, 1)
  assert.equal(rows[0].goods[0].price, 28)
  assert.equal(rows[0].goods[1].price, null)
  assert.equal(rows[0].goods.some((item) => item.name === '没有编号'), false)
})

test('checkout cannot start from a bill that only has a session id, and payment stays closed', () => {
  assert.equal(checkoutBody({ sessionId: 15 }), null)
  assert.equal(checkoutBody({ sessionId: 15, version: '1' }), null)
  assert.deepEqual(checkoutBody({ sessionId: 15, version: 2 }), { sessionId: 15, version: 2 })
  const unconnected = presentSettlement({
    paid: true,
    paymentSuccess: false,
    disposition: 'UNCONNECTED',
    payableFen: 1800,
    pots: [{ potId: 1, name: '单锅', priceFen: 1800 }],
  })
  assert.equal(unconnected.paid, false)
  assert.equal(unconnected.paymentSuccess, false)
  assert.equal(settlementText(unconnected), '账单未支付。')
  const zero = presentSettlement({
    paid: false,
    paymentSuccess: false,
    zeroPayReleased: true,
    disposition: 'ZERO_PAY',
    payableFen: 0,
  })
  assert.equal(zero.paid, false)
  assert.equal(settlementText(zero), '应付为 0，桌次已释放。这不是支付成功。')
  assert.equal(formatFen(1800), '18.00')
  assert.equal(formatFen(0), '0.00')
  assert.equal(formatFen(null), '')
  assert.match(BILL_CHECKOUT_GAP, /version/)
})

test('customer pages stay on the existing hotpot reads and do not list reservations or wire pay', () => {
  const api = source('api/hotpot.js')
  for (const forbidden of ['walk-in', 'session/open', '/seat', '我的预约', 'alipay', 'payUnify']) {
    assert.equal(api.includes(forbidden), false, forbidden)
  }
  assert.match(api, /\/hotpot\/slot/)
  assert.match(api, /\/hotpot\/reservation/)
  assert.match(api, /\/hotpot\/scan/)
  assert.match(api, /\/hotpot\/bill/)
  assert.match(api, /\/hotpot\/session\/settle/)

  const reserve = source('pages-hotpot/reserve/reserve.vue')
  const scan = source('pages-hotpot/scan/scan.vue')
  const bill = source('pages-hotpot/bill/bill.vue')
  const pages = source('pages.json')
  assert.equal(reserve.includes('我的预约'), false)
  assert.equal(reserve.includes('hotpotScan'), false)
  assert.equal(scan.includes('hotpotSettle'), false)
  assert.equal(scan.includes('walk-in'), false)
  assert.equal(scan.includes('session/open'), false)
  assert.equal(scan.includes('/hotpot/seat'), false)
  assert.equal(bill.includes('alipay'), false)
  assert.equal(bill.includes('weixin'), false)
  assert.equal(bill.includes('payUnify'), false)
  assert.equal(bill.includes('paid: true'), false)
  assert.equal(bill.includes('paid = true'), false)
  const startCheckout = bill.slice(bill.indexOf('const startCheckout'))
  assert.ok(startCheckout.indexOf('checkoutBody(') < startCheckout.indexOf('hotpotSettle'))
  assert.match(bill, /BILL_CHECKOUT_GAP/)
  assert.match(pages, /"root": "pages-hotpot"/)
  assert.match(pages, /"path": "reserve\/reserve"/)
  assert.match(pages, /"path": "scan\/scan"/)
  assert.match(pages, /"path": "bill\/bill"/)
})
