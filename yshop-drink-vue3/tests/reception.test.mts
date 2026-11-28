import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import {
  acceptLineCommand,
  acceptOpen,
  acceptReservationSeat,
  acceptSeat,
  boundShopId,
  canAssignTable,
  canSeat,
  claimRequestKey,
  createRequestKey,
  forgetRequestKey,
  isRequestKey,
  lateArrivalFlag,
  lineCommandBlockReason,
  lineCommandBody,
  lineCommandScope,
  openBlockReason,
  openBody,
  openScope,
  partitionByShop,
  acceptZeroRelease,
  presentOpenSessions,
  settleBlockReason,
  settleBody,
  releaseRequestKey,
  reservationSeatBlockReason,
  reservationSeatBody,
  reservationSeatScope,
  seatBody,
  slotClockText,
  stableRequestKey,
  type DishLineView,
  type HoldView,
  type OpenSessionView,
  type SlotView,
  type TableView
} from '../src/views/mall/store/hotpot/reception/reception.ts'

const shop = 7

const hold = (patch: Partial<HoldView> = {}): HoldView => ({
  id: 11,
  slotId: 3,
  shopId: shop,
  uid: 90,
  status: 'HELD',
  sessionId: null,
  ...patch
})

const slot = (patch: Partial<SlotView> = {}): SlotView => ({
  slotId: 3,
  shopId: shop,
  capacity: 2,
  occupied: 1,
  ...patch
})

const table = (patch: Partial<TableView> = {}): TableView => ({
  tableId: 4,
  shopId: shop,
  signCode: 'A1',
  occupied: false,
  ...patch
})

test('only a positive bound shop is usable', () => {
  assert.equal(boundShopId(7), 7)
  assert.equal(boundShopId('8'), 8)
  assert.equal(boundShopId(0), null)
  assert.equal(boundShopId(-1), null)
  assert.equal(boundShopId(''), null)
  assert.equal(boundShopId('1.5'), null)
  assert.equal(boundShopId(null), null)
})

test('another shop is dropped and cannot be seated or opened', () => {
  const rows = partitionByShop(
    [hold(), hold({ id: 12, shopId: 8 }), { shopId: 9 } as HoldView],
    shop
  )
  assert.equal(rows.visible.length, 1)
  assert.equal(rows.ignored, 2)
  assert.equal(canSeat(hold({ shopId: 8 }), shop), false)
  assert.equal(openBlockReason(shop, slot({ shopId: 8 }), table()), '请选择本店时段')
  assert.equal(openBlockReason(shop, slot(), table({ shopId: 8 })), '请选择本店空闲桌台')
})

test('seat is only the held reservation in this shop', () => {
  assert.equal(canSeat(hold(), shop), true)
  assert.equal(canSeat(hold(), null), false)
  assert.equal(canSeat(hold({ status: 'SEATED' }), shop), false)
  assert.equal(canSeat(hold({ status: 'CANCELLED' }), shop), false)
  const body = seatBody(hold(), shop, 'seat-key1')
  assert.deepEqual(Object.keys(body), ['holdId', 'slotId', 'requestKey'])
  assert.deepEqual(body, { holdId: 11, slotId: 3, requestKey: 'seat-key1' })
  assert.equal('signCode' in body, false)
  assert.equal('shopId' in body, false)
  assert.equal(acceptSeat({ holdId: 11, shopId: shop, slotId: 3, status: 'SEATED' }, shop, hold()).ok, true)
  assert.equal(
    acceptSeat({ holdId: 11, shopId: 8, slotId: 3, status: 'SEATED' }, shop, hold()).ok,
    false
  )
  assert.equal(
    acceptSeat({ holdId: 11, shopId: shop, slotId: 3, status: 'HELD' }, shop, hold()).ok,
    false
  )
})

test('a claimed auto-cancel flag does not become a cancel', () => {
  const accepted = acceptSeat(
    { holdId: 11, shopId: shop, slotId: 3, status: 'SEATED', lateAutoCancels: true },
    shop,
    hold()
  )
  assert.equal(accepted.ok, true)
  if (accepted.ok) {
    assert.equal(accepted.autoCancelClaimed, true)
  }
})

