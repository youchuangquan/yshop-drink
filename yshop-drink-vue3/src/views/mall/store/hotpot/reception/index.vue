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
      title="时段带有开始和结束时刻，且结束时刻已过时，本表会标出「时段已过」，只作提醒。不会自动取消，不会释放名额，也没有到店保留时长。没有时刻，或时刻跨过午夜，不标。"
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
      <el-table-column label="时段" align="center" min-width="200">
        <template #default="scope">
          {{ holdSlotText(scope.row) }}
        </template>
      </el-table-column>
      <el-table-column label="顾客编号" align="center" prop="uid" />
      <el-table-column label="状态" align="center" min-width="180">
        <template #default="scope">
          <div>{{ holdStatusText(scope.row) }}</div>
          <el-tag v-if="passedReminder(scope.row)" class="mt-5px" type="warning">时段已过</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" min-width="280">
        <template #default="scope">
          <template v-if="canAssignTable(scope.row, shopId)">
            <el-select
              v-if="idleTables.length > 0"
              :model-value="assignSign[scope.row.id]"
              placeholder="空闲桌台"
              clearable
              class="!w-140px mr-5px"
              :disabled="assigningId === scope.row.id"
              @change="(value) => chooseTable(scope.row.id, value)"
            >
              <el-option
                v-for="table in idleTables"
                :key="table.tableId"
                :label="table.signCode"
                :value="table.signCode"
              />
            </el-select>
            <span v-else class="mr-5px">没有空闲桌台</span>
            <el-button
              link
              type="primary"
              :disabled="reservationSeatBlockReason(shopId, scope.row, tableFor(scope.row)) != null"
              :loading="assigningId === scope.row.id"
              v-hasPermi="['store:shop:update']"
              @click="assignTable(scope.row)"
            >
              安排空桌
            </el-button>
          </template>
          <el-button
            v-if="canSeat(scope.row, shopId)"
            link
            type="primary"
            :disabled="assigningId != null"
            :loading="seatingId === scope.row.id"
            v-hasPermi="['store:shop:update']"
            @click="seat(scope.row)"
          >
            入座
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="mt-10px">
      入座只把未入座的预约标成已入座，不分配桌台，也不开桌次。安排空桌把本店空桌绑到该顾客并开出桌次，时段名额不会再加一次。顾客编号为 0 的散客不走安排空桌。时段已过只是提醒。
    </div>
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

  <ContentWrap v-if="shopId != null" title="进行中桌次">
    <el-alert
      class="mb-15px"
      type="info"
      :closable="false"
      show-icon
      title="登记已上桌的份数，或退还没上桌的份数。下单份数不会改小。已上桌的不能退。这里不结账，也不收款。"
    />
    <el-alert
      v-if="ignoredSessions > 0"
      class="mb-15px"
      type="warning"
      :closable="false"
      show-icon
      :title="`有 ${ignoredSessions} 条桌次不属于本店或已不在进行中，已从列表中去掉。`"
    />
    <div v-if="!loading && sessions.length === 0">本店没有进行中的桌次。</div>
    <div v-for="session in sessions" :key="session.sessionId" class="mt-15px">
      <div>
        桌台 {{ session.signCode || '未绑定桌台' }} · 桌次 {{ session.sessionId }} · 版本
        {{ session.version }} · 应付 {{ session.payableFen == null ? '未返回' : `${session.payableFen} 分` }}
      </div>
      <div v-if="session.feesWaived" class="mt-10px">锅底费已免除。锅底仍留在这桌，不计应付。</div>
      <div v-if="session.dishes.length === 0" class="mt-10px">这桌没有菜品行。</div>
      <el-table v-else v-loading="loading" :data="session.dishes" class="mt-10px">
        <el-table-column label="菜品" align="center" prop="name" min-width="120" />
        <el-table-column label="下单" align="center" prop="quantity" />
        <el-table-column label="已上桌" align="center" prop="servedQty" />
        <el-table-column label="已退" align="center" prop="refundedQty" />
        <el-table-column label="未上桌可退" align="center" prop="refundableQty" />
        <el-table-column label="上菜" align="center" min-width="220">
          <template #default="scope">
            <template v-if="scope.row.refundableQty > 0">
              <el-input-number
                :model-value="draftQty('serve', session.sessionId, scope.row)"
                :min="1"
                :max="scope.row.refundableQty"
                :step="1"
                step-strictly
                class="!w-120px mr-5px"
                :disabled="pendingLine != null"
                @change="(value) => setDraft('serve', session.sessionId, scope.row.lineId, value)"
              />
              <el-button
                link
                type="primary"
                :disabled="serveBlocked(session, scope.row)"
                :loading="pendingLine === lineToken('serve', session.sessionId, scope.row.lineId)"
                v-hasPermi="['store:shop:query']"
                @click="serveLine(session, scope.row)"
              >
                登记上菜
              </el-button>
            </template>
            <span v-else>没有未上桌的份数</span>
          </template>
        </el-table-column>
        <el-table-column label="退未上桌" align="center" min-width="220">
          <template #default="scope">
            <template v-if="scope.row.refundableQty > 0">
              <el-input-number
                :model-value="draftQty('refund', session.sessionId, scope.row)"
                :min="1"
                :max="scope.row.refundableQty"
                :step="1"
                step-strictly
                class="!w-120px mr-5px"
                :disabled="pendingLine != null"
                @change="(value) => setDraft('refund', session.sessionId, scope.row.lineId, value)"
              />
              <el-button
                link
                type="primary"
                :disabled="refundBlocked(session, scope.row)"
                :loading="pendingLine === lineToken('refund', session.sessionId, scope.row.lineId)"
                v-hasPermi="['store:shop:update']"
                @click="refundLine(session, scope.row)"
              >
                退未上桌
              </el-button>
            </template>
            <span v-else>{{ scope.row.servedQty > 0 ? '已上桌的不能退' : '没有可退份数' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </ContentWrap>
</template>

<script setup lang="ts" name="HotpotReception">
import * as HotpotApi from '@/api/mall/store/hotpot'
import { useUserStore } from '@/store/modules/user'
import {
  acceptLineCommand,
  acceptOpen,
  acceptReservationSeat,
  acceptSeat,
  boundShopId,
  canAssignTable,
  canSeat,
  claimRequestKey,
  createRequestKey,
  forgetRequestKey,
  holdStatusText,
  lateArrivalFlag,
  lineCommandBlockReason,
  lineCommandBody,
  lineCommandScope,
  openBlockReason,
  openBody,
  openScope,
  partitionByShop,
  presentOpenSessions,
  releaseRequestKey,
  reservationSeatBlockReason,
  reservationSeatBody,
  reservationSeatScope,
  seatBody,
  seatScope,
  slotClockText,
  stableRequestKey,
  type DishLineView,
  type HoldView,
  type OpenSessionView,
  type SlotView,
  type TableView
} from './reception'

const message = useMessage()
const userStore = useUserStore()
const shopId = computed(() => boundShopId((userStore.getUser as { shopId?: unknown }).shopId))

const loading = ref(false)
const opening = ref(false)
const seatingId = ref<number | null>(null)
const assigningId = ref<number | null>(null)
const clock = ref(new Date())
const assignSign = ref<Record<number, string>>({})
const holds = ref<HoldView[]>([])
const slots = ref<SlotView[]>([])
const tables = ref<TableView[]>([])
const sessions = ref<OpenSessionView[]>([])
const ignoredHolds = ref(0)
const ignoredSlots = ref(0)
const ignoredTables = ref(0)
const ignoredSessions = ref(0)
const slotId = ref<number | undefined>(undefined)
const signCode = ref<string | undefined>(undefined)
const pendingLine = ref<string | null>(null)
const drafts = ref<Record<string, number>>({})
const requestKeys = new Map<string, string>()
const inflightKeys = new Set<string>()

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
  const clockText = slotClockText(slot)
  const time = clockText ? ` ${clockText}` : ''
  return `时段 ${slot.slotId}${time}（已占 ${slot.occupied} / ${slot.capacity}）`
}

const slotFor = (hold: HoldView) => slots.value.find((slot) => slot.slotId === hold.slotId) ?? null

const holdSlotText = (hold: HoldView) => {
  const clockText = slotClockText(slotFor(hold))
  return clockText ? `${hold.slotId} ${clockText}` : String(hold.slotId)
}

const passedReminder = (hold: HoldView) => lateArrivalFlag(slotFor(hold), clock.value) != null

const tableFor = (hold: HoldView) => {
  const code = assignSign.value[hold.id]
  if (!code) {
    return null
  }
  return tables.value.find((table) => table.signCode === code && table.occupied === false) ?? null
}

const chooseTable = (holdId: number, value: unknown) => {
  const next = { ...assignSign.value }
  if (typeof value === 'string' && value.trim() !== '') {
    next[holdId] = value
  } else {
    delete next[holdId]
  }
  assignSign.value = next
}

const reload = async () => {
  const currentShop = shopId.value
  if (currentShop == null) {
    holds.value = []
    slots.value = []
    tables.value = []
    sessions.value = []
    return
  }
  loading.value = true
  clock.value = new Date()
  try {
    const [holdRows, slotRows, tableRows, sessionRows] = await Promise.all([
      HotpotApi.listHolds(),
      HotpotApi.listSlots(),
      HotpotApi.listTables(),
      HotpotApi.listOpenSessions()
    ])
    const holdPart = partitionByShop(holdRows, currentShop)
    const slotPart = partitionByShop(slotRows, currentShop)
    const tablePart = partitionByShop(tableRows, currentShop)
    const sessionPart = presentOpenSessions(sessionRows, currentShop)
    holds.value = holdPart.visible
    slots.value = slotPart.visible
    tables.value = tablePart.visible
    sessions.value = sessionPart.visible
    ignoredHolds.value = holdPart.ignored
    ignoredSlots.value = slotPart.ignored
    ignoredTables.value = tablePart.ignored
    ignoredSessions.value = sessionPart.ignored
    if (selectedSlot.value == null) {
      slotId.value = undefined
    }
    if (selectedTable.value == null) {
      signCode.value = undefined
    }
    const idleCodes = new Set(
      tablePart.visible.filter((table) => table.occupied === false).map((table) => table.signCode)
    )
    const nextSign: Record<number, string> = {}
    for (const [id, code] of Object.entries(assignSign.value)) {
      if (idleCodes.has(code)) {
        nextSign[Number(id)] = code
      }
    }
    assignSign.value = nextSign
  } finally {
    loading.value = false
  }
}

const seat = async (hold: HoldView) => {
  const currentShop = shopId.value
  if (
    currentShop == null ||
    !canSeat(hold, currentShop) ||
    seatingId.value != null ||
    assigningId.value != null
  ) {
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

const assignTable = async (hold: HoldView) => {
  const currentShop = shopId.value
  if (
    currentShop == null ||
    assigningId.value != null ||
    seatingId.value != null ||
    reservationSeatBlockReason(currentShop, hold, tableFor(hold))
  ) {
    return
  }
  assigningId.value = hold.id
  try {
    await message.confirm(
      '确认把这条预约安排到所选空桌并开桌次？桌次属于该顾客，时段名额不会再占一次，也不会收款。'
    )
  } catch {
    assigningId.value = null
    return
  }
  const table = tableFor(hold)
  if (!table || reservationSeatBlockReason(currentShop, hold, table)) {
    assigningId.value = null
    return
  }
  const scope = reservationSeatScope(currentShop, hold, table.signCode)
  const requestKey = stableRequestKey(requestKeys, scope, createRequestKey)
  try {
    const result = await HotpotApi.seatReservation(
      reservationSeatBody(hold, currentShop, table, requestKey)
    )
    const accepted = acceptReservationSeat(result, currentShop, hold)
    if (!accepted.ok) {
      message.error(accepted.reason)
      await reload()
      return
    }
    forgetRequestKey(requestKeys, scope)
    const payable =
      accepted.payableFen == null ? '应付未返回' : `应付 ${accepted.payableFen} 分，未收款`
    message.success(`已为顾客 ${accepted.ownerUid} 开桌次 ${accepted.sessionId}，${payable}`)
    const nextSign = { ...assignSign.value }
    delete nextSign[hold.id]
    assignSign.value = nextSign
    await reload()
  } catch {
    // 失败时保留同一个请求键，只用于这张桌的重试。
  } finally {
    assigningId.value = null
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

const draftKey = (action: 'serve' | 'refund', sessionId: number, lineId: number) => {
  return `${action}:${sessionId}:${lineId}`
}

const lineToken = (action: 'serve' | 'refund', sessionId: number, lineId: number) => {
  return draftKey(action, sessionId, lineId)
}

const draftQty = (action: 'serve' | 'refund', sessionId: number, line: DishLineView) => {
  const stored = drafts.value[draftKey(action, sessionId, line.lineId)]
  if (typeof stored === 'number' && stored >= 1 && stored <= line.refundableQty) {
    return stored
  }
  return line.refundableQty >= 1 ? 1 : undefined
}

const setDraft = (action: 'serve' | 'refund', sessionId: number, lineId: number, value: unknown) => {
  const next = { ...drafts.value }
  const key = draftKey(action, sessionId, lineId)
  if (typeof value === 'number' && Number.isSafeInteger(value) && value >= 1) {
    next[key] = value
  } else {
    delete next[key]
  }
  drafts.value = next
}

const serveBlocked = (session: OpenSessionView, line: DishLineView) => {
  return (
    pendingLine.value != null ||
    lineCommandBlockReason(shopId.value, session, line, draftQty('serve', session.sessionId, line) ?? 0, 'serve') !=
      null
  )
}

const refundBlocked = (session: OpenSessionView, line: DishLineView) => {
  return (
    pendingLine.value != null ||
    lineCommandBlockReason(
      shopId.value,
      session,
      line,
      draftQty('refund', session.sessionId, line) ?? 0,
      'refund'
    ) != null
  )
}

const submitLine = async (action: 'serve' | 'refund', session: OpenSessionView, line: DishLineView) => {
  const currentShop = shopId.value
  const quantity = draftQty(action, session.sessionId, line) ?? 0
  const token = lineToken(action, session.sessionId, line.lineId)
  if (currentShop == null || pendingLine.value != null) {
    return
  }
  if (lineCommandBlockReason(currentShop, session, line, quantity, action)) {
    return
  }
  pendingLine.value = token
  const verb = action === 'serve' ? '登记上菜' : '退还未上桌的份数'
  try {
    await message.confirm(`确认${verb} ${quantity} 份？这一步不结账，也不收款。`)
  } catch {
    pendingLine.value = null
    return
  }
  const scope = lineCommandScope(action, session.sessionId, line.lineId, quantity)
  let requestKey = ''
  try {
    requestKey = stableRequestKey(requestKeys, scope, createRequestKey)
    if (!claimRequestKey(inflightKeys, requestKey)) {
      message.warning('这一笔正在提交')
      return
    }
    const body = lineCommandBody(session, currentShop, line, quantity, requestKey, action)
    const result =
      action === 'serve' ? await HotpotApi.serveDishes(body) : await HotpotApi.refundDishes(body)
    const accepted = acceptLineCommand(result, currentShop, session.sessionId)
    if (!accepted.ok) {
      message.error(accepted.reason)
      return
    }
    forgetRequestKey(requestKeys, scope)
    if (accepted.released) {
      message.success('未上桌的菜已退完，应付为 0，桌次已释放。这不是支付成功。')
    } else if (action === 'serve') {
      message.success('已登记上菜')
    } else {
      message.success('已退还未上桌的份数')
    }
    await reload()
  } catch {
    // 失败时保留同一个请求键，避免同一份明细重试再记一笔。
  } finally {
    if (requestKey) {
      releaseRequestKey(inflightKeys, requestKey)
    }
    pendingLine.value = null
  }
}

const serveLine = (session: OpenSessionView, line: DishLineView) => {
  return submitLine('serve', session, line)
}

const refundLine = (session: OpenSessionView, line: DishLineView) => {
  return submitLine('refund', session, line)
}

onMounted(() => {
  reload()
})
</script>
