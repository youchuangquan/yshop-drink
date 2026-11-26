<template>
	<layout>
		<uv-navbar
		  :fixed="false"
		  title="扫码开桌"
		  left-arrow
		  @leftClick="$onClickLeft"
		/>
		<view class="container hotpot-page">
			<view class="bg-white hotpot-page__block">
				<view class="font-size-lg text-color-base" v-if="shopName">{{ shopName }}</view>
				<view class="font-size-lg text-color-base" v-else-if="shopReady(shopId)">门店 {{ shopId }}</view>
				<view class="font-size-base text-color-base" v-else>请先选择门店，或扫描带有门店的桌码</view>
				<view class="font-size-sm text-color-assist hotpot-page__gap">空闲桌只显示桌号和菜单。这次扫码不占桌。</view>
				<button v-if="!shopReady(shopId)" class="hotpot-page__action" type="primary" @tap="chooseShop">选择门店</button>
				<button class="hotpot-page__action" type="primary" :disabled="loading" @tap="scanCode">扫桌码</button>
				<view class="hotpot-page__gap">
					<uv-input v-model="typedCode" placeholder="输入桌码" border="surround"></uv-input>
				</view>
				<button class="hotpot-page__action" type="primary" plain :disabled="loading" @tap="lookUpTyped">查看这张桌</button>
			</view>

			<view class="font-size-sm text-color-assist hotpot-page__hint" v-if="loading">正在读取桌码</view>
			<view class="font-size-sm text-color-assist hotpot-page__hint" v-else-if="table.kind === 'invalid'">桌码没有加载出来</view>

			<view class="bg-white hotpot-page__block" v-if="table.kind === 'idle' || table.kind === 'occupied'">
				<view class="font-size-lg text-color-base">桌号 {{ table.signCode }}</view>
				<view class="font-size-sm text-color-assist hotpot-page__gap" v-if="table.kind === 'idle' && !opened">这张桌现在空闲。只显示桌号和菜单，不占桌。</view>
				<view class="font-size-sm text-color-assist hotpot-page__gap" v-else-if="opened">第一道菜已下单，开台号 {{ opened.sessionId }}，版本 {{ opened.version }}。继续点菜会带上这个版本。</view>
				<view class="font-size-sm text-color-assist hotpot-page__gap" v-else>这张桌已经占用。这次扫码不会再开一桌。</view>
				<view class="font-size-base text-color-base hotpot-page__gap" v-if="opened && formatFen(opened.payableFen)">当前应付 ￥{{ formatFen(opened.payableFen) }}</view>
				<button v-if="table.kind === 'occupied' || opened" class="hotpot-page__action" type="primary" plain @tap="openBill">查看我的桌次账单</button>
			</view>

			<view class="bg-white hotpot-page__block" v-if="table.kind === 'idle' && !isLogin">
				<view class="font-size-base text-color-base">点菜需要登录。扫码本身不占桌。</view>
				<button class="hotpot-page__action" type="primary" @tap="goLogin">登录</button>
			</view>

			<view v-if="table.kind === 'idle' && isLogin">
				<view class="font-size-sm text-color-assist hotpot-page__hint" v-if="slotState === 'loading'">正在读取时段</view>
				<view class="font-size-sm text-color-assist hotpot-page__hint" v-else-if="slotState === 'failed'">时段没有加载出来。没有已有时段就不能点菜。</view>
				<view class="font-size-sm text-color-assist hotpot-page__hint" v-else-if="slotState === 'ready' && slots.length === 0">这家店还没有时段。点菜需要已有的时段，这里不会新建。</view>
				<view class="bg-white hotpot-page__block" v-if="usingReservation && selectedSlot">
					<view class="font-size-lg text-color-base">使用已预约的时段 {{ selectedSlot.slotId }}</view>
					<view class="font-size-sm text-color-assist hotpot-page__gap" v-if="slotClock(selectedSlot.startTime) || slotClock(selectedSlot.endTime)">
						<text v-if="slotClock(selectedSlot.startTime)">开始 {{ slotClock(selectedSlot.startTime) }}</text>
						<text v-if="slotClock(selectedSlot.endTime)"> 结束 {{ slotClock(selectedSlot.endTime) }}</text>
					</view>
					<view class="font-size-sm text-color-assist hotpot-page__gap">点菜挂在这个时段上，不会再占一个名额。</view>
				</view>
				<view v-else-if="slotState === 'ready' && slots.length > 0">
					<view class="font-size-sm text-color-assist hotpot-page__hint" v-if="!selectedSlot">先从这家店已有的时段里选一个，才能点菜。</view>
					<view class="bg-white hotpot-page__block" v-for="slot in slots" :key="slot.slotId">
						<view class="font-size-lg text-color-base">时段 {{ slot.slotId }}</view>
						<view class="font-size-sm text-color-assist hotpot-page__gap">容量 {{ slot.capacity }}，已占 {{ slot.occupied }}</view>
						<view class="font-size-sm text-color-assist hotpot-page__gap" v-if="slotClock(slot.startTime) || slotClock(slot.endTime)">
							<text v-if="slotClock(slot.startTime)">开始 {{ slotClock(slot.startTime) }}</text>
							<text v-if="slotClock(slot.endTime)"> 结束 {{ slotClock(slot.endTime) }}</text>
						</view>
						<view class="font-size-base text-color-base hotpot-page__gap" v-if="leftOf(slot) != null">剩余 {{ leftOf(slot) }}</view>
						<button class="hotpot-page__action" type="primary" :plain="!isSelected(slot)" @tap="chooseSlot(slot)">{{ isSelected(slot) ? '用这个时段点菜' : '选这个时段' }}</button>
					</view>
				</view>
			</view>

			<view v-if="table.kind === 'idle'">
				<view class="font-size-lg text-color-base hotpot-page__hint">菜单</view>
				<view class="font-size-sm text-color-assist hotpot-page__hint" v-if="orderNote">{{ orderNote }}</view>
				<view class="font-size-sm text-color-assist hotpot-page__hint" v-if="menuState === 'loading'">正在读取菜单</view>
				<view class="font-size-sm text-color-assist hotpot-page__hint" v-else-if="menuState === 'failed'">菜单没有加载出来</view>
				<view class="font-size-sm text-color-assist hotpot-page__hint" v-else-if="menuState === 'ready' && menu.length === 0">菜单是空的</view>
				<view class="bg-white hotpot-page__block" v-for="category in menu" :key="category.id">
					<view class="font-size-base text-color-base">{{ category.name }}</view>
					<view class="hotpot-page__good" v-for="good in category.goods" :key="good.id">
						<image v-if="good.image" class="hotpot-page__thumb" :src="good.image" mode="aspectFill"></image>
						<view class="hotpot-page__good-copy">
							<view class="font-size-base text-color-base">{{ good.name }}</view>
							<view class="font-size-sm text-color-assist hotpot-page__gap" v-if="good.info">{{ good.info }}</view>
							<view class="font-size-base text-color-base hotpot-page__gap" v-if="good.price != null">￥{{ good.price }}</view>
							<view v-if="isLogin">
								<view class="font-size-sm text-color-assist hotpot-page__gap">份数 {{ quantityOf(good.id) }}</view>
								<view class="hotpot-page__qty">
									<button class="hotpot-page__qty-btn" type="primary" plain :disabled="ordering || quantityOf(good.id) <= 1" @tap="changeQuantity(good.id, -1)">少一份</button>
									<button class="hotpot-page__qty-btn hotpot-page__qty-btn--last" type="primary" plain :disabled="ordering" @tap="changeQuantity(good.id, 1)">多一份</button>
								</view>
								<button class="hotpot-page__action" type="primary" :disabled="!canOrder || ordering" @tap="placeDish(good)">点这道菜</button>
							</view>
						</view>
					</view>
				</view>
			</view>
		</view>
	</layout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { useMainStore } from '@/store/store'
