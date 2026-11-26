/**
 * 预约接待与散客开桌的页面规则。
 * 只组现有店员接口的报文，不带门店编号，也不带金额或支付渠道。
 * 时段没有开始或结束时间，这里不能判断是否已过点。
 */

const KEY_PATTERN = /^[A-Za-z0-9_-]{8,64}$/
const KEY_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-'

export interface SlotView {
  slotId: number
  shopId: number
  capacity: number
  occupied: number
}

export interface TableView {
  tableId: number
  shopId: number
  signCode: string
  occupied: boolean
}

export interface HoldView {
  id: number
  slotId: number
  shopId: number
  uid: number
  status: string
  sessionId?: number | null
}

export interface SeatBody {
  holdId: number
  slotId: number
  requestKey: string
}

export interface OpenBody {
  slotId: number
  signCode: string
  requestKey: string
}

export function boundShopId(raw: unknown): number | null {
  if (typeof raw === 'number' && Number.isSafeInteger(raw) && raw > 0) {
    return raw
  }
  if (typeof raw === 'string' && /^[1-9]\d*$/.test(raw)) {
    const parsed = Number(raw)
    if (Number.isSafeInteger(parsed) && parsed > 0) {
      return parsed
    }
  }
  return null
}

export function isRequestKey(raw: unknown): raw is string {
  return typeof raw === 'string' && KEY_PATTERN.test(raw)
}

export function createRequestKey(random?: () => number): string {
  let body = ''
  for (let i = 0; i < 16; i++) {
    const sample = random ? random() : cryptoByte()
    const index = Math.abs(Math.trunc(sample)) % KEY_ALPHABET.length
    body += KEY_ALPHABET[index]
  }
  return `rk${body}`
}

export function stableRequestKey(
  bucket: Map<string, string>,
  scope: string,
  factory: () => string
): string {
  const existing = bucket.get(scope)
  if (isRequestKey(existing)) {
    return existing
  }
  const created = factory()
  if (!isRequestKey(created)) {
    throw new Error('request key rejected')
  }
  bucket.set(scope, created)
  return created
}

export function forgetRequestKey(bucket: Map<string, string>, scope: string) {
  bucket.delete(scope)
}

/**
 * 没有时段时刻，不能标迟到。不接收停留时长。
 */
export function lateArrivalFlag(): null {
  return null
}

export function partitionByShop<T extends { shopId: number }>(rows: T[] | null | undefined, shopId: number) {
  const visible: T[] = []
  let ignored = 0
  if (!Array.isArray(rows)) {
    return { visible, ignored }
  }
  for (const row of rows) {
    if (row && row.shopId === shopId) {
      visible.push(row)
    } else {
      ignored++
    }
  }
  return { visible, ignored }
}

export function canSeat(hold: HoldView | null | undefined, shopId: number | null): boolean {
  if (shopId == null || !hold) {
    return false
  }
  return (
    hold.shopId === shopId &&
    hold.status === 'HELD' &&
    positive(hold.id) &&
    positive(hold.slotId)
  )
}

export function seatBody(hold: HoldView, shopId: number, requestKey: string): SeatBody {
  if (!canSeat(hold, shopId) || !isRequestKey(requestKey)) {
    throw new Error('seat rejected')
  }
  return {
    holdId: hold.id,
    slotId: hold.slotId,
    requestKey
  }
}

export function seatScope(shopId: number, hold: HoldView): string {
  return `seat:${shopId}:${hold.id}:${hold.slotId}`
}

export function openBlockReason(
  shopId: number | null,
  slot: SlotView | null | undefined,
  table: TableView | null | undefined
): string | null {
  if (shopId == null) {
    return '当前账号没有绑定门店'
  }
  if (!slot || slot.shopId !== shopId) {
    return '请选择本店时段'
  }
  if (!Number.isSafeInteger(slot.capacity) || !Number.isSafeInteger(slot.occupied)) {
    return '时段名额不完整，不能开桌'
  }
  if (slot.capacity < 0 || slot.occupied < 0 || slot.occupied >= slot.capacity) {
    return '该时段没有剩余名额'
  }
  if (!table || table.shopId !== shopId || table.occupied !== false) {
    return '请选择本店空闲桌台'
  }
  if (typeof table.signCode !== 'string' || table.signCode.trim() === '') {
    return '请选择本店空闲桌台'
  }
  return null
}

export function openBody(
  shopId: number,
  slot: SlotView,
  table: TableView,
  requestKey: string
): OpenBody {
  if (openBlockReason(shopId, slot, table) || !isRequestKey(requestKey)) {
    throw new Error('open rejected')
  }
  return {
    slotId: slot.slotId,
    signCode: table.signCode,
    requestKey
  }
}

export function openScope(shopId: number, slotId: number, signCode: string): string {
  return `open:${shopId}:${slotId}:${signCode}`
}

export interface SeatResult {
  holdId?: number
  shopId?: number
  slotId?: number
  status?: string
  lateAutoCancels?: boolean
}

export function acceptSeat(result: SeatResult | null | undefined, shopId: number, hold: HoldView) {
  if (!result || result.shopId !== shopId || result.holdId !== hold.id || result.slotId !== hold.slotId) {
    return { ok: false as const, reason: '入座结果与本店预约不一致' }
  }
  if (result.status !== 'SEATED') {
    return { ok: false as const, reason: '入座结果不是已入座' }
  }
  return {
    ok: true as const,
    autoCancelClaimed: result.lateAutoCancels === true
  }
}

export interface SessionResult {
  sessionId?: number
  shopId?: number
  slotId?: number
  status?: string
  payableFen?: number | null
  paid?: boolean
  zeroPayReleased?: boolean
  paymentSuccess?: boolean | null
}

export function acceptOpen(result: SessionResult | null | undefined, shopId: number, slotId: number) {
  if (!result || result.shopId !== shopId || result.slotId !== slotId || !positive(result.sessionId)) {
    return { ok: false as const, reason: '开桌结果与本店所选时段不一致' }
  }
  if (result.paid === true || result.paymentSuccess === true) {
    return { ok: false as const, reason: '开桌结果带了已支付标记，本页不会把它当成已付款' }
  }
  if (result.zeroPayReleased === true || result.status !== 'OPEN') {
    return { ok: false as const, reason: '开桌结果不是进行中的桌次' }
  }
  return {
    ok: true as const,
    sessionId: result.sessionId as number,
    payableFen: typeof result.payableFen === 'number' ? result.payableFen : null
  }
}

export function holdStatusText(hold: HoldView): string {
  if (hold.status === 'HELD') {
    return '未入座'
  }
  if (hold.status === 'SEATED' && positive(hold.sessionId)) {
    return `已入座，桌次 ${hold.sessionId}`
  }
  if (hold.status === 'SEATED') {
    return '已入座，尚未开桌次'
  }
  return '未知状态'
}

function positive(raw: unknown): raw is number {
  return typeof raw === 'number' && Number.isSafeInteger(raw) && raw > 0
}

function cryptoByte(): number {
  const bytes = new Uint8Array(1)
  crypto.getRandomValues(bytes)
  return bytes[0]
}
