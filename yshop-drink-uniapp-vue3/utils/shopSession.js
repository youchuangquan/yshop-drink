const LEGACY_CART_KEY = 'cart'
const LEGACY_COUPON_KEY = 'mycoupon'
const LEGACY_CREDENTIAL_KEY = 'shopCredential'

export function cartKey(shopId) {
  return `cart:${shopId}`
}

export function couponKey(shopId) {
  return `mycoupon:${shopId}`
}

export function credentialKey(shopId) {
  return `shopCredential:${shopId}`
}

export function draftKey(shopId) {
  return `orderDraft:${shopId}`
}

function shopIdentity(shop) {
  if (shop == null) {
    return ''
  }
  const raw = typeof shop === 'object' ? shop.id : shop
  if (raw == null) {
    return ''
  }
  const text = String(raw).trim()
  if (!text || text === '0') {
    return ''
  }
  return text
}

function readList(storage, key) {
  const value = storage.get(key)
  return Array.isArray(value) ? value : []
}

export function createShopSession(storage) {
  const state = {
    shopId: '',
    cart: [],
    coupon: null,
    credential: null,
    orderDraft: null,
  }

  function clearShop(shopId) {
    storage.remove(cartKey(shopId))
    storage.remove(couponKey(shopId))
    storage.remove(credentialKey(shopId))
    storage.remove(draftKey(shopId))
    storage.remove(LEGACY_CART_KEY)
    storage.remove(LEGACY_COUPON_KEY)
    storage.remove(LEGACY_CREDENTIAL_KEY)
  }

  return {
    state,
    bindShop(shop) {
      const nextId = shopIdentity(shop)
      if (!nextId) {
        return { cleared: false, shopId: state.shopId }
      }
      if (state.shopId && state.shopId !== nextId) {
        const previousShopId = state.shopId
        clearShop(previousShopId)
        state.cart = []
        state.coupon = null
        state.credential = null
        state.orderDraft = null
        state.shopId = nextId
        return { cleared: true, previousShopId, shopId: nextId }
      }
      state.shopId = nextId
      state.cart = readList(storage, cartKey(nextId))
      state.coupon = storage.get(couponKey(nextId)) || null
      state.credential = storage.get(credentialKey(nextId)) || null
      state.orderDraft = storage.get(draftKey(nextId)) || null
      storage.remove(LEGACY_CART_KEY)
      return { cleared: false, shopId: nextId }
    },
    setCart(cart) {
      if (!state.shopId) {
        return false
      }
      const copy = JSON.parse(JSON.stringify(cart || []))
      state.cart = copy
      storage.set(cartKey(state.shopId), copy)
      storage.remove(LEGACY_CART_KEY)
      return true
    },
    readCart(shopId) {
      const id = shopIdentity(shopId)
      if (!id) {
        return []
      }
      return readList(storage, cartKey(id))
    },
    setCoupon(coupon) {
      if (!state.shopId) {
        return false
      }
      state.coupon = coupon || null
      if (coupon) {
        storage.set(couponKey(state.shopId), coupon)
      } else {
        storage.remove(couponKey(state.shopId))
      }
      storage.remove(LEGACY_COUPON_KEY)
      return true
    },
    setCredential(credential) {
      if (!state.shopId) {
        return false
      }
      state.credential = credential || null
      if (credential) {
        storage.set(credentialKey(state.shopId), credential)
      } else {
        storage.remove(credentialKey(state.shopId))
      }
      storage.remove(LEGACY_CREDENTIAL_KEY)
      return true
    },
    setOrderDraft(draft) {
      if (!state.shopId) {
        return false
      }
      state.orderDraft = draft || null
      if (draft) {
        storage.set(draftKey(state.shopId), draft)
      } else {
        storage.remove(draftKey(state.shopId))
      }
      return true
    },
  }
}

export function createMemoryStorage(seed = {}) {
  const data = { ...seed }
  return {
    data,
    get(key) {
      return data[key]
    },
    set(key, value) {
      data[key] = value
    },
    remove(key) {
      delete data[key]
    },
  }
}
