import test from 'node:test'
import assert from 'node:assert/strict'
import {
  pricedOptions,
  pickFlavor,
  changeAddon,
  queueNumberVisible,
  quotedFee,
  quotePayText,
  addonLines,
} from '../utils/malatang.js'

test('flavor and addon choices keep only configured prices', () => {
  const options = [
    { id: 1, kind: 'FLAVOR', name: '麻辣', price: '3.50' },
    { id: 2, kind: 'FLAVOR', name: '未标价', price: null },
    { id: 3, kind: 'ADDON', name: '宽粉', price: 2 },
    { id: 4, kind: 'ADDON', name: '空白', price: '' },
  ]
  assert.deepEqual(pricedOptions(options, 'FLAVOR').map((item) => item.id), [1])
  assert.deepEqual(pricedOptions(options, 'ADDON').map((item) => item.price), [2])
  assert.equal(quotedFee(null), null)
  assert.equal(quotedFee(''), null)
  assert.equal(quotedFee(0), 0)
  assert.equal(quotePayText(null), '')
  assert.equal(quotePayText({ payAmount: null }), '')
  assert.equal(quotePayText({ payAmount: '33.70' }), '33.70')
})

test('queue number stays hidden until one has been issued', () => {
  assert.equal(queueNumberVisible(null), false)
  assert.equal(queueNumberVisible(''), false)
  assert.equal(queueNumberVisible(0), false)
  assert.equal(queueNumberVisible('0'), false)
  assert.equal(queueNumberVisible(12), true)
})

test('flavor toggle and addon quantity do not invent a price', () => {
  const picked = pickFlavor({ flavorOptionId: null, addons: [] }, 5)
  assert.equal(picked.flavorOptionId, 5)
  assert.equal(pickFlavor(picked, 5).flavorOptionId, null)
  const once = changeAddon(picked, 6, 1)
  assert.deepEqual(once.addons, [{ optionId: 6, number: 1 }])
  const twice = changeAddon(once, 6, 1)
  assert.equal(twice.addons[0].number, 2)
  assert.equal('price' in twice.addons[0], false)
  assert.deepEqual(addonLines({ options: [{ kind: 'ADDON', name: '宽粉', amount: '4.00' }, { kind: 'FLAVOR' }] }), [
    { kind: 'ADDON', name: '宽粉', amount: '4.00' },
  ])
})
