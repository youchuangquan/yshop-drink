/**
 * 预约接待与散客开桌。
 * 店号来自登录态，报文里不带门店编号，也不带金额或支付渠道。
 * POST /store/hotpot/seat 只把 HELD 改成 SEATED，不分配桌台，也不开桌次。
 * 预约安排到空桌走 POST /store/hotpot/reservation/seat。
 * 顾客编号为 0 的散客仍走 POST /store/hotpot/session/open。
 * 时段结束时刻已过时只在本页提醒，不取消，也不释放名额。
 */

const KEY_PATTERN = /^[A-Za-z0-9_-]{8,64}$/
const KEY_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-'
const SLOT_CLOCK = /^([01]\d|2[0-3]):[0-5]\d:[0-5]\d$/

export interface SlotView {
  slotId: number
  shopId: number
  capacity: number
  occupied: number
  startTime?: string | null
  endTime?: string | null
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

export interface ReservationSeatBody {
  holdId: number
  slotId: number
  signCode: string
  requestKey: string
}

export interface OpenBody {
  slotId: number
  signCode: string
  requestKey: string
}

export interface SlotWindow {
  startTime: string
  endTime: string
}

export interface PassedSlotReminder {
  passed: true
  startTime: string
  endTime: string
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
 * 只认成对的 HH:mm:ss。缺一边、或不是这个格式，就不当作时段时刻。
 */
export function slotWindow(slot: { startTime?: unknown; endTime?: unknown } | null | undefined): SlotWindow | null {
  if (!slot) {
    return null
  }
  if (typeof slot.startTime !== 'string' || typeof slot.endTime !== 'string') {
    return null
  }
  if (!SLOT_CLOCK.test(slot.startTime) || !SLOT_CLOCK.test(slot.endTime)) {
    return null
  }
  return { startTime: slot.startTime, endTime: slot.endTime }
}

export function slotClockText(slot: { startTime?: unknown; endTime?: unknown } | null | undefined): string {
  const window = slotWindow(slot)
  if (!window) {
    return ''
  }
  return `${window.startTime} 至 ${window.endTime}`
}

/**
 * 结束时刻已过只作提醒。不接收停留时长，也不看占位上另写的日期。
 * 起止跨过午夜时没有日期，无法判断是未开始还是已结束，只展示时刻，不标。
 * 调用方不传当前时间时不标。
 */
export function lateArrivalFlag(
  slot?: { startTime?: unknown; endTime?: unknown } | null,
  now?: Date
): PassedSlotReminder | null {
  const window = slotWindow(slot)
  if (!window || !(now instanceof Date) || Number.isNaN(now.getTime())) {
    return null
  }
  const start = clockSeconds(window.startTime)
  const end = clockSeconds(window.endTime)
  if (end <= start) {
    return null
  }
  const current = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds()
  if (current > end) {
    return { passed: true, startTime: window.startTime, endTime: window.endTime }
  }
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

export function canAssignTable(hold: HoldView | null | undefined, shopId: number | null): boolean {
  if (shopId == null || !hold) {
    return false
  }
  return (
    hold.shopId === shopId &&
    positive(hold.uid) &&
    (hold.status === 'HELD' || hold.status === 'SEATED') &&
    positive(hold.id) &&
    positive(hold.slotId)
  )
}

export function reservationSeatBlockReason(
  shopId: number | null,
  hold: HoldView | null | undefined,
  table: TableView | null | undefined
): string | null {
  if (shopId == null) {
    return '当前账号没有绑定门店'
  }
  if (!hold || hold.shopId !== shopId) {
    return '请选择本店预约'
  }
  if (!positive(hold.uid)) {
    return '散客请走开桌'
  }
  if (hold.status !== 'HELD' && hold.status !== 'SEATED') {
    return '这条预约不能安排空桌'
  }
  if (!positive(hold.id) || !positive(hold.slotId)) {
    return '这条预约不能安排空桌'
  }
  if (!table || table.shopId !== shopId || table.occupied !== false) {
    return '请选择本店空闲桌台'
  }
  if (typeof table.signCode !== 'string' || table.signCode.trim() === '') {
    return '请选择本店空闲桌台'
  }
  return null
}

export function reservationSeatBody(
  hold: HoldView,
  shopId: number,
  table: TableView,
  requestKey: string
): ReservationSeatBody {
  if (reservationSeatBlockReason(shopId, hold, table) || !isRequestKey(requestKey)) {
    throw new Error('reservation seat rejected')
  }
  return {
    holdId: hold.id,
    slotId: hold.slotId,
    signCode: table.signCode,
    requestKey
  }
}

export function reservationSeatScope(shopId: number, hold: HoldView, signCode: string): string {
  return `reserve:${shopId}:${hold.id}:${hold.slotId}:${signCode}`
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
  holdId?: number | null
  ownerUid?: number | null
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

export function acceptReservationSeat(
  result: SessionResult | null | undefined,
  shopId: number,
  hold: HoldView
) {
  if (
    !result ||
    result.shopId !== shopId ||
    result.slotId !== hold.slotId ||
    result.holdId !== hold.id ||
    result.ownerUid !== hold.uid ||
    !positive(result.ownerUid) ||
    !positive(result.sessionId)
  ) {
    return { ok: false as const, reason: '开桌结果与这条预约不一致' }
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
    ownerUid: result.ownerUid as number,
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

function clockSeconds(hms: string): number {
  const [hour, minute, second] = hms.split(':').map((part) => Number(part))
  return hour * 3600 + minute * 60 + second
}

function cryptoByte(): number {
  const bytes = new Uint8Array(1)
  crypto.getRandomValues(bytes)
  return bytes[0]
}