test('walk-in open needs a free table and remaining capacity', () => {
  assert.equal(openBlockReason(shop, slot(), table()), null)
  assert.equal(openBlockReason(shop, slot({ occupied: 2 }), table()), '该时段没有剩余名额')
  assert.equal(openBlockReason(shop, slot({ capacity: 0, occupied: 0 }), table()), '该时段没有剩余名额')
  assert.equal(openBlockReason(shop, slot({ occupied: Number.NaN }), table()), '时段名额不完整，不能开桌')
  assert.equal(openBlockReason(shop, slot(), table({ occupied: true })), '请选择本店空闲桌台')
  assert.equal(openBlockReason(null, slot(), table()), '当前账号没有绑定门店')
  const body = openBody(shop, slot(), table(), 'open-key1')
  assert.deepEqual(Object.keys(body), ['slotId', 'signCode', 'requestKey'])
  assert.equal('holdId' in body, false)
  assert.equal('shopId' in body, false)
  assert.equal('paidFen' in body, false)
})

test('a paid or released open result is refused', () => {
  const base = { sessionId: 20, shopId: shop, slotId: 3, status: 'OPEN', payableFen: 0, paid: false }
  const ok = acceptOpen(base, shop, 3)
  assert.equal(ok.ok, true)
  if (ok.ok) {
    assert.equal(ok.payableFen, 0)
  }
  assert.equal(acceptOpen({ ...base, paid: true }, shop, 3).ok, false)
  assert.equal(acceptOpen({ ...base, paymentSuccess: true }, shop, 3).ok, false)
  assert.equal(acceptOpen({ ...base, zeroPayReleased: true }, shop, 3).ok, false)
  assert.equal(acceptOpen({ ...base, status: 'RELEASED' }, shop, 3).ok, false)
  assert.equal(acceptOpen({ ...base, shopId: 8 }, shop, 3).ok, false)
})

test('the same attempt keeps one request key', () => {
  const bucket = new Map<string, string>()
  const scope = openScope(shop, 3, 'A1')
  let n = 0
  const factory = () => {
    n += 1
    return n === 1 ? 'same-key1' : 'next-key2'
  }
  assert.equal(stableRequestKey(bucket, scope, factory), 'same-key1')
  assert.equal(stableRequestKey(bucket, scope, factory), 'same-key1')
  assert.equal(stableRequestKey(bucket, openScope(shop, 3, 'A2'), factory), 'next-key2')
  assert.equal(isRequestKey('short'), false)
  assert.throws(() => stableRequestKey(bucket, 'bad', () => 'bad key'))
  const minted = createRequestKey(() => 1)
  assert.equal(isRequestKey(minted), true)
})

const at = (hour: number, minute: number, second: number) => new Date(2026, 10, 26, hour, minute, second)

test('a passed slot end is a reminder and does not cancel or release', () => {
  assert.equal(lateArrivalFlag(), null)
  assert.equal(lateArrivalFlag(slot(), at(12, 0, 0)), null)
  assert.equal(lateArrivalFlag(slot({ startTime: '11:00:00' }), at(12, 0, 0)), null)
  assert.equal(lateArrivalFlag(slot({ endTime: '11:30:00' }), at(12, 0, 0)), null)
  assert.equal(lateArrivalFlag(slot({ startTime: '11:00', endTime: '11:30:00' }), at(12, 0, 0)), null)
  assert.equal(lateArrivalFlag(slot({ startTime: ' 11:00:00', endTime: '11:30:00' }), at(12, 0, 0)), null)

  const windowed = slot({ startTime: '11:00:00', endTime: '11:30:00', occupied: 1 })
  assert.equal(slotClockText(windowed), '11:00:00 至 11:30:00')
  assert.equal(lateArrivalFlag(windowed, at(11, 0, 0)), null)
  assert.equal(lateArrivalFlag(windowed, at(11, 30, 0)), null)
  assert.deepEqual(lateArrivalFlag(windowed, at(11, 30, 1)), {
    passed: true,
    startTime: '11:00:00',
    endTime: '11:30:00'
  })
  assert.equal(windowed.occupied, 1)
  assert.equal(windowed.capacity, 2)

  const overnight = slot({ startTime: '22:00:00', endTime: '02:00:00' })
  assert.equal(slotClockText(overnight), '22:00:00 至 02:00:00')
  assert.equal(lateArrivalFlag(overnight, at(23, 0, 0)), null)
  assert.equal(lateArrivalFlag(overnight, at(3, 0, 0)), null)

  const withClock = hold() as HoldView & { slotTime?: string; retainMinutes?: number }
  withClock.slotTime = '2000-01-01T00:00:00+08:00'
  withClock.retainMinutes = 15
  assert.equal(canAssignTable(withClock, shop), true)
  assert.equal(withClock.status, 'HELD')
  assert.equal(lateArrivalFlag(), null)
})

