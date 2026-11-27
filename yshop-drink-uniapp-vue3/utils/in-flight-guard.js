/**
 * 第一次带 requestKey 的下单请求还没回来时，不要再发第二次。
 * 尾随防抖只推迟点击，盖不住请求还在飞的这段时间。
 */
export function createInFlightGuard() {
	let pending = false
	return {
		pending() {
			return pending
		},
		tryEnter() {
			if (pending) {
				return false
			}
			pending = true
			return true
		},
		leave() {
			pending = false
		},
		tap(schedule) {
			if (pending) {
				return false
			}
			schedule()
			return true
		}
	}
}
