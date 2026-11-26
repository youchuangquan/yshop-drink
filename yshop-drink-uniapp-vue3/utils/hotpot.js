/**
 * 顾客火锅页只用接口返回的字段。
 * 扫码只读。空桌要等第一道菜提交成功，服务端才占桌。
 * 点菜必须带已有的 slotId。结账只用账单上的 version，应付金额也只显示账单返回的 payableFen。
 * paymentSuccess 不能当成已付。
 */

const REQUEST_KEY = /^[A-Za-z0-9_-]{8,64}$/

export function shopReady(shopId) {
  if (typeof shopId === 'number') {
    return Number.isInteger(shopId) && shopId > 0
  }
  if (typeof shopId !== 'string') {
    return false
  }
  return /^[1-9]\d*$/.test(shopId.trim())
}

export function idReady(value) {
  if (typeof value === 'number') {
    return Number.isInteger(value) && value > 0
  }
  if (typeof value !== 'string') {
    return false
  }
  return /^[1-9]\d*$/.test(value.trim())
}

export function remainingCapacity(slot) {
  if (!slot || typeof slot.capacity !== 'number' || typeof slot.occupied !== 'number') {
    return null
  }
  if (!Number.isInteger(slot.capacity) || !Number.isInteger(slot.occupied)) {
    return null
  }
  if (slot.capacity < 0 || slot.occupied < 0) {
    return null
  }
  return slot.capacity - slot.occupied
}

export function visibleSlots(rows, shopId) {
  if (!Array.isArray(rows) || !shopReady(shopId)) {
    return []
  }
  return rows.filter((slot) => slot && idReady(slot.slotId) && String(slot.shopId) === String(shopId))
}

export function canReserve(slot, loggedIn, shopId) {
  if (loggedIn !== true || !slot || !shopReady(shopId) || !idReady(slot.slotId)) {
    return false
  }
  if (String(slot.shopId) !== String(shopId)) {
    return false
  }
  const left = remainingCapacity(slot)
  return left != null && left > 0
}

export function reservationBody(shopId, slot, requestKey) {
  if (!canReserve(slot, true, shopId)) {
    return null
  }
  if (typeof requestKey !== 'string' || !REQUEST_KEY.test(requestKey)) {
    return null
  }
  return {
    shopId,
    slotId: slot.slotId,
    requestKey,
  }
}

export function sameRequest(previous, slotId) {
  if (!previous || previous.done === true || !idReady(slotId) || !idReady(previous.slotId)) {
    return false
  }
  if (String(previous.slotId) !== String(slotId)) {
    return false
  }
  return typeof previous.key === 'string' && REQUEST_KEY.test(previous.key)
}

export function cancelBody(holdId) {
  if (!idReady(holdId)) {
    return null
  }
  return { holdId }
}

export function holdStatusText(status) {
  if (status === 'HELD') return '已占名额'
  if (status === 'CANCELLED') return '已取消'
  if (status === 'SEATED') return '已入座'
  if (status === 'RELEASED') return '已释放'
  if (typeof status === 'string' && status) return status
  return ''
}

function decodePart(value) {
  try {
    return decodeURIComponent(String(value).replace(/\+/g, ' '))
  } catch (error) {
    return null
  }
}

function queryParams(query) {
  const params = {}
  for (const part of String(query).split('&')) {
    if (!part) continue
    const index = part.indexOf('=')
    const rawKey = index >= 0 ? part.slice(0, index) : part
    const rawValue = index >= 0 ? part.slice(index + 1) : ''
    const key = decodePart(rawKey)
    const value = decodePart(rawValue)
    if (key == null || value == null) {
      return null
    }
    params[key] = value
  }
  return params
}

export function readScan(raw, currentShopId) {
  if (raw == null) {
    return null
  }
  const text = String(raw).trim()
  if (!text) {
    return null
  }
  const queryAt = text.indexOf('?')
  const query = queryAt >= 0 ? text.slice(queryAt + 1).split('#')[0] : (text.includes('=') ? text.split('#')[0] : '')
  if (query) {
    const params = queryParams(query)
    if (!params) {
      return null
    }
    const signCode = typeof params.signCode === 'string' ? params.signCode.trim() : ''
    const shopId = shopReady(params.shopId) ? params.shopId : (shopReady(currentShopId) ? currentShopId : null)
    if (!signCode || !shopId) {
      return null
    }
    return { shopId, signCode }
  }
  if (text.includes('://') || text.includes('/')) {
    return null
  }
  if (!shopReady(currentShopId)) {
    return null
  }
  return { shopId: currentShopId, signCode: text }
}

