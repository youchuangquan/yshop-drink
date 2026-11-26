import api from './api'

export function hotpotSlots(shopId) {
  return api.get('/hotpot/slot', { shopId }, { login: true })
}

export function hotpotReserve(data) {
  return api.post('/hotpot/reservation', data, { login: true })
}

export function hotpotCancel(data) {
  return api.post('/hotpot/reservation/cancel', data, { login: true })
}

export function hotpotScan(shopId, signCode) {
  return api.get('/hotpot/scan', { shopId, signCode }, { login: false })
}

export function hotpotDish(data) {
  return api.post('/hotpot/session/dish', data, { login: true })
}

export function hotpotBill(shopId, signCode) {
  return api.get('/hotpot/bill', { shopId, signCode }, { login: true })
}

export function hotpotSettle(data) {
  return api.post('/hotpot/session/settle', data, { login: true })
}
