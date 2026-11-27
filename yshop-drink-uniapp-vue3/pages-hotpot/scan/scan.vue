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
				<view class="font-size-sm text-color-assist hotpot-page__gap" v-if="table.kind === 'idle'">这张桌现在空闲。只显示桌号和菜单，不占桌。</view>
				<view class="font-size-sm text-color-assist hotpot-page__gap" v-else>这张桌已经占用。这次扫码不会再开一桌。</view>
				<button v-if="table.kind === 'occupied'" class="hotpot-page__action" type="primary" plain @tap="openBill">查看我的桌次账单</button>
			</view>

			<view v-if="table.kind === 'idle'">
				<view class="font-size-lg text-color-base hotpot-page__hint">菜单</view>
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
						</view>
					</view>
				</view>
			</view>
		</view>
	</layout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { useMainStore } from '@/store/store'
import { menuGoods } from '@/api/goods'
import { hotpotScan } from '@/api/hotpot'
import { shopReady, readScan, scanState, menuRows } from '@/utils/hotpot'

const main = useMainStore()
const { store } = storeToRefs(main)
const queryShopId = ref('')
const typedCode = ref('')
const loading = ref(false)
const menuState = ref('idle')
const menu = ref([])
const table = ref({ kind: '', showMenu: false, occupy: false })

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

const chooseShop = () => {
  uni.navigateTo({ url: '/pages-checkout/shop/shop' })
}

const applyScan = async (raw) => {
  const parsed = readScan(raw, shopId.value)
  if (!parsed) {
    table.value = { kind: 'invalid', showMenu: false, occupy: false }
    menu.value = []
    menuState.value = 'idle'
    return
  }
  if (shopReady(parsed.shopId)) {
    queryShopId.value = parsed.shopId
  }
  loading.value = true
  menuState.value = 'idle'
  menu.value = []
  table.value = { kind: '', showMenu: false, occupy: false }
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
  if (table.value.kind !== 'occupied' || !table.value.signCode || !shopReady(shopId.value)) {
    return
  }
  uni.navigateTo({
    url: `/pages-hotpot/bill/bill?shopId=${encodeURIComponent(shopId.value)}&signCode=${encodeURIComponent(table.value.signCode)}`,
  })
}

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
}
</style>