import { menuGoods } from '@/api/goods'
import { hotpotDish, hotpotScan, hotpotSlots } from '@/api/hotpot'
import { createRequestKey } from '@/utils/request-key'
import {
  shopReady,
  idReady,
  readScan,
  scanState,
  menuRows,
  visibleSlots,
  remainingCapacity,
  readHold,
  reservedSlot,
  slotClock,
  dishBody,
  sameDishAttempt,
  openedSession,
  formatFen,
  rememberHold,
} from '@/utils/hotpot'

const main = useMainStore()
const { isLogin, store } = storeToRefs(main)
const queryShopId = ref('')
const typedCode = ref('')
const loading = ref(false)
const menuState = ref('idle')
const menu = ref([])
const table = ref({ kind: '', showMenu: false, occupy: false })
const slots = ref([])
const slotState = ref('idle')
const selectedSlot = ref(null)
const usingReservation = ref(false)
const quantities = ref({})
const ordering = ref(false)
const orderNote = ref('')
const opened = ref(null)
let pendingDish = null
let slotToken = 0
let orderToken = 0

const shopId = computed(() => {
  if (shopReady(queryShopId.value)) return queryShopId.value
  if (store.value && shopReady(store.value.id)) return store.value.id
  return ''
})
const shopName = computed(() => {
  if (!store.value || !store.value.name || !shopReady(store.value.id)) return ''
  if (String(store.value.id) !== String(shopId.value)) return ''
  return store.value.name
})

