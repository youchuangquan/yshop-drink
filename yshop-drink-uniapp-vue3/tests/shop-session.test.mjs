import test from 'node:test'
import assert from 'node:assert/strict'
import { createMemoryStorage, createShopSession, cartKey, couponKey, credentialKey } from '../utils/shopSession.js'

test('switching shops clears the previous cart, coupon, credential and legacy cache', () => {
  const storage = createMemoryStorage({
    cart: [{ id: 9, number: 1, name: 'legacy' }],
  })
  const session = createShopSession(storage)
  const first = session.bindShop({ id: 1, name: '火锅店' })
  assert.equal(first.cleared, false)
  assert.equal(storage.get('cart'), undefined)

  session.setCart([{ id: 11, number: 2, name: '牛油锅底' }])
  session.setCoupon({ id: 7, shopId: '1', value: 5 })
  session.setCredential('shop-1-session')
  session.setOrderDraft({ remark: '少辣' })

  const switched = session.bindShop({ id: 2, name: '麻辣烫店' })
  assert.equal(switched.cleared, true)
  assert.equal(switched.previousShopId, '1')
  assert.deepEqual(session.state.cart, [])
  assert.equal(session.state.coupon, null)
  assert.equal(session.state.credential, null)
  assert.equal(session.state.orderDraft, null)
  assert.equal(storage.get(cartKey(1)), undefined)
  assert.equal(storage.get(couponKey(1)), undefined)
  assert.equal(storage.get(credentialKey(1)), undefined)
  assert.equal(storage.get('cart'), undefined)
  assert.equal(storage.get('mycoupon'), undefined)
  assert.equal(storage.get('shopCredential'), undefined)
  assert.deepEqual(session.readCart(2), [])
  assert.deepEqual(session.readCart(1), [])
})

test('same shop keeps its own cart and does not read another shop', () => {
  const storage = createMemoryStorage()
  const hotpot = createShopSession(storage)
  hotpot.bindShop({ id: 1 })
  hotpot.setCart([{ id: 1, number: 1 }])

  const mala = createShopSession(storage)
  mala.bindShop({ id: 2 })
  mala.setCart([{ id: 2, number: 3 }])

  const again = createShopSession(storage)
  const bound = again.bindShop({ id: 1 })
  assert.equal(bound.cleared, false)
  assert.deepEqual(again.state.cart, [{ id: 1, number: 1 }])
  assert.deepEqual(again.readCart(2), [{ id: 2, number: 3 }])
  assert.notDeepEqual(again.readCart(1), again.readCart(2))
})

test('cart is not written when no shop is bound', () => {
  const storage = createMemoryStorage()
  const session = createShopSession(storage)
  assert.equal(session.setCart([{ id: 1 }]), false)
  assert.equal(storage.get('cart'), undefined)
  assert.deepEqual(session.readCart(0), [])
})