test('reservation seating binds an idle table for a guest and not a walk-in', () => {
  assert.equal(canAssignTable(hold(), shop), true)
  assert.equal(canAssignTable(hold({ status: 'SEATED' }), shop), true)
  assert.equal(canSeat(hold({ status: 'SEATED' }), shop), false)
  assert.equal(canAssignTable(hold({ uid: 0 }), shop), false)
  assert.equal(canAssignTable(hold({ status: 'CANCELLED' }), shop), false)
  assert.equal(canAssignTable(hold({ shopId: 8 }), shop), false)
  assert.equal(reservationSeatBlockReason(shop, hold({ uid: 0 }), table()), '散客请走开桌')
  assert.equal(reservationSeatBlockReason(shop, hold(), table({ occupied: true })), '请选择本店空闲桌台')
  assert.equal(reservationSeatBlockReason(shop, hold(), null), '请选择本店空闲桌台')
  const full = slot({ capacity: 1, occupied: 1 })
  assert.equal(openBlockReason(shop, full, table()), '该时段没有剩余名额')
  assert.equal(reservationSeatBlockReason(shop, hold(), table()), null)

  const body = reservationSeatBody(hold({ status: 'SEATED' }), shop, table(), 'seat-key1')
  assert.deepEqual(Object.keys(body), ['holdId', 'slotId', 'signCode', 'requestKey'])
  assert.deepEqual(body, { holdId: 11, slotId: 3, signCode: 'A1', requestKey: 'seat-key1' })
  assert.equal('shopId' in body, false)
  assert.equal('capacity' in body, false)
  assert.equal('occupied' in body, false)
  assert.equal('paidFen' in body, false)
  assert.throws(() => reservationSeatBody(hold({ uid: 0 }), shop, table(), 'seat-key1'))
  assert.throws(() => reservationSeatBody(hold(), shop, table(), 'short'))

  const opened = {
    sessionId: 20,
    shopId: shop,
    slotId: 3,
    holdId: 11,
    ownerUid: 90,
    status: 'OPEN',
    payableFen: 0,
    paid: false,
    zeroPayReleased: false,
    paymentSuccess: false
  }
  const accepted = acceptReservationSeat(opened, shop, hold())
  assert.equal(accepted.ok, true)
  if (accepted.ok) {
    assert.equal(accepted.ownerUid, 90)
    assert.equal(accepted.sessionId, 20)
    assert.equal(accepted.payableFen, 0)
  }
  assert.equal(acceptReservationSeat({ ...opened, ownerUid: 0 }, shop, hold()).ok, false)
  assert.equal(acceptReservationSeat({ ...opened, holdId: 12 }, shop, hold()).ok, false)
  assert.equal(acceptReservationSeat({ ...opened, shopId: 8 }, shop, hold()).ok, false)
  assert.equal(acceptReservationSeat({ ...opened, paid: true }, shop, hold()).ok, false)
  assert.equal(acceptReservationSeat({ ...opened, paymentSuccess: true }, shop, hold()).ok, false)
  assert.equal(acceptReservationSeat({ ...opened, zeroPayReleased: true }, shop, hold()).ok, false)
  assert.equal(acceptReservationSeat({ ...opened, status: 'RELEASED' }, shop, hold()).ok, false)
  assert.equal(reservationSeatBlockReason(shop, hold(), table()), null)
})

test('the same table reuses one reservation seat key', () => {
  const bucket = new Map<string, string>()
  const guest = hold()
  const same = reservationSeatScope(shop, guest, 'A1')
  let n = 0
  const factory = () => {
    n += 1
    return `retry-key${n}`
  }
  assert.equal(stableRequestKey(bucket, same, factory), 'retry-key1')
  assert.equal(stableRequestKey(bucket, same, factory), 'retry-key1')
  assert.equal(stableRequestKey(bucket, reservationSeatScope(shop, guest, 'A2'), factory), 'retry-key2')
  assert.equal(stableRequestKey(bucket, reservationSeatScope(shop, hold({ id: 12 }), 'A1'), factory), 'retry-key3')
  forgetRequestKey(bucket, same)
  assert.equal(stableRequestKey(bucket, same, factory), 'retry-key4')
  assert.notEqual(same, openScope(shop, 3, 'A1'))
})