const canOrder = computed(() => {
  if (!isLogin.value || ordering.value || table.value.kind !== 'idle') return false
  if (!table.value.signCode || !shopReady(shopId.value)) return false
  if (!selectedSlot.value || !idReady(selectedSlot.value.slotId)) return false
  if (opened.value && (typeof opened.value.version !== 'number' || !Number.isInteger(opened.value.version))) return false
  return true
})

const chooseShop = () => {
  uni.navigateTo({ url: '/pages-checkout/shop/shop' })
}

const goLogin = () => {
  uni.navigateTo({ url: '/pages-user/login/login' })
}

function pageStorage() {
  return {
    get(key) {
      return uni.getStorageSync(key)
    },
    set(key, value) {
      uni.setStorageSync(key, value)
    },
    remove(key) {
      uni.removeStorageSync(key)
    },
  }
}

const leftOf = (slot) => remainingCapacity(slot)

const isSelected = (slot) => selectedSlot.value && String(selectedSlot.value.slotId) === String(slot.slotId)

const chooseSlot = (slot) => {
  if (usingReservation.value || !slot || !idReady(slot.slotId)) return
  selectedSlot.value = slot
}

const quantityOf = (productId) => {
  const value = quantities.value[productId]
  return typeof value === 'number' && Number.isInteger(value) && value >= 1 ? value : 1
}

const changeQuantity = (productId, delta) => {
  if (ordering.value) return
  const next = quantityOf(productId) + delta
  if (!Number.isInteger(next) || next < 1) return
  quantities.value = { ...quantities.value, [productId]: next }
}

const resetOrder = () => {
  slots.value = []
  slotState.value = 'idle'
  selectedSlot.value = null
  usingReservation.value = false
  quantities.value = {}
  orderNote.value = ''
  ordering.value = false
  opened.value = null
  pendingDish = null
  slotToken += 1
  orderToken += 1
}

const loadSlots = async () => {
  const token = ++slotToken
  const shop = shopId.value
  if (!isLogin.value || !shopReady(shop) || table.value.kind !== 'idle') {
    slots.value = []
    slotState.value = 'idle'
    selectedSlot.value = null
    usingReservation.value = false
    return
  }
  slotState.value = 'loading'
  try {
    const data = await hotpotSlots(shop)
    if (token !== slotToken || String(shopId.value) !== String(shop) || table.value.kind !== 'idle') return
    const rows = visibleSlots(data, shop)
    slots.value = rows
    const reserved = reservedSlot(rows, shop, readHold(pageStorage(), shop))
    if (reserved) {
      selectedSlot.value = reserved
      usingReservation.value = true
    } else {
      usingReservation.value = false
      const current = selectedSlot.value
      const still = current && rows.find((slot) => String(slot.slotId) === String(current.slotId))
      selectedSlot.value = still || null
    }
    slotState.value = 'ready'
  } catch (error) {
    if (token !== slotToken) return
    slots.value = []
    selectedSlot.value = null
    usingReservation.value = false
    slotState.value = 'failed'
  }
}

const applyScan = async (raw) => {
  const parsed = readScan(raw, shopId.value)
  if (!parsed) {
    table.value = { kind: 'invalid', showMenu: false, occupy: false }
    menu.value = []
    menuState.value = 'idle'
    resetOrder()
    return
  }
  if (shopReady(parsed.shopId)) {
    queryShopId.value = parsed.shopId
  }
  loading.value = true
  menuState.value = 'idle'
  menu.value = []
  table.value = { kind: '', showMenu: false, occupy: false }
  resetOrder()
  try {
    const data = await hotpotScan(parsed.shopId, parsed.signCode)
    const state = scanState(data)
    table.value = state
    loading.value = false
    if (state.kind === 'idle' && state.showMenu) {
      menuState.value = 'loading'
      try {
        const rows = await menuGoods({ shopId: parsed.shopId })
        menu.value = menuRows(rows)
        menuState.value = 'ready'
      } catch (error) {
        menuState.value = 'failed'
      }
      await loadSlots()
    }
  } catch (error) {
    table.value = { kind: 'invalid', showMenu: false, occupy: false }
    menuState.value = 'idle'
  } finally {
    loading.value = false
  }
}

