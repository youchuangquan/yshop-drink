export function sameOptionId(left, right) {
  return left != null && right != null && String(left) === String(right)
}

export function pricedOptions(options, kind) {
  if (!Array.isArray(options)) {
    return []
  }
  return options.filter((item) => item && item.kind === kind && item.price != null && item.price !== '')
}

export function emptySelection() {
  return { flavorOptionId: null, addons: [] }
}

export function normalizeSelection(raw) {
  const selection = emptySelection()
  if (!raw || typeof raw !== 'object') {
    return selection
  }
  selection.flavorOptionId = raw.flavorOptionId == null || raw.flavorOptionId === '' ? null : raw.flavorOptionId
  const addons = Array.isArray(raw.addons) ? raw.addons : []
  selection.addons = addons
    .filter((item) => item && item.optionId != null && Number(item.number) > 0)
    .map((item) => ({ optionId: item.optionId, number: Number(item.number) }))
  return selection
}

export function pickFlavor(selection, optionId) {
  const next = normalizeSelection(selection)
  next.flavorOptionId = sameOptionId(next.flavorOptionId, optionId) ? null : optionId
  return next
}

export function changeAddon(selection, optionId, delta) {
  const next = normalizeSelection(selection)
  const rows = next.addons.map((item) => ({ ...item }))
  const found = rows.find((item) => sameOptionId(item.optionId, optionId))
  const step = Number(delta)
  if (!Number.isFinite(step) || step === 0) {
    return next
  }
  if (!found) {
    if (step > 0) {
      rows.push({ optionId, number: step })
    }
  } else {
    found.number += step
  }
  next.addons = rows.filter((item) => item.number > 0)
  return next
}

export function addonNumber(selection, optionId) {
  const row = normalizeSelection(selection).addons.find((item) => sameOptionId(item.optionId, optionId))
  return row ? row.number : 0
}

export function queueNumberVisible(numberId) {
  if (numberId == null || numberId === '') {
    return false
  }
  const number = Number(numberId)
  return Number.isFinite(number) && number > 0
}

export function quotedFee(amount) {
  if (amount == null || amount === '') {
    return null
  }
  return amount
}

export function quotePayText(quote) {
  if (!quote || quote.payAmount == null || quote.payAmount === '') {
    return ''
  }
  return String(quote.payAmount)
}

export function addonLines(order) {
  const rows = order && Array.isArray(order.options) ? order.options : []
  return rows.filter((row) => row && row.kind === 'ADDON')
}