const dish = (patch: Partial<DishLineView> = {}): DishLineView => ({
  lineId: 5,
  name: '鲜牛肉',
  priceFen: 1800,
  quantity: 4,
  servedQty: 1,
  refundedQty: 0,
  refundableQty: 3,
  ...patch
})

const openSession = (patch: Partial<OpenSessionView> = {}): OpenSessionView => ({
  sessionId: 20,
  shopId: shop,
  signCode: 'A1',
  version: 3,
  payableFen: 7200,
  feesWaived: false,
  status: 'OPEN',
  pots: [],
  dishes: [dish()],
  ...patch
})

test('in-progress sessions keep this shop and the line quantities', () => {
  const rows = presentOpenSessions(
    [
      {
        sessionId: 20,
        shopId: shop,
        signCode: 'A1',
        version: 3,
        payableFen: 7200,
        feesWaived: false,
        status: 'OPEN',
        pots: [{ potId: 4, name: '红锅', priceFen: 3577 }],
        dishes: [
          {
            lineId: 5,
            name: '鲜牛肉',
            priceFen: 1800,
            quantity: 4,
            servedQty: 1,
            refundedQty: 0,
            refundableQty: 3
          }
        ]
      },
      { sessionId: 21, shopId: 8, signCode: 'B2', version: 1, status: 'OPEN' },
      { sessionId: 22, shopId: shop, version: 1, status: 'RELEASED', zeroPayReleased: true },
      { sessionId: 23, shopId: shop, version: 1, status: 'OPEN', paid: true }
    ],
    shop
  )
  assert.equal(rows.visible.length, 1)
  assert.equal(rows.ignored, 3)
  assert.equal(rows.visible[0].signCode, 'A1')
  assert.equal(rows.visible[0].feesWaived, false)
  assert.equal(rows.visible[0].pots.length, 1)
  assert.equal(rows.visible[0].pots[0].name, '红锅')
  assert.equal(rows.visible[0].pots[0].priceFen, 3577)
  assert.equal(rows.visible[0].dishes[0].quantity, 4)
  assert.equal(rows.visible[0].dishes[0].servedQty, 1)
  assert.equal(rows.visible[0].dishes[0].refundableQty, 3)
  assert.equal(presentOpenSessions([], shop).visible.length, 0)
})

test('serve and refund bodies stay on the line and do not carry a shop or a payment', () => {
  const session = openSession()
  const line = session.dishes[0]
  assert.equal(lineCommandBlockReason(shop, session, line, 3, 'serve'), null)
  assert.equal(lineCommandBlockReason(shop, session, line, 4, 'serve'), '上菜份数超过还没上桌的数量')
  assert.equal(lineCommandBlockReason(shop, session, line, 4, 'refund'), '已上桌的不能退')
  assert.equal(lineCommandBlockReason(shop, session, dish({ refundableQty: 0, servedQty: 4 }), 1, 'refund'), '这道菜没有未上桌的份数')

  const body = lineCommandBody(session, shop, line, 2, 'line-key1', 'refund')
  assert.deepEqual(Object.keys(body), ['sessionId', 'version', 'requestKey', 'items'])
  assert.deepEqual(body, {
    sessionId: 20,
    version: 3,
    requestKey: 'line-key1',
    items: [{ lineId: 5, quantity: 2 }]
  })
  assert.equal('shopId' in body, false)
  assert.equal('paidFen' in body, false)
  assert.throws(() => lineCommandBody(session, shop, line, 4, 'line-key1', 'refund'))

  const scope = lineCommandScope('serve', 20, 5, 1)
  const bucket = new Map<string, string>()
  const inflight = new Set<string>()
  let n = 0
  const factory = () => {
    n += 1
    return n === 1 ? 'serve-key1' : 'serve-key2'
  }
  const key = stableRequestKey(bucket, scope, factory)
  assert.equal(claimRequestKey(inflight, key), true)
  assert.equal(stableRequestKey(bucket, scope, factory), key)
  assert.equal(claimRequestKey(inflight, key), false)
  releaseRequestKey(inflight, key)
  assert.equal(claimRequestKey(inflight, key), true)

  const released = acceptLineCommand(
    {
      sessionId: 20,
      shopId: shop,
      status: 'RELEASED',
      paid: false,
      zeroPayReleased: true,
      paymentSuccess: false,
      feesWaived: true,
      payableFen: 0
    },
    shop,
    20
  )
  assert.equal(released.ok, true)
  if (released.ok) {
    assert.equal(released.released, true)
    assert.equal(released.feesWaived, true)
  }
  assert.equal(
    acceptLineCommand(
      { sessionId: 20, shopId: shop, status: 'OPEN', paid: true, paymentSuccess: true },
      shop,
      20
    ).ok,
    false
  )
  assert.equal(
    acceptLineCommand(
      { sessionId: 20, shopId: shop, status: 'OPEN', paymentSuccess: true, paid: false },
      shop,
      20
    ).ok,
    false
  )
})