const scanCode = () => {
  uni.scanCode({
    onlyFromCamera: false,
    success: (res) => {
      applyScan(res && res.result)
    },
    fail: () => {
      // 取消扫码不改已经看到的桌，也不占桌
    },
  })
}

const lookUpTyped = () => {
  const code = typedCode.value.trim()
  if (!code) {
    return
  }
  applyScan(code)
}

const openBill = () => {
  if (!table.value.signCode || !shopReady(shopId.value)) {
    return
  }
  if (table.value.kind !== 'occupied' && !opened.value) {
    return
  }
  uni.navigateTo({
    url: `/pages-hotpot/bill/bill?shopId=${encodeURIComponent(shopId.value)}&signCode=${encodeURIComponent(table.value.signCode)}`,
  })
}

const placeDish = async (good) => {
  if (!canOrder.value || !good || ordering.value) {
    return
  }
  const spec = {
    productId: good.id,
    quantity: quantityOf(good.id),
    slotId: selectedSlot.value.slotId,
    signCode: table.value.signCode,
    version: opened.value ? opened.value.version : null,
  }
  if (!sameDishAttempt(pendingDish, spec)) {
    pendingDish = { ...spec, key: createRequestKey(), done: false }
  }
  const body = dishBody({
    shopId: shopId.value,
    slotId: spec.slotId,
    signCode: spec.signCode,
    productId: spec.productId,
    quantity: spec.quantity,
    requestKey: pendingDish.key,
    version: spec.version,
    open: opened.value != null,
  })
  if (!body) {
    orderNote.value = '这道菜还不能下单。'
    return
  }
  const hadSession = opened.value != null
  const token = orderToken
  const orderedSign = table.value.signCode
  ordering.value = true
  orderNote.value = ''
  try {
    const data = await hotpotDish(body)
    if (token !== orderToken || table.value.signCode !== orderedSign || table.value.kind !== 'idle') {
      return
    }
    const session = openedSession(data)
    if (!session) {
      orderNote.value = hadSession ? '这道菜没有加上。' : '这道菜没有下单。扫码没有占桌。'
      return
    }
    opened.value = session
    pendingDish = { ...pendingDish, done: true, version: session.version }
    orderNote.value = ''
    if (!hadSession && shopReady(shopId.value)) {
      rememberHold(pageStorage(), shopId.value, null)
    }
    if (idReady(data.slotId)) {
      const match = slots.value.find((slot) => String(slot.slotId) === String(data.slotId))
      if (match) selectedSlot.value = match
    }
  } catch (error) {
    if (token !== orderToken || table.value.signCode !== orderedSign) {
      return
    }
    orderNote.value = hadSession ? '这道菜没有加上。' : '这道菜没有下单。扫码没有占桌。'
  } finally {
    if (token === orderToken) {
      ordering.value = false
    }
  }
}

onShow(() => {
  if (table.value.kind === 'idle' && !opened.value) {
    loadSlots()
  }
})

onLoad((option) => {
  if (option && shopReady(option.shopId)) {
    queryShopId.value = option.shopId
  }
  if (option && option.signCode) {
    typedCode.value = option.signCode
    const shop = shopReady(queryShopId.value) ? queryShopId.value : ''
    const raw = shop
      ? `signCode=${encodeURIComponent(option.signCode)}&shopId=${encodeURIComponent(shop)}`
      : option.signCode
    applyScan(raw)
  }
})
</script>

<style lang="scss" scoped>
.hotpot-page {
  padding: $spacing-row-base;

  &__block {
    margin-bottom: $spacing-row-base;
    padding: $spacing-row-lg;
    border-radius: $border-radius-lg;
  }

  &__gap {
    margin-top: $spacing-row-base;
  }

  &__hint {
    margin-bottom: $spacing-row-base;
  }

  &__action {
    margin-top: $spacing-row-lg;
  }

  &__good {
    display: flex;
    margin-top: $spacing-row-lg;
  }

  &__thumb {
    flex-shrink: 0;
    width: 160rpx;
    height: 160rpx;
    margin-right: $spacing-row-lg;
    border-radius: $border-radius-base;
  }

  &__good-copy {
    flex: 1;
  }

  &__qty {
    display: flex;
    margin-top: $spacing-row-base;
  }

  &__qty-btn {
    flex: 1;
    margin-right: $spacing-row-base;

    &--last {
      margin-right: 0;
    }
  }
}
</style>
