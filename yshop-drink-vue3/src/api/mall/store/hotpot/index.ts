import request from '@/config/axios'

export interface HotpotSlotVO {
  slotId: number
  shopId: number
  capacity: number
  occupied: number
}

export interface HotpotHoldVO {
  id: number
  slotId: number
  shopId: number
  uid: number
  status: string
  sessionId?: number | null
}

export interface HotpotTableVO {
  tableId: number
  shopId: number
  signCode: string
  occupied: boolean
}

export interface HotpotSeatResultVO {
  holdId: number
  shopId: number
  slotId: number
  status: string
  requestKey?: string
  lateAutoCancels?: boolean
}

export interface HotpotSessionResultVO {
  sessionId: number
  shopId: number
  slotId: number
  version?: number
  payableFen?: number | null
  status: string
  paid?: boolean
  zeroPayReleased?: boolean
  paymentSuccess?: boolean | null
  disposition?: string | null
}

export interface HotpotSeatReq {
  holdId: number
  slotId: number
  requestKey: string
}

export interface HotpotOpenReq {
  slotId: number
  signCode: string
  requestKey: string
}

export const listSlots = async () => {
  return await request.get<HotpotSlotVO[]>({ url: '/store/hotpot/slot' })
}

export const listHolds = async () => {
  return await request.get<HotpotHoldVO[]>({ url: '/store/hotpot/hold' })
}

export const listTables = async () => {
  return await request.get<HotpotTableVO[]>({ url: '/store/hotpot/table' })
}

export const seatHold = async (data: HotpotSeatReq) => {
  return await request.post<HotpotSeatResultVO>({ url: '/store/hotpot/seat', data })
}

export const openWalkIn = async (data: HotpotOpenReq) => {
  return await request.post<HotpotSessionResultVO>({ url: '/store/hotpot/session/open', data })
}