test('pot lines stay on the open session and only a zero bill can be released', () => {
  const kept = presentOpenSessions(
    [
      {
        sessionId: 20,
        shopId: shop,
        signCode: 'A1',
        version: 3,
        payableFen: 0,
        status: 'OPEN',
        pots: [
          { potId: 4, name: '红锅', priceFen: 3577 },
          { name: '清汤' },
          {}
        ],
        dishes: []
      }
    ],
    shop
  )
  assert.equal(kept.visible.length, 1)
  assert.equal(kept.visible[0].pots.length, 2)
  assert.equal(kept.visible[0].pots[0].priceFen, 3577)
  assert.equal(kept.visible[0].pots[1].name, '清汤')
  assert.equal(kept.visible[0].pots[1].priceFen, null)

  const zero = openSession({ payableFen: 0, pots: [{ potId: 4, name: '红锅', priceFen: 3577 }] })
  assert.equal(settleBlockReason(shop, zero), null)
  assert.equal(settleBlockReason(shop, openSession({ payableFen: 1800 })), '应付不是 0，不能结清放桌')
  assert.equal(settleBlockReason(shop, openSession({ payableFen: null })), '应付不是 0，不能结清放桌')
  const body = settleBody(zero, shop)
  assert.deepEqual(Object.keys(body), ['sessionId', 'version'])
  assert.deepEqual(body, { sessionId: 20, version: 3 })
  assert.equal('shopId' in body, false)
  assert.equal('paidFen' in body, false)
  assert.throws(() => settleBody(openSession({ payableFen: 1 }), shop))

  const released = acceptZeroRelease(
    {
      sessionId: 20,
      shopId: shop,
      payableFen: 0,
      paid: false,
      paymentSuccess: false,
      zeroPayReleased: true,
      disposition: 'ZERO_PAY'
    },
    shop,
    20
  )
  assert.equal(released.ok, true)
  if (released.ok) {
    assert.equal(released.paid, false)
    assert.equal(released.released, true)
  }
  assert.equal(
    acceptZeroRelease(
      { sessionId: 20, shopId: shop, paid: true, paymentSuccess: true, zeroPayReleased: true, disposition: 'ZERO_PAY' },
      shop,
      20
    ).ok,
    false
  )
  assert.equal(
    acceptZeroRelease(
      { sessionId: 20, shopId: shop, paid: false, paymentSuccess: false, zeroPayReleased: false, payableFen: 1800 },
      shop,
      20
    ).ok,
    false
  )
})

test('the reception page shows pots and releases only a zero payable', () => {
  const page = readFileSync(
    new URL('../src/views/mall/store/hotpot/reception/index.vue', import.meta.url),
    'utf8'
  )
  const api = readFileSync(new URL('../src/api/mall/store/hotpot/index.ts', import.meta.url), 'utf8')
  assert.match(page, /进行中桌次/)
  assert.match(page, /本店没有进行中的桌次/)
  assert.match(page, /serveDishes/)
  assert.match(page, /refundDishes/)
  assert.match(page, /session\.pots/)
  assert.match(page, /结清放桌/)
  assert.match(page, /payableFen === 0/)
  assert.match(page, /settleOpenSession/)
  assert.match(page, /这不是支付成功/)
  assert.equal(page.includes('微信支付'), false)
  assert.equal(page.includes('支付宝'), false)
  assert.equal(page.includes('人工清台'), false)
  assert.match(api, /\/store\/hotpot\/session'/)
  assert.match(api, /\/store\/hotpot\/session\/serve/)
  assert.match(api, /\/store\/hotpot\/session\/refund/)
  assert.match(api, /\/store\/hotpot\/session\/settle/)
})
