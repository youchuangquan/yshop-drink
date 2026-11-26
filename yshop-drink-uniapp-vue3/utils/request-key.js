/**
 * 同一次下单重试共用这一个键。服务端用它避免再插一笔订单。
 * 不带这个键的旧请求仍能下单，但那不是防重。
 */
export function createRequestKey() {
	const bytes = new Uint8Array(16)
	const cryptoObj = typeof globalThis !== 'undefined' ? globalThis.crypto : undefined
	if (cryptoObj && cryptoObj.getRandomValues) {
		cryptoObj.getRandomValues(bytes)
	} else {
		for (let i = 0; i < bytes.length; i++) {
			bytes[i] = Math.floor(Math.random() * 256)
		}
	}
	return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}
