<template>
  <ContentWrap title="预约接待与开桌">
    <el-alert
      v-if="shopId == null"
      title="当前账号没有绑定门店"
      type="error"
      :closable="false"
      show-icon
      description="预约接待和开桌只使用登录态上的本店。这里不能选择其他门店。"
    />
    <template v-else>
      <el-form :inline="true" class="-mb-15px">
        <el-form-item label="本店编号">
          <el-input :model-value="String(shopId)" class="!w-240px" disabled />
        </el-form-item>
        <el-form-item>
          <el-button @click="reload" :loading="loading">
            <Icon icon="ep:refresh" class="mr-5px" /> 刷新
          </el-button>
        </el-form-item>
      </el-form>
    </template>
  </ContentWrap>

  <ContentWrap v-if="shopId != null" title="预约接待">
    <el-alert
      class="mb-15px"
      type="info"
      :closable="false"
      show-icon
      title="时段没有开始或结束时间，不能标记已过点。迟到不会自动取消，也没有到店保留时长。"
    />
    <el-alert
      v-if="ignoredHolds > 0"
      class="mb-15px"
      type="warning"
      :closable="false"
      show-icon
      :title="`有 ${ignoredHolds} 条占位不属于本店，已从列表中去掉，不能操作。`"
    />
    <el-table v-loading="loading" :data="holds">
      <el-table-column label="占位" align="center" prop="id" />
      <el-table-column label="时段" align="center" prop="slotId" />
      <el-table-column label="顾客编号" align="center" prop="uid" />
      <el-table-column label="状态" align="center" min-width="180">
        <template #default="scope">
          {{ holdStatusText(scope.row) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="120">
        <template #default="scope">
          <el-button
            v-if="canSeat(scope.row, shopId)"
            link
            type="primary"
            :loading="seatingId === scope.row.id"
            v-hasPermi="['store:shop:update']"
            @click="seat(scope.row)"
          >
            入座
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="mt-10px">入座只把这条预约标成已入座。现有接口不会为这条预约分配桌台或开桌次。</div>
  </ContentWrap>

  <ContentWrap v-if="shopId != null" title="散客开桌">
    <el-alert
      v-if="ignoredSlots > 0 || ignoredTables > 0"
      class="mb-15px"
      type="warning"
      :closable="false"
      show-icon
      title="不属于本店的时段或桌台已从选项中去掉。"
    />
    <el-form :inline="true" class="-mb-15px">
      <el-form-item label="时段">
        <el-select v-model="slotId" placeholder="选择本店时段" clearable class="!w-240px">
          <el-option
            v-for="slot in slots"
            :key="slot.slotId"
            :label="slotLabel(slot)"
            :value="slot.slotId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="空闲桌台">
        <el-select v-model="signCode" placeholder="选择空闲桌台" clearable class="!w-240px">
          <el-option
            v-for="table in idleTables"
            :key="table.tableId"
            :label="table.signCode"
            :value="table.signCode"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          :disabled="!openReady"
          :loading="opening"
          v-hasPermi="['store:shop:update']"
          @click="openTable"
        >
          开桌
        </el-button>
      </el-form-item>
    </el-form>
    <div v-if="openHint" class="mt-10px">{{ openHint }}</div>
    <div class="mt-10px">开桌占用所选时段的一个名额，并占用所选空桌。这一页不收款。</div>

    <el-table v-loading="loading" :data="tables" class="mt-15px">
      <el-table-column label="桌台" align="center" prop="signCode" />
      <el-table-column label="状态" align="center">
        <template #default="scope">
          {{ scope.row.occupied === false ? '空闲' : '占用' }}
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>
</template>

<script setup lang="ts" name="HotpotReception">
import * as HotpotApi from '@/api/mall/store/hotpot'
import { useUserStore } from '@/store/modules/user'
import {
  acceptOpen,
  acceptSeat,
  boundShopId,
  canSeat,
  createRequestKey,
  forgetRequestKey,
  holdStatusText,
  openBlockReason,
  openBody,
  openScope,
  partitionByShop,
  seatBody,
  seatScope,
  stableRequestKey,
  type HoldView,
  type SlotView,
  type TableView
} from './reception'

const message = useMessage()
const userStore = useUserStore()
const shopId = computed(() => boundShopId((userStore.getUser as { shopId?: unknown }).shopId))

const loading = ref(false)
const opening = ref(false)
const seatingId = ref<number | null>(null)
const holds = ref<HoldView[]>([])
const slots = ref<SlotView[]>([])
const tables = ref<TableView[]>([])
const ignoredHolds = ref(0)
const ignoredSlots = ref(0)
const ignoredTables = ref(0)
const slotId = ref<number | undefined>(undefined)
const signCode = ref<string | undefined>(undefined)
const requestKeys = new Map<string, string>()

const selectedSlot = computed(() => slots.value.find((slot) => slot.slotId === slotId.value) ?? null)
const selectedTable = computed(
  () => tables.value.find((table) => table.signCode === signCode.value && table.occupied === false) ?? null
)
const idleTables = computed(() => tables.value.filter((table) => table.occupied === false && table.signCode))
const openReady = computed(() => {
  return (
    shopId.value != null &&
    openBlockReason(shopId.value, selectedSlot.value, selectedTable.value) == null
  )
})
const openHint = computed(() => {
  if (shopId.value == null || (slotId.value == null && !signCode.value)) {
    return ''
  }
  return openBlockReason(shopId.value, selectedSlot.value, selectedTable.value) ?? ''
})

const slotLabel = (slot: SlotView) => {
  return `时段 ${slot.slotId}（已占 ${slot.occupied} / ${slot.capacity}）`
}

const reload = async () => {
  const currentShop = shopId.value
  if (currentShop == null) {
    holds.value = []
    slots.value = []
    tables.value = []
    return
  }
  loading.value = true
  try {
    const [holdRows, slotRows, tableRows] = await Promise.all([
      HotpotApi.listHolds(),
      HotpotApi.listSlots(),
      HotpotApi.listTables()
    ])
    const holdPart = partitionByShop(holdRows, currentShop)
    const slotPart = partitionByShop(slotRows, currentShop)
    const tablePart = partitionByShop(tableRows, currentShop)
    holds.value = holdPart.visible
    slots.value = slotPart.visible
    tables.value = tablePart.visible
    ignoredHolds.value = holdPart.ignored
    ignoredSlots.value = slotPart.ignored
    ignoredTables.value = tablePart.ignored
    if (selectedSlot.value == null) {
      slotId.value = undefined
    }
    if (selectedTable.value == null) {
      signCode.value = undefined
    }
  } finally {
    loading.value = false
  }
}

const seat = async (hold: HoldView) => {
  const currentShop = shopId.value
  if (currentShop == null || !canSeat(hold, currentShop) || seatingId.value != null) {
    return
  }
  seatingId.value = hold.id
  try {
    await message.confirm('确认把这条预约标为已入座？这一步不分配桌台，也不为该预约开桌次。')
  } catch {
    seatingId.value = null
    return
  }
  const scope = seatScope(currentShop, hold)
  const requestKey = stableRequestKey(requestKeys, scope, createRequestKey)
  try {
    const result = await HotpotApi.seatHold(seatBody(hold, currentShop, requestKey))
    const accepted = acceptSeat(result, currentShop, hold)
    if (!accepted.ok) {
      message.error(accepted.reason)
      return
    }
    forgetRequestKey(requestKeys, scope)
    if (accepted.autoCancelClaimed) {
      message.warning('服务端标记了迟到自动取消。本页不会取消这条预约。')
    }
    message.success('已标为已入座')
    await reload()
  } catch {
    // 失败时保留同一个请求键，避免重试再记一笔。
  } finally {
    seatingId.value = null
  }
}

const openTable = async () => {
  const currentShop = shopId.value
  const slot = selectedSlot.value
  const table = selectedTable.value
  if (currentShop == null || opening.value || !slot || !table || openBlockReason(currentShop, slot, table)) {
    return
  }
  opening.value = true
  try {
    await message.confirm('确认为散客开桌？将占用所选时段的一个名额和所选空桌，不会收款。')
  } catch {
    opening.value = false
    return
  }
  const scope = openScope(currentShop, slot.slotId, table.signCode)
  const requestKey = stableRequestKey(requestKeys, scope, createRequestKey)
  try {
    const result = await HotpotApi.openWalkIn(openBody(currentShop, slot, table, requestKey))
    const accepted = acceptOpen(result, currentShop, slot.slotId)
    if (!accepted.ok) {
      message.error(accepted.reason)
      await reload()
      return
    }
    forgetRequestKey(requestKeys, scope)
    const payable =
      accepted.payableFen == null ? '应付未返回' : `应付 ${accepted.payableFen} 分，未收款`
    message.success(`已开桌次 ${accepted.sessionId}，${payable}`)
    signCode.value = undefined
    await reload()
  } catch {
    // 失败时保留同一个请求键。
  } finally {
    opening.value = false
  }
}

onMounted(() => {
  reload()
})
</script>
