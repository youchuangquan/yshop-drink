import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import debounce from '../uni_modules/uv-ui-tools/libs/function/debounce.js'
import { createInFlightGuard } from '../utils/in-flight-guard.js'

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function deferred() {
	let resolve
	const promise = new Promise((done) => {
		resolve = done
	})
	return { promise, resolve }
}

test('checkout pay button does not send the same request key again while the first order is in flight', async () => {
	const guard = createInFlightGuard()
	const sent = []
	let requestKey = ''
	const flight = deferred()
	const pay = async () => {
		if (!guard.tryEnter()) {
			return
		}
		try {
			const key = requestKey || (requestKey = 'checkout-key')
			sent.push(key)
			const order = await flight.promise
			if (order) {
				requestKey = ''
			}
		} finally {
			guard.leave()
		}
	}
	const tapCheckoutPay = () => guard.tap(() => debounce(pay, 40))
	const tapConfirmPay = () => guard.tap(() => debounce(pay, 40))

	assert.equal(tapCheckoutPay(), true)
	await delay(15)
	assert.equal(tapConfirmPay(), true)
	await delay(100)
	assert.deepEqual(sent, ['checkout-key'])

	assert.equal(tapCheckoutPay(), false)
	assert.equal(tapConfirmPay(), false)
	await delay(80)
	assert.deepEqual(sent, ['checkout-key'])
	assert.equal(guard.pending(), true)

	flight.resolve(null)
	await delay(0)
	assert.equal(guard.pending(), false)
	assert.equal(tapCheckoutPay(), true)
	await delay(50)
	assert.deepEqual(sent, ['checkout-key', 'checkout-key'])
})

test('score exchange submit drops a second call while the first request is in flight', async () => {
	const guard = createInFlightGuard()
	const sent = []
	let requestKey = ''
	const flight = deferred()
	const submit = async () => {
		if (!guard.tryEnter()) {
			return false
		}
		try {
			const key = requestKey || (requestKey = 'exchange-key')
			sent.push(key)
			await flight.promise
			return true
		} finally {
			guard.leave()
		}
	}

	const first = submit()
	const second = await submit()
	assert.equal(second, false)
	assert.deepEqual(sent, ['exchange-key'])
	flight.resolve()
	assert.equal(await first, true)
	assert.equal(guard.pending(), false)
	assert.equal(await submit(), true)
	assert.deepEqual(sent, ['exchange-key', 'exchange-key'])
})

test('a thrown order submit releases the guard', async () => {
	const guard = createInFlightGuard()
	const pay = async () => {
		if (!guard.tryEnter()) {
			return
		}
		try {
			throw new Error('network')
		} finally {
			guard.leave()
		}
	}
	await assert.rejects(pay, /network/)
	assert.equal(guard.pending(), false)
	assert.equal(guard.tryEnter(), true)
	guard.leave()
})

test('payment and score pages keep the in-flight guard and the zero-amount balance path', () => {
	const pay = readFileSync(new URL('../pages-checkout/pay/pay.vue', import.meta.url), 'utf8')
	const confirm = readFileSync(new URL('../pages-score/scoreproduct/confirm.vue', import.meta.url), 'utf8')
	assert.match(pay, /@tap="tapCheckoutPay"/)
	assert.match(pay, /@tap="tapConfirmPay"/)
	assert.match(pay, /checkoutGuard\.tap\(\(\) => debounce\(submit, 500\)\)/)
	assert.match(pay, /checkoutGuard\.tap\(\(\) => debounce\(pay, 500\)\)/)
	assert.match(pay, /if \(!checkoutGuard\.tryEnter\(\)\)/)
	assert.match(pay, /Number\(quote\.value\.payAmount\) === 0/)
	assert.match(pay, /payType\.value = 'yue'/)
	assert.match(pay, /订单金额为0自动走余额支付/)
	assert.doesNotMatch(pay, /@tap="debounce\(/)
	assert.match(confirm, /if \(!exchangeGuard\.tryEnter\(\)\)/)
	assert.match(confirm, /requestKey: exchangeRequestKey/)
})