export function scanState(response) {
  if (!response || typeof response.signCode !== 'string' || !response.signCode.trim()) {
    return { kind: 'invalid', showMenu: false, occupy: false }
  }
  const signCode = response.signCode.trim()
  if (response.occupied === true) {
    return { kind: 'occupied', signCode, showMenu: false, occupy: false }
  }
  if (response.occupied === false) {
    return { kind: 'idle', signCode, showMenu: true, occupy: false }
  }
  return { kind: 'invalid', showMenu: false, occupy: false }
}

export function menuRows(goods) {
  if (!Array.isArray(goods)) {
    return []
  }
  const rows = []
  for (const category of goods) {
    if (!category || !Array.isArray(category.goodsList)) {
      continue
    }
    const items = []
    for (const good of category.goodsList) {
      if (!good || good.id == null) {
        continue
      }
      items.push({
        id: good.id,
        name: good.storeName == null ? '' : String(good.storeName),
        info: good.storeInfo == null ? '' : String(good.storeInfo),
        image: good.image || '',
        price: good.price == null || good.price === '' ? null : good.price,
      })
    }
    if (items.length > 0) {
      rows.push({
        id: category.id,
        name: category.name == null ? '' : String(category.name),
        goods: items,
      })
    }
  }
  return rows
}

const CLOCK = /^(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d$/

export function slotClock(value) {
  return typeof value === 'string' && CLOCK.test(value) ? value : ''
}

export function holdStorageKey(shopId) {
  return `hotpotHold:${shopId}`
}

export function holdRecord(hold, shopId) {
  if (!hold || typeof hold !== 'object' || !shopReady(shopId)) {
    return null
  }
  if (!idReady(hold.holdId) || !idReady(hold.slotId)) {
    return null
  }
  if (hold.shopId != null && hold.shopId !== '' && String(hold.shopId) !== String(shopId)) {
    return null
  }
  if (hold.status !== 'HELD' && hold.status !== 'SEATED') {
    return null
  }
  return {
    holdId: hold.holdId,
    shopId: hold.shopId == null || hold.shopId === '' ? shopId : hold.shopId,
    slotId: hold.slotId,
    status: hold.status,
  }
}

export function rememberHold(storage, shopId, hold) {
  if (!storage || typeof storage.set !== 'function' || typeof storage.remove !== 'function' || !shopReady(shopId)) {
    return null
  }
  const record = holdRecord(hold, shopId)
  const key = holdStorageKey(shopId)
  if (!record) {
    storage.remove(key)
    return null
  }
  storage.set(key, record)
  return record
}

export function readHold(storage, shopId) {
  if (!storage || typeof storage.get !== 'function' || !shopReady(shopId)) {
    return null
  }
  const raw = storage.get(holdStorageKey(shopId))
  if (raw == null || raw === '') {
    return null
  }
  return holdRecord(raw, shopId)
}

export function reservedSlot(rows, shopId, record) {
  const saved = holdRecord(record, shopId)
  if (!saved) {
    return null
  }
  const slots = visibleSlots(rows, shopId)
  for (const slot of slots) {
    if (String(slot.slotId) === String(saved.slotId)) {
      return slot
    }
  }
  return null
}

export function dishBody(input) {
  if (!input || !shopReady(input.shopId) || !idReady(input.slotId) || !idReady(input.productId)) {
    return null
  }
  if (typeof input.signCode !== 'string' || !input.signCode.trim()) {
    return null
  }
  if (typeof input.quantity !== 'number' || !Number.isInteger(input.quantity) || input.quantity < 1) {
    return null
  }
  if (typeof input.requestKey !== 'string' || !REQUEST_KEY.test(input.requestKey)) {
    return null
  }
  const body = {
    shopId: input.shopId,
    slotId: input.slotId,
    signCode: input.signCode.trim(),
    productId: input.productId,
    quantity: input.quantity,
    requestKey: input.requestKey,
  }
  if (input.open === true) {
    if (typeof input.version !== 'number' || !Number.isInteger(input.version) || input.version < 1) {
      return null
    }
    body.version = input.version
  }
  return body
}

function dishMark(dish) {
  if (!dish || !idReady(dish.productId) || !idReady(dish.slotId)) {
    return ''
  }
  if (typeof dish.quantity !== 'number' || !Number.isInteger(dish.quantity) || dish.quantity < 1) {
    return ''
  }
  if (typeof dish.signCode !== 'string' || !dish.signCode.trim()) {
    return ''
  }
  const version = dish.version == null ? '' : String(dish.version)
  return [String(dish.productId), String(dish.quantity), String(dish.slotId), dish.signCode.trim(), version].join('|')
}

export function sameDishAttempt(previous, dish) {
  if (!previous || previous.done === true) {
    return false
  }
  if (typeof previous.key !== 'string' || !REQUEST_KEY.test(previous.key)) {
    return false
  }
  const left = dishMark(previous)
  const right = dishMark(dish)
  return left !== '' && left === right
}

export function openedSession(response) {
  if (!response || !idReady(response.sessionId)) {
    return null
  }
  if (typeof response.version !== 'number' || !Number.isInteger(response.version) || response.version < 1) {
    return null
  }
  return {
    sessionId: response.sessionId,
    version: response.version,
    slotId: idReady(response.slotId) ? response.slotId : null,
    payableFen: integerFen(response.payableFen),
    status: typeof response.status === 'string' ? response.status : '',
  }
}

function integerFen(value) {
  return typeof value === 'number' && Number.isInteger(value) ? value : null
}

function lineName(value) {
  return value == null ? '' : String(value)
}

export function presentBill(bill) {
  if (!bill || !idReady(bill.sessionId)) {
    return null
  }
  const pots = Array.isArray(bill.pots)
    ? bill.pots.filter((pot) => pot && (pot.name || pot.potId != null || pot.priceFen != null)).map((pot) => ({
      potId: pot.potId,
      name: lineName(pot.name),
      priceFen: integerFen(pot.priceFen),
    }))
    : []
  const dishes = Array.isArray(bill.dishes)
    ? bill.dishes.filter((dish) => dish && (dish.name || dish.productId != null)).map((dish) => ({
      productId: dish.productId,
      name: lineName(dish.name),
      priceFen: integerFen(dish.priceFen),
      quantity: typeof dish.quantity === 'number' && Number.isInteger(dish.quantity) && dish.quantity >= 1 ? dish.quantity : null,
    }))
    : []
  return {
    sessionId: bill.sessionId,
    version: typeof bill.version === 'number' && Number.isInteger(bill.version) && bill.version >= 0 ? bill.version : null,
    payableFen: integerFen(bill.payableFen),
    status: typeof bill.status === 'string' ? bill.status : '',
    pots,
    dishes,
  }
}

export function checkoutBody(bill) {
  if (!bill || !idReady(bill.sessionId)) {
    return null
  }
  if (typeof bill.version !== 'number' || !Number.isInteger(bill.version) || bill.version < 0) {
    return null
  }
  return {
    sessionId: bill.sessionId,
    version: bill.version,
  }
}

export function canSettle(bill) {
  if (!bill || integerFen(bill.payableFen) == null) {
    return false
  }
  return checkoutBody(bill) != null
}

export const BILL_CHECKOUT_GAP = '这份账单缺少整数 version 或应付金额。不能发起结账，也不会把账单标成已付。'

export const CHANNEL_CLOSED = '收款渠道未接通'

export function settleFailureText(error, payableFen) {
  const body = error && (error.data || (error.response && error.response.data))
  const msg = body && typeof body.msg === 'string' ? body.msg.trim() : ''
  if (msg) {
    return msg
  }
  if (typeof payableFen === 'number' && Number.isInteger(payableFen) && payableFen > 0) {
    return CHANNEL_CLOSED
  }
  return '结账没有完成，账单未支付。'
}

export function sessionStatusText(status) {
  if (status === 'OPEN') return '开台中'
  if (status === 'RELEASED') return '已释放'
  if (typeof status === 'string' && status) return status
  return ''
}

export function formatFen(fen) {
  if (typeof fen !== 'number' || !Number.isInteger(fen)) {
    return ''
  }
  const sign = fen < 0 ? '-' : ''
  const abs = Math.abs(fen)
  const yuan = Math.trunc(abs / 100)
  const cent = String(abs % 100).padStart(2, '0')
  return `${sign}${yuan}.${cent}`
}

export function presentSettlement(session) {
  if (!session || typeof session !== 'object') {
    return null
  }
  const disposition = typeof session.disposition === 'string' ? session.disposition : null
  // paymentSuccess 单独为 true 时仍是未支付。应付为 0 的释放也不是支付成功。
  const paid = session.paid === true && session.paymentSuccess === true && disposition !== 'UNCONNECTED' && session.zeroPayReleased !== true
  const pots = Array.isArray(session.pots)
    ? session.pots.filter((pot) => pot && (pot.name || pot.priceFen != null)).map((pot) => ({
      potId: pot.potId,
      name: pot.name == null ? '' : String(pot.name),
      priceFen: typeof pot.priceFen === 'number' && Number.isInteger(pot.priceFen) ? pot.priceFen : null,
    }))
    : []
  return {
    paid,
    paymentSuccess: paid,
    zeroPayReleased: session.zeroPayReleased === true,
    disposition,
    payableFen: typeof session.payableFen === 'number' && Number.isInteger(session.payableFen) ? session.payableFen : null,
    status: typeof session.status === 'string' ? session.status : '',
    pots,
  }
}

export function settlementText(view) {
  if (!view) {
    return ''
  }
  if (view.paid === true) {
    return ''
  }
  if (view.zeroPayReleased === true) {
    return '应付为 0，桌次已释放。这不是支付成功。'
  }
  return '账单未支付。'
}
