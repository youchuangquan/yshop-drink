/**
 * 顾客火锅页只用现有接口返回的字段。
 * 预约占的是时段容量。扫码不占桌。账单缺 version 时不能结账，也不能把账单标成已付。
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

export const BILL_CHECKOUT_GAP = '这份账单只返回了开台号。结账还需要 version，应付金额和明细也没有返回，所以不能发起结账，也不会把账单标成已付。'

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
  const paid = session.paid === true && session.paymentSuccess === true && disposition !== 'UNCONNECTED'
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
