<template>
	<layout>
		<uv-navbar
		  :fixed="false"
		  title="桌次账单与结账"
		  left-arrow
		  @leftClick="$onClickLeft"
		/>
		<view class="container hotpot-page">
			<view class="bg-white hotpot-page__block">
				<view class="font-size-lg text-color-base" v-if="shopName">{{ shopName }}</view>
				<view class="font-size-lg text-color-base" v-else-if="shopReady(shopId)">门店 {{ shopId }}</view>
				<view class="font-size-base text-color-base" v-else>请先选择门店</view>
				<view class="font-size-sm text-color-assist hotpot-page__gap">这里只看绑定到你账号的桌次账单。</view>
				<button v-if="!shopReady(shopId)" class="hotpot-page__action" type="primary" @tap="chooseShop">选择门店</button>
			</view>

			<view class="bg-white hotpot-page__block" v-if="shopReady(shopId) && !isLogin">
				<view class="font-size-base text-color-base">查看账单需要登录</view>
				<button class="hotpot-page__action" type="primary" @tap="goLogin">登录</button>
			</view>

			<view class="bg-white hotpot-page__block" v-if="shopReady(shopId) && isLogin">
				<uv-input v-model="signCode" placeholder="输入桌码" border="surround"></uv-input>
				<button class="hotpot-page__action" type="primary" :disabled="loading" @tap="loadBill">查看账单</button>
			</view>

			<view class="font-size-sm text-color-assist hotpot-page__hint" v-if="loading">正在读取账单</view>
			<view class="font-size-sm text-color-assist hotpot-page__hint" v-else-if="loadState === 'failed'">账单没有加载出来</view>

			<view class="bg-white hotpot-page__block" v-if="bill && bill.sessionId">
				<view class="font-size-sm text-color-assist" v-if="loadedCode">桌码 {{ loadedCode }}</view>
				<view class="font-size-lg text-color-base hotpot-page__gap">开台号 {{ bill.sessionId }}</view>
				<view class="font-size-sm text-color-assist hotpot-page__gap" v-if="!checkout">{{ BILL_CHECKOUT_GAP }}</view>
				<view v-else>
					<button class="hotpot-page__action" type="primary" :disabled="checking" @tap="startCheckout">按当前账单结账</button>
					<view class="font-size-sm text-color-assist hotpot-page__gap" v-if="settlement && settlementText(settlement)">{{ settlementText(settlement) }}</view>
					<view class="font-size-sm text-color-base hotpot-page__gap" v-if="settlement && settlement.paid === true">已支付</view>
					<view class="font-size-base text-color-base hotpot-page__gap" v-if="settlement && formatFen(settlement.payableFen)">应付 ￥{{ formatFen(settlement.payableFen) }}</view>
					<view class="hotpot-page__line" v-for="pot in settlementPots" :key="pot.potId || pot.name">
						<view class="font-size-base text-color-base">{{ pot.name }}</view>
						<view class="font-size-sm text-color-assist" v-if="formatFen(pot.priceFen)">￥{{ formatFen(pot.priceFen) }}</view>
					</view>
					<view class="font-size-sm text-color-assist hotpot-page__gap" v-if="checkoutFailed">结账没有完成，账单未支付。</view>
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
import { hotpotBill, hotpotSettle } from '@/api/hotpot'
import {
  shopReady,
  checkoutBody,
  BILL_CHECKOUT_GAP,
  presentSettlement,
  settlementText,
  formatFen,
} from '@/utils/hotpot'

const main = useMainStore()
const { isLogin, store } = storeToRefs(main)
const queryShopId = ref('')
const signCode = ref('')
const bill = ref(null)
const settlement = ref(null)
const loading = ref(false)
const checking = ref(false)
const loadState = ref('idle')
const checkoutFailed = ref(false)
const loadedCode = ref('')

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
const checkout = computed(() => checkoutBody(bill.value))
const settlementPots = computed(() => (settlement.value && Array.isArray(settlement.value.pots) ? settlement.value.pots : []))

const chooseShop = () => {
  uni.navigateTo({ url: '/pages-checkout/shop/shop' })
}

const goLogin = () => {
  uni.navigateTo({ url: '/pages-user/login/login' })
}

const loadBill = async () => {
  const code = signCode.value.trim()
  if (!isLogin.value || !shopReady(shopId.value) || !code || loading.value) {
    return
  }
  loading.value = true
  loadState.value = 'loading'
  bill.value = null
  settlement.value = null
  checkoutFailed.value = false
  loadedCode.value = ''
  try {
    const data = await hotpotBill(shopId.value, code)
    if (signCode.value.trim() !== code) {
      return
    }
    if (!data || data.sessionId == null) {
      loadState.value = 'failed'
      return
    }
    loadedCode.value = code
    bill.value = data
    loadState.value = 'ready'
  } catch (error) {
    if (signCode.value.trim() === code) {
      loadState.value = 'failed'
    }
  } finally {
    loading.value = false
    if (loadState.value === 'loading') {
      loadState.value = 'idle'
    }
  }
}

const startCheckout = async () => {
  const body = checkoutBody(bill.value)
  if (!body || checking.value) {
    return
  }
  checking.value = true
  checkoutFailed.value = false
  settlement.value = null
  try {
    const data = await hotpotSettle(body)
    const view = presentSettlement(data)
    if (!view) {
      checkoutFailed.value = true
      return
    }
    settlement.value = view
  } catch (error) {
    checkoutFailed.value = true
  } finally {
    checking.value = false
  }
}

onLoad((option) => {
  if (option && shopReady(option.shopId)) {
    queryShopId.value = option.shopId
  }
  if (option && option.signCode) {
    signCode.value = option.signCode
  }
})

onShow(() => {
  if (isLogin.value && signCode.value.trim() && shopReady(shopId.value) && !bill.value && !loading.value) {
    loadBill()
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

  &__line {
    display: flex;
    justify-content: space-between;
    margin-top: $spacing-row-base;
  }
}
</style>
