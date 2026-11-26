<template>
	<layout>
		<uv-navbar
		  :fixed="false"
		  title="火锅预约"
		  left-arrow
		  @leftClick="$onClickLeft"
		/>
		<view class="container hotpot-page">
			<view class="bg-white hotpot-page__block">
				<view class="font-size-lg text-color-base" v-if="shopName">{{ shopName }}</view>
				<view class="font-size-lg text-color-base" v-else-if="shopReady(shopId)">门店 {{ shopId }}</view>
				<view class="font-size-base text-color-base" v-else>请先选择门店</view>
				<view class="font-size-sm text-color-assist hotpot-page__gap">预约只占这个时段的名额，不锁定桌号。</view>
				<button v-if="!shopReady(shopId)" class="hotpot-page__action" type="primary" @tap="chooseShop">选择门店</button>
			</view>

			<view class="bg-white hotpot-page__block" v-if="shopReady(shopId) && !isLogin">
				<view class="font-size-base text-color-base">提交预约需要登录</view>
				<button class="hotpot-page__action" type="primary" @tap="goLogin">登录</button>
			</view>

			<view v-if="shopReady(shopId) && isLogin">
				<view class="font-size-sm text-color-assist hotpot-page__hint" v-if="loadState === 'loading'">正在读取时段</view>
				<view class="font-size-sm text-color-assist hotpot-page__hint" v-else-if="loadState === 'failed'">时段没有加载出来</view>
				<view class="font-size-sm text-color-assist hotpot-page__hint" v-else-if="slots.length === 0">这家店还没有可预约的时段</view>
				<view class="bg-white hotpot-page__block" v-for="slot in slots" :key="slot.slotId">
					<view class="font-size-lg text-color-base">时段 {{ slot.slotId }}</view>
					<view class="font-size-sm text-color-assist hotpot-page__gap">容量 {{ slot.capacity }}，已占 {{ slot.occupied }}</view>
					<view class="font-size-base text-color-base hotpot-page__gap" v-if="leftOf(slot) != null && leftOf(slot) >= 0">剩余 {{ leftOf(slot) }}</view>
					<view class="font-size-sm text-color-assist hotpot-page__gap" v-else>剩余名额无法从返回的容量计算</view>
					<button
						class="hotpot-page__action"
						type="primary"
						:disabled="!canReserve(slot, isLogin, shopId) || submitting"
						@tap="submit(slot)"
					>{{ reserveLabel(slot) }}</button>
				</view>
			</view>

			<view class="bg-white hotpot-page__block" v-if="hold && hold.holdId">
				<view class="font-size-lg text-color-base">这次预约</view>
				<view class="font-size-base text-color-base hotpot-page__gap">已占用时段 {{ hold.slotId }} 的名额</view>
				<view class="font-size-sm text-color-assist hotpot-page__gap">没有锁定桌号</view>
				<view class="font-size-sm text-color-assist hotpot-page__gap" v-if="holdStatusText(hold.status)">状态 {{ holdStatusText(hold.status) }}</view>
				<view class="font-size-sm text-color-assist hotpot-page__gap" v-if="hold.lateAutoCancels === false">迟到不会自动取消</view>
				<button
					v-if="hold.status === 'HELD'"
					class="hotpot-page__action"
					type="primary"
					plain
					:disabled="cancelling"
					@tap="cancelHold"
				>取消这次预约</button>
			</view>
		</view>
	</layout>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { storeToRefs } from 'pinia'
import { useMainStore } from '@/store/store'
import { createRequestKey } from '@/utils/request-key'
import {
  shopReady,
  visibleSlots,
  remainingCapacity,
  canReserve,
  reservationBody,
  sameRequest,
  cancelBody,
  holdStatusText,
} from '@/utils/hotpot'
import { hotpotSlots, hotpotReserve, hotpotCancel } from '@/api/hotpot'

const main = useMainStore()
const { isLogin, store } = storeToRefs(main)
const shopId = computed(() => (store.value && shopReady(store.value.id) ? store.value.id : ''))
const shopName = computed(() => (shopId.value && store.value && store.value.name ? store.value.name : ''))
const slots = ref([])
const loadState = ref('idle')
const submitting = ref(false)
const cancelling = ref(false)
const hold = ref(null)
let pending = null

const leftOf = (slot) => remainingCapacity(slot)

const reserveLabel = (slot) => {
  const left = remainingCapacity(slot)
  if (left == null || left < 0) return '无法预约'
  if (left === 0) return '没有剩余名额'
  if (!isLogin.value) return '登录后预约'
  return '预约这个时段'
}

const chooseShop = () => {
  uni.navigateTo({ url: '/pages-checkout/shop/shop' })
}

const goLogin = () => {
  uni.navigateTo({ url: '/pages-user/login/login' })
}

const loadSlots = async () => {
  if (!shopReady(shopId.value) || !isLogin.value) {
    slots.value = []
    loadState.value = 'idle'
    return
  }
  loadState.value = 'loading'
  try {
    const data = await hotpotSlots(shopId.value)
    slots.value = visibleSlots(data, shopId.value)
    loadState.value = 'ready'
  } catch (error) {
    slots.value = []
    loadState.value = 'failed'
  }
}

const submit = async (slot) => {
  if (!isLogin.value || submitting.value) {
    return
  }
  if (!sameRequest(pending, slot.slotId)) {
    pending = { slotId: slot.slotId, key: createRequestKey(), done: false }
  }
  const body = reservationBody(shopId.value, slot, pending.key)
  if (!body) {
    return
  }
  submitting.value = true
  try {
    const data = await hotpotReserve(body)
    if (!data || !data.holdId) {
      return
    }
    hold.value = data
    pending = { ...pending, done: true }
    await loadSlots()
  } catch (error) {
    // 请求层已经提示失败，这里不把预约写成成功
  } finally {
    submitting.value = false
  }
}

const cancelHold = async () => {
  if (!hold.value || hold.value.status !== 'HELD' || cancelling.value) {
    return
  }
  const body = cancelBody(hold.value.holdId)
  if (!body) {
    return
  }
  cancelling.value = true
  try {
    const data = await hotpotCancel(body)
    if (data && data.holdId) {
      hold.value = data
    }
    await loadSlots()
  } catch (error) {
    // 取消失败时保留这次预约的原状态
  } finally {
    cancelling.value = false
  }
}

watch(shopId, () => {
  hold.value = null
  pending = null
})

onShow(() => {
  loadSlots()
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
}
</style>
