<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { SeatTypeResponse, CreateSeatTypeRequest, UpdateSeatTypeRequest, SeatTypeStatus } from '@/types/seatType.types'
import type { DayPricingRuleResponse, TimeSlotPricingRuleResponse, CreateTimeSlotPricingRuleRequest, UpdateTimeSlotPricingRuleRequest } from '@/types/pricing.types'
import seatTypeService from '@/services/seatType.service'
import pricingService from '@/services/pricing.service'
import { formatCurrency, formatDate, formatStatus } from '@/utils/formatters'
import { useToast } from '@/composables/useToast'
import { ALLOWED_COLOR_TOKENS, ALLOWED_ICONS, getSeatColorConfig } from '@/utils/seatTypePresentation'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Badge from '@/components/common/Badge.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const toast = useToast()

// Active Tab
type TabKey = 'seat-types' | 'day-rules' | 'time-slots'
const activeTab = ref<TabKey>('seat-types')

// --- Tab 1: Seat Types ---
const seatTypes = ref<SeatTypeResponse[]>([])
const isLoadingSeatTypes = ref<boolean>(true)
const errorMessageSeatTypes = ref<string>('')

const isSeatTypeModalOpen = ref<boolean>(false)
const isEditingSeatType = ref<boolean>(false)
const editingSeatTypeId = ref<string>('')
const isSavingSeatType = ref<boolean>(false)
const seatTypeFormError = ref<string>('')

const seatTypeForm = ref<{
  code: string
  name: string
  capacity: number
  priceModifier: number
  colorToken: string
  icon: string
  description: string
  status: SeatTypeStatus
}>({
  code: '',
  name: '',
  capacity: 1,
  priceModifier: 0,
  colorToken: 'slate',
  icon: 'armchair',
  description: '',
  status: 'ACTIVE',
})

async function fetchSeatTypes() {
  isLoadingSeatTypes.value = true
  errorMessageSeatTypes.value = ''
  try {
    const res = await seatTypeService.getAdminSeatTypes({ page: 0, size: 50 })
    seatTypes.value = res.content || []
  } catch (err: any) {
    errorMessageSeatTypes.value = err.response?.data?.message || 'Không thể tải danh sách loại ghế.'
  } finally {
    isLoadingSeatTypes.value = false
  }
}

function openCreateSeatTypeModal() {
  isEditingSeatType.value = false
  editingSeatTypeId.value = ''
  seatTypeFormError.value = ''
  seatTypeForm.value = {
    code: '',
    name: '',
    capacity: 1,
    priceModifier: 0,
    colorToken: 'slate',
    icon: 'armchair',
    description: '',
    status: 'ACTIVE',
  }
  isSeatTypeModalOpen.value = true
}

function openEditSeatTypeModal(st: SeatTypeResponse) {
  isEditingSeatType.value = true
  editingSeatTypeId.value = st.id
  seatTypeFormError.value = ''
  seatTypeForm.value = {
    code: st.code || '',
    name: st.name,
    capacity: st.capacity || 1,
    priceModifier: st.priceModifier,
    colorToken: st.colorToken || 'slate',
    icon: st.icon || 'armchair',
    description: st.description || '',
    status: st.status || 'ACTIVE',
  }
  isSeatTypeModalOpen.value = true
}

async function handleSaveSeatType() {
  seatTypeFormError.value = ''
  if (!seatTypeForm.value.code.trim()) {
    seatTypeFormError.value = 'Vui lòng nhập mã loại ghế (VD: STANDARD, VIP, COUPLE).'
    return
  }
  if (!seatTypeForm.value.name.trim()) {
    seatTypeFormError.value = 'Vui lòng nhập tên loại ghế.'
    return
  }
  if (seatTypeForm.value.capacity < 1 || seatTypeForm.value.capacity > 4) {
    seatTypeFormError.value = 'Sức chứa phải từ 1 đến 4 người.'
    return
  }
  if (seatTypeForm.value.priceModifier < 0) {
    seatTypeFormError.value = 'Phụ thu không được âm.'
    return
  }

  isSavingSeatType.value = true
  try {
    if (isEditingSeatType.value) {
      const updatePayload: UpdateSeatTypeRequest = {
        code: seatTypeForm.value.code.trim().toUpperCase(),
        name: seatTypeForm.value.name.trim(),
        capacity: seatTypeForm.value.capacity,
        priceModifier: seatTypeForm.value.priceModifier,
        colorToken: seatTypeForm.value.colorToken,
        icon: seatTypeForm.value.icon,
        description: seatTypeForm.value.description.trim() || undefined,
        status: seatTypeForm.value.status,
      }
      await seatTypeService.updateSeatType(editingSeatTypeId.value, updatePayload)
      toast.success('Cập nhật loại ghế thành công!')
    } else {
      const createPayload: CreateSeatTypeRequest = {
        code: seatTypeForm.value.code.trim().toUpperCase(),
        name: seatTypeForm.value.name.trim(),
        capacity: seatTypeForm.value.capacity,
        priceModifier: seatTypeForm.value.priceModifier,
        colorToken: seatTypeForm.value.colorToken,
        icon: seatTypeForm.value.icon,
        description: seatTypeForm.value.description.trim() || undefined,
        status: seatTypeForm.value.status,
      }
      await seatTypeService.createSeatType(createPayload)
      toast.success('Tạo loại ghế mới thành công!')
    }
    isSeatTypeModalOpen.value = false
    await fetchSeatTypes()
  } catch (err: any) {
    seatTypeFormError.value = err.response?.data?.message || 'Không thể lưu loại ghế.'
  } finally {
    isSavingSeatType.value = false
  }
}

// --- Tab 2: Day Pricing Rules ---
const dayRules = ref<DayPricingRuleResponse[]>([])
const isLoadingDayRules = ref<boolean>(false)
const errorMessageDayRules = ref<string>('')

const isDayRuleModalOpen = ref<boolean>(false)
const editingDayRule = ref<DayPricingRuleResponse | null>(null)
const dayRuleModifier = ref<number>(0)
const isSavingDayRule = ref<boolean>(false)
const dayRuleFormError = ref<string>('')

async function fetchDayRules() {
  isLoadingDayRules.value = true
  errorMessageDayRules.value = ''
  try {
    dayRules.value = await pricingService.getDayPricingRules()
  } catch (err: any) {
    errorMessageDayRules.value = err.response?.data?.message || 'Không thể tải bảng giá theo ngày.'
  } finally {
    isLoadingDayRules.value = false
  }
}

function openEditDayRuleModal(rule: DayPricingRuleResponse) {
  editingDayRule.value = rule
  dayRuleModifier.value = rule.modifier
  dayRuleFormError.value = ''
  isDayRuleModalOpen.value = true
}

async function handleSaveDayRule() {
  if (!editingDayRule.value) return
  dayRuleFormError.value = ''
  isSavingDayRule.value = true
  try {
    await pricingService.updateDayPricingRule(editingDayRule.value.id, {
      modifier: dayRuleModifier.value,
    })
    toast.success(`Cập nhật giá ${editingDayRule.value.dayOfWeekName || editingDayRule.value.dayOfWeek} thành công!`)
    isDayRuleModalOpen.value = false
    await fetchDayRules()
  } catch (err: any) {
    dayRuleFormError.value = err.response?.data?.message || 'Không thể cập nhật phụ thu ngày.'
  } finally {
    isSavingDayRule.value = false
  }
}

function isWeekend(day: string) {
  return day === 'SATURDAY' || day === 'SUNDAY'
}

// --- Tab 3: Time Slot Pricing Rules ---
const timeSlotRules = ref<TimeSlotPricingRuleResponse[]>([])
const isLoadingTimeSlots = ref<boolean>(false)
const errorMessageTimeSlots = ref<string>('')

const isTimeSlotModalOpen = ref<boolean>(false)
const isEditingTimeSlot = ref<boolean>(false)
const editingTimeSlotId = ref<string>('')
const isSavingTimeSlot = ref<boolean>(false)
const timeSlotFormError = ref<string>('')

const timeSlotForm = ref<{
  name: string
  startTime: string
  endTime: string
  modifier: number
}>({
  name: '',
  startTime: '08:00',
  endTime: '12:00',
  modifier: 0,
})

async function fetchTimeSlotRules() {
  isLoadingTimeSlots.value = true
  errorMessageTimeSlots.value = ''
  try {
    timeSlotRules.value = await pricingService.getTimeSlotPricingRules()
  } catch (err: any) {
    errorMessageTimeSlots.value = err.response?.data?.message || 'Không thể tải danh sách khung giờ.'
  } finally {
    isLoadingTimeSlots.value = false
  }
}

function openCreateTimeSlotModal() {
  isEditingTimeSlot.value = false
  editingTimeSlotId.value = ''
  timeSlotFormError.value = ''
  timeSlotForm.value = {
    name: '',
    startTime: '08:00',
    endTime: '12:00',
    modifier: 0,
  }
  isTimeSlotModalOpen.value = true
}

function openEditTimeSlotModal(slot: TimeSlotPricingRuleResponse) {
  isEditingTimeSlot.value = true
  editingTimeSlotId.value = slot.id
  timeSlotFormError.value = ''
  timeSlotForm.value = {
    name: slot.name || '',
    startTime: slot.startTime.length > 5 ? slot.startTime.substring(0, 5) : slot.startTime,
    endTime: slot.endTime.length > 5 ? slot.endTime.substring(0, 5) : slot.endTime,
    modifier: slot.modifier,
  }
  isTimeSlotModalOpen.value = true
}

async function handleSaveTimeSlot() {
  timeSlotFormError.value = ''
  if (!timeSlotForm.value.name.trim()) {
    timeSlotFormError.value = 'Vui lòng nhập tên khung giờ.'
    return
  }
  if (!timeSlotForm.value.startTime || !timeSlotForm.value.endTime) {
    timeSlotFormError.value = 'Vui lòng chọn giờ bắt đầu và giờ kết thúc.'
    return
  }
  if (timeSlotForm.value.startTime >= timeSlotForm.value.endTime) {
    timeSlotFormError.value = 'Giờ bắt đầu phải trước giờ kết thúc.'
    return
  }

  isSavingTimeSlot.value = true
  try {
    const start = timeSlotForm.value.startTime.length === 5 ? `${timeSlotForm.value.startTime}:00` : timeSlotForm.value.startTime
    const end = timeSlotForm.value.endTime.length === 5 ? `${timeSlotForm.value.endTime}:00` : timeSlotForm.value.endTime

    if (isEditingTimeSlot.value) {
      const payload: UpdateTimeSlotPricingRuleRequest = {
        name: timeSlotForm.value.name.trim(),
        startTime: start,
        endTime: end,
        modifier: timeSlotForm.value.modifier,
      }
      await pricingService.updateTimeSlotPricingRule(editingTimeSlotId.value, payload)
      toast.success('Cập nhật khung giờ thành công!')
    } else {
      const payload: CreateTimeSlotPricingRuleRequest = {
        name: timeSlotForm.value.name.trim(),
        startTime: start,
        endTime: end,
        modifier: timeSlotForm.value.modifier,
      }
      await pricingService.createTimeSlotPricingRule(payload)
      toast.success('Tạo khung giờ định giá mới thành công!')
    }
    isTimeSlotModalOpen.value = false
    await fetchTimeSlotRules()
  } catch (err: any) {
    timeSlotFormError.value = err.response?.data?.message || 'Không thể lưu khung giờ định giá.'
  } finally {
    isSavingTimeSlot.value = false
  }
}

async function handleDeleteTimeSlot(slot: TimeSlotPricingRuleResponse) {
  if (!confirm(`Bạn có chắc chắn muốn xóa khung giờ "${slot.name}" (${slot.startTime} - ${slot.endTime})?`)) {
    return
  }
  try {
    await pricingService.deleteTimeSlotPricingRule(slot.id)
    toast.success('Đã xóa khung giờ thành công!')
    await fetchTimeSlotRules()
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Không thể xóa khung giờ.')
  }
}

function handleTabChange(tab: TabKey) {
  activeTab.value = tab
  if (tab === 'seat-types' && seatTypes.value.length === 0) {
    fetchSeatTypes()
  } else if (tab === 'day-rules' && dayRules.value.length === 0) {
    fetchDayRules()
  } else if (tab === 'time-slots' && timeSlotRules.value.length === 0) {
    fetchTimeSlotRules()
  }
}

onMounted(() => {
  fetchSeatTypes()
  fetchDayRules()
  fetchTimeSlotRules()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Top Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-white tracking-tight flex items-center gap-2">
          🏷️ Quản Lý Bảng Giá & Định Giá Vé
        </h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          Thiết lập phụ thu loại ghế, phụ thu ngày trong tuần và khung giờ chiếu theo công thức định giá tự động.
        </p>
      </div>

      <div class="flex items-center gap-2">
        <Button
          v-if="activeTab === 'seat-types'"
          variant="primary"
          size="md"
          class="shadow-lg shadow-indigo-600/30"
          @click="openCreateSeatTypeModal"
        >
          <template #prefix>
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
          </template>
          Tạo loại ghế mới
        </Button>

        <Button
          v-else-if="activeTab === 'time-slots'"
          variant="primary"
          size="md"
          class="shadow-lg shadow-indigo-600/30"
          @click="openCreateTimeSlotModal"
        >
          <template #prefix>
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
          </template>
          Thêm khung giờ mới
        </Button>
      </div>
    </div>

    <!-- Pricing Formula Highlight Banner -->
    <div class="rounded-2xl bg-gradient-to-r from-indigo-950/60 via-slate-900 to-purple-950/40 border border-indigo-500/20 p-4 sm:p-5 shadow-lg">
      <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div class="space-y-1">
          <div class="flex items-center gap-2">
            <span class="px-2 py-0.5 rounded text-[11px] font-bold bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
              CÔNG THỨC V1
            </span>
            <span class="text-xs font-semibold text-slate-300">Định giá vé động theo thời gian thực</span>
          </div>
          <div class="flex flex-wrap items-center gap-2 pt-1 font-mono text-xs sm:text-sm">
            <span class="px-2.5 py-1 rounded-lg bg-slate-800 text-slate-200 border border-slate-700 font-bold">
              Giá vé
            </span>
            <span class="text-slate-400 font-bold">=</span>
            <span class="px-2.5 py-1 rounded-lg bg-sky-500/15 text-sky-300 border border-sky-500/30 font-semibold" title="Giá gốc thiết lập trên suất chiếu">
              Giá suất chiếu (Base)
            </span>
            <span class="text-slate-400 font-bold">+</span>
            <span class="px-2.5 py-1 rounded-lg bg-emerald-500/15 text-emerald-300 border border-emerald-500/30 font-semibold" title="Phụ thu loại ghế (STANDARD, VIP, COUPLE...)">
              Phụ thu loại ghế
            </span>
            <span class="text-slate-400 font-bold">+</span>
            <span class="px-2.5 py-1 rounded-lg bg-amber-500/15 text-amber-300 border border-amber-500/30 font-semibold" title="Phụ thu ngày trong tuần (T2 - CN)">
              Phụ thu ngày
            </span>
            <span class="text-slate-400 font-bold">+</span>
            <span class="px-2.5 py-1 rounded-lg bg-purple-500/15 text-purple-300 border border-purple-500/30 font-semibold" title="Phụ thu khung giờ (Sáng sớm, Giờ vàng, Đêm muộn...)">
              Phụ thu khung giờ
            </span>
          </div>
        </div>
        <div class="text-xs text-slate-400 bg-slate-900/80 p-3 rounded-xl border border-slate-800 shrink-0 max-w-xs">
          💡 <span class="text-slate-300 font-medium">Bảo vệ lịch sử:</span> Khi vé đã đặt hoặc thanh toán thành công, giá vé được lưu cố định (snapshot) và không bị thay đổi nếu sửa bảng giá.
        </div>
      </div>
    </div>

    <!-- Tab Navigation -->
    <div class="flex items-center gap-2 border-b border-slate-800 pb-2">
      <button
        type="button"
        :class="[
          'px-4 py-2.5 rounded-xl font-semibold text-xs sm:text-sm transition-all flex items-center gap-2',
          activeTab === 'seat-types'
            ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/20'
            : 'text-slate-400 hover:text-white hover:bg-slate-850'
        ]"
        @click="handleTabChange('seat-types')"
      >
        <span>💺</span>
        <span>Loại Ghế & Phụ Thu</span>
        <span class="px-1.5 py-0.2 rounded-full text-[10px] bg-white/20 text-white font-mono">
          {{ seatTypes.length }}
        </span>
      </button>

      <button
        type="button"
        :class="[
          'px-4 py-2.5 rounded-xl font-semibold text-xs sm:text-sm transition-all flex items-center gap-2',
          activeTab === 'day-rules'
            ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/20'
            : 'text-slate-400 hover:text-white hover:bg-slate-850'
        ]"
        @click="handleTabChange('day-rules')"
      >
        <span>📅</span>
        <span>Giá Theo Ngày Trong Tuần</span>
        <span class="px-1.5 py-0.2 rounded-full text-[10px] bg-white/20 text-white font-mono">
          {{ dayRules.length || 7 }}
        </span>
      </button>

      <button
        type="button"
        :class="[
          'px-4 py-2.5 rounded-xl font-semibold text-xs sm:text-sm transition-all flex items-center gap-2',
          activeTab === 'time-slots'
            ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/20'
            : 'text-slate-400 hover:text-white hover:bg-slate-850'
        ]"
        @click="handleTabChange('time-slots')"
      >
        <span>⏰</span>
        <span>Giá Theo Khung Giờ</span>
        <span class="px-1.5 py-0.2 rounded-full text-[10px] bg-white/20 text-white font-mono">
          {{ timeSlotRules.length }}
        </span>
      </button>
    </div>

    <!-- TAB 1: SEAT TYPES -->
    <div v-if="activeTab === 'seat-types'" class="space-y-4">
      <ErrorAlert v-if="errorMessageSeatTypes" :message="errorMessageSeatTypes" @retry="fetchSeatTypes" />

      <Card v-else padding="none">
        <div class="overflow-x-auto">
          <table class="w-full text-left border-collapse text-xs">
            <thead>
              <tr class="bg-slate-850 border-b border-slate-700/80 text-[11px] font-bold uppercase text-slate-400 tracking-wider">
                <th class="px-4 py-3.5">Mã code</th>
                <th class="px-4 py-3.5">Tên hiển thị</th>
                <th class="px-4 py-3.5">Sức chứa</th>
                <th class="px-4 py-3.5">Phụ thu (₫)</th>
                <th class="px-4 py-3.5">Mô tả</th>
                <th class="px-4 py-3.5">Trạng thái</th>
                <th class="px-4 py-3.5">Cập nhật</th>
                <th class="px-4 py-3.5 text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
              <tr v-if="isLoadingSeatTypes" v-for="n in 4" :key="n" class="animate-pulse">
                <td colspan="8" class="px-4 py-4"><div class="h-5 rounded bg-slate-800"></div></td>
              </tr>

              <tr v-else-if="seatTypes.length === 0">
                <td colspan="8" class="px-4 py-12 text-center text-slate-500">
                  Chưa có dữ liệu loại ghế.
                </td>
              </tr>

              <tr v-else v-for="st in seatTypes" :key="st.id" class="hover:bg-slate-800/50 transition-colors">
                <td class="px-4 py-3.5 font-mono font-bold text-indigo-300">
                  {{ st.code || '---' }}
                </td>
                <td class="px-4 py-3.5 font-bold text-white">
                  <div class="flex items-center gap-2">
                    <span
                      class="w-3.5 h-3.5 rounded-full shrink-0 border border-white/20"
                      :style="{ backgroundColor: getSeatColorConfig(st.colorToken).hex }"
                    />
                    <span>{{ st.name }}</span>
                  </div>
                </td>
                <td class="px-4 py-3.5">
                  <span
                    :class="[
                      'px-2 py-0.5 rounded text-[11px] font-semibold',
                      st.capacity > 1
                        ? 'bg-rose-500/20 text-rose-300 border border-rose-500/30'
                        : 'bg-slate-800 text-slate-300 border border-slate-700'
                    ]"
                  >
                    {{ st.capacity || 1 }} người
                  </span>
                </td>
                <td class="px-4 py-3.5 font-black text-emerald-400 font-mono">
                  +{{ formatCurrency(st.priceModifier) }}
                </td>
                <td class="px-4 py-3.5 text-slate-300 max-w-[200px] truncate">
                  {{ st.description || '---' }}
                </td>
                <td class="px-4 py-3.5">
                  <Badge :variant="st.status === 'ACTIVE' ? 'success' : 'neutral'" size="sm">
                    {{ formatStatus(st.status) }}
                  </Badge>
                </td>
                <td class="px-4 py-3.5 text-slate-400">
                  {{ formatDate(st.updatedAt || st.createdAt) }}
                </td>
                <td class="px-4 py-3.5 text-right">
                  <Button variant="ghost" size="sm" @click="openEditSeatTypeModal(st)">
                    Sửa
                  </Button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </Card>
    </div>

    <!-- TAB 2: DAY PRICING RULES -->
    <div v-else-if="activeTab === 'day-rules'" class="space-y-4">
      <ErrorAlert v-if="errorMessageDayRules" :message="errorMessageDayRules" @retry="fetchDayRules" />

      <Card v-else padding="none">
        <div class="overflow-x-auto">
          <table class="w-full text-left border-collapse text-xs">
            <thead>
              <tr class="bg-slate-850 border-b border-slate-700/80 text-[11px] font-bold uppercase text-slate-400 tracking-wider">
                <th class="px-4 py-3.5">Ngày Trong Tuần</th>
                <th class="px-4 py-3.5">Phân Loại</th>
                <th class="px-4 py-3.5">Điều Chỉnh Giá (₫)</th>
                <th class="px-4 py-3.5">Cập Nhật Lần Cuối</th>
                <th class="px-4 py-3.5 text-right">Thao Tác</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
              <tr v-if="isLoadingDayRules" v-for="n in 7" :key="n" class="animate-pulse">
                <td colspan="5" class="px-4 py-4"><div class="h-5 rounded bg-slate-800"></div></td>
              </tr>

              <tr v-else-if="dayRules.length === 0">
                <td colspan="5" class="px-4 py-12 text-center text-slate-500">
                  Chưa có dữ liệu giá theo ngày.
                </td>
              </tr>

              <tr v-else v-for="dr in dayRules" :key="dr.id" class="hover:bg-slate-800/50 transition-colors">
                <td class="px-4 py-3.5 font-bold text-white text-sm">
                  {{ dr.dayOfWeekName || dr.dayOfWeek }}
                </td>
                <td class="px-4 py-3.5">
                  <span
                    v-if="isWeekend(dr.dayOfWeek)"
                    class="px-2 py-0.5 rounded text-[11px] font-bold bg-amber-500/15 text-amber-300 border border-amber-500/30"
                  >
                    🎉 Cuối Tuần
                  </span>
                  <span
                    v-else
                    class="px-2 py-0.5 rounded text-[11px] font-medium bg-slate-800 text-slate-400 border border-slate-700"
                  >
                    🏢 Ngày Thường
                  </span>
                </td>
                <td class="px-4 py-3.5 font-mono font-black text-sm">
                  <span v-if="dr.modifier > 0" class="text-emerald-400">
                    +{{ formatCurrency(dr.modifier) }}
                  </span>
                  <span v-else-if="dr.modifier < 0" class="text-rose-400">
                    {{ formatCurrency(dr.modifier) }}
                  </span>
                  <span v-else class="text-slate-400 font-normal">
                    0 ₫ (Tiêu chuẩn)
                  </span>
                </td>
                <td class="px-4 py-3.5 text-slate-400">
                  {{ formatDate(dr.updatedAt) }}
                </td>
                <td class="px-4 py-3.5 text-right">
                  <Button variant="ghost" size="sm" @click="openEditDayRuleModal(dr)">
                    Chỉnh sửa giá
                  </Button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </Card>
    </div>

    <!-- TAB 3: TIME SLOT PRICING RULES -->
    <div v-else-if="activeTab === 'time-slots'" class="space-y-4">
      <ErrorAlert v-if="errorMessageTimeSlots" :message="errorMessageTimeSlots" @retry="fetchTimeSlotRules" />

      <Card v-else padding="none">
        <div class="overflow-x-auto">
          <table class="w-full text-left border-collapse text-xs">
            <thead>
              <tr class="bg-slate-850 border-b border-slate-700/80 text-[11px] font-bold uppercase text-slate-400 tracking-wider">
                <th class="px-4 py-3.5">Tên Khung Giờ</th>
                <th class="px-4 py-3.5">Khoảng Thời Gian</th>
                <th class="px-4 py-3.5">Điều Chỉnh Giá (₫)</th>
                <th class="px-4 py-3.5">Cập Nhật Lần Cuối</th>
                <th class="px-4 py-3.5 text-right">Thao Tác</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
              <tr v-if="isLoadingTimeSlots" v-for="n in 3" :key="n" class="animate-pulse">
                <td colspan="5" class="px-4 py-4"><div class="h-5 rounded bg-slate-800"></div></td>
              </tr>

              <tr v-else-if="timeSlotRules.length === 0">
                <td colspan="5" class="px-4 py-12 text-center text-slate-500">
                  Chưa có khung giờ định giá nào được tạo. Nhấn "Thêm khung giờ mới" để thiết lập.
                </td>
              </tr>

              <tr v-else v-for="ts in timeSlotRules" :key="ts.id" class="hover:bg-slate-800/50 transition-colors">
                <td class="px-4 py-3.5 font-bold text-white text-sm">
                  {{ ts.name }}
                </td>
                <td class="px-4 py-3.5 font-mono text-indigo-300">
                  <span class="px-2 py-0.5 rounded bg-indigo-950/60 border border-indigo-500/20 font-bold">
                    {{ ts.startTime.substring(0, 5) }} – {{ ts.endTime.substring(0, 5) }}
                  </span>
                </td>
                <td class="px-4 py-3.5 font-mono font-black text-sm">
                  <span v-if="ts.modifier > 0" class="text-emerald-400">
                    +{{ formatCurrency(ts.modifier) }}
                  </span>
                  <span v-else-if="ts.modifier < 0" class="text-rose-400">
                    {{ formatCurrency(ts.modifier) }}
                  </span>
                  <span v-else class="text-slate-400 font-normal">
                    0 ₫ (Tiêu chuẩn)
                  </span>
                </td>
                <td class="px-4 py-3.5 text-slate-400">
                  {{ formatDate(ts.updatedAt) }}
                </td>
                <td class="px-4 py-3.5 text-right space-x-1.5">
                  <Button variant="ghost" size="sm" @click="openEditTimeSlotModal(ts)">
                    Sửa
                  </Button>
                  <Button variant="danger" size="sm" @click="handleDeleteTimeSlot(ts)">
                    Xóa
                  </Button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </Card>
    </div>

    <!-- MODAL: SEAT TYPE CREATE / EDIT -->
    <Teleport to="body">
      <transition name="modal">
        <div
          v-if="isSeatTypeModalOpen"
          class="fixed inset-0 z-50 overflow-y-auto bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 sm:p-6"
          @click.self="isSeatTypeModalOpen = false"
        >
          <div class="relative w-full max-w-lg rounded-3xl bg-slate-900 border border-slate-800 shadow-2xl p-6 sm:p-7 space-y-5 animate-scale">
            <div class="flex items-center justify-between border-b border-slate-800 pb-3">
              <h2 class="text-xl font-bold text-white tracking-tight">
                {{ isEditingSeatType ? 'Chỉnh Sửa Loại Ghế' : 'Tạo Loại Ghế Mới' }}
              </h2>
              <button
                type="button"
                class="w-8 h-8 rounded-full bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white flex items-center justify-center text-sm"
                @click="isSeatTypeModalOpen = false"
              >
                ✕
              </button>
            </div>

            <ErrorAlert v-if="seatTypeFormError" :message="seatTypeFormError" />

            <form class="space-y-4 text-sm" @submit.prevent="handleSaveSeatType">
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label for="seatTypeCodeInput" class="block font-semibold text-slate-300 mb-1">
                    Mã code * (In hoa)
                  </label>
                  <input
                    id="seatTypeCodeInput"
                    v-model="seatTypeForm.code"
                    type="text"
                    class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono text-sm uppercase focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    placeholder="VD: COUPLE, VIP..."
                    required
                  />
                </div>

                <div>
                  <label for="seatTypeNameInput" class="block font-semibold text-slate-300 mb-1">
                    Tên hiển thị *
                  </label>
                  <input
                    id="seatTypeNameInput"
                    v-model="seatTypeForm.name"
                    type="text"
                    class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    placeholder="VD: Ghế Đôi Sweetbox"
                    required
                  />
                </div>
              </div>

              <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label for="seatTypeCapacityInput" class="block font-semibold text-slate-300 mb-1">
                    Sức chứa (người) *
                  </label>
                  <input
                    id="seatTypeCapacityInput"
                    v-model.number="seatTypeForm.capacity"
                    type="number"
                    min="1"
                    max="4"
                    class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    required
                  />
                  <p v-if="isEditingSeatType" class="text-[11px] text-amber-400/80 mt-1">
                    Lưu ý: Không thể sửa sức chứa nếu đã có ghế áp dụng.
                  </p>
                </div>

                <div>
                  <label for="seatTypeModifierInput" class="block font-semibold text-slate-300 mb-1">
                    Phụ thu giá vé (₫) *
                  </label>
                  <input
                    id="seatTypeModifierInput"
                    v-model.number="seatTypeForm.priceModifier"
                    type="number"
                    step="1000"
                    min="0"
                    class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    placeholder="VD: 50000"
                    required
                  />
                </div>
              </div>

              <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label for="seatTypeColorInput" class="block font-semibold text-slate-300 mb-1">
                    Mã màu đại diện
                  </label>
                  <div class="relative">
                    <select
                      id="seatTypeColorInput"
                      v-model="seatTypeForm.colorToken"
                      class="w-full pl-9 pr-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 appearance-none"
                    >
                      <option v-for="c in ALLOWED_COLOR_TOKENS" :key="c.token" :value="c.token">
                        {{ c.label }}
                      </option>
                    </select>
                    <span
                      class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 rounded-full border border-white/20 pointer-events-none"
                      :style="{ backgroundColor: getSeatColorConfig(seatTypeForm.colorToken).hex }"
                    />
                  </div>
                </div>

                <div>
                  <label for="seatTypeIconInput" class="block font-semibold text-slate-300 mb-1">
                    Biểu tượng
                  </label>
                  <select
                    id="seatTypeIconInput"
                    v-model="seatTypeForm.icon"
                    class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  >
                    <option v-for="ic in ALLOWED_ICONS" :key="ic.identifier" :value="ic.identifier">
                      {{ ic.label }}
                    </option>
                  </select>
                </div>
              </div>

              <div v-if="isEditingSeatType">
                <label for="seatTypeStatusInput" class="block font-semibold text-slate-300 mb-1">
                  Trạng thái
                </label>
                <select
                  id="seatTypeStatusInput"
                  v-model="seatTypeForm.status"
                  class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                >
                  <option value="ACTIVE">Hoạt động (ACTIVE)</option>
                  <option value="INACTIVE">Ngưng hoạt động (INACTIVE)</option>
                </select>
              </div>

              <div>
                <label for="seatTypeDescInput" class="block font-semibold text-slate-300 mb-1">
                  Mô tả / Ghi chú
                </label>
                <textarea
                  id="seatTypeDescInput"
                  v-model="seatTypeForm.description"
                  rows="2"
                  class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="Thông tin thêm về loại ghế..."
                ></textarea>
              </div>

              <div class="flex items-center justify-end gap-3 pt-3 border-t border-slate-800">
                <Button variant="secondary" size="md" type="button" :disabled="isSavingSeatType" @click="isSeatTypeModalOpen = false">
                  Hủy bỏ
                </Button>
                <Button variant="primary" size="md" type="submit" :loading="isSavingSeatType" class="shadow-lg shadow-indigo-600/30">
                  {{ isEditingSeatType ? 'Lưu thay đổi' : 'Tạo mới' }}
                </Button>
              </div>
            </form>
          </div>
        </div>
      </transition>
    </Teleport>

    <!-- MODAL: DAY PRICING RULE EDIT -->
    <Teleport to="body">
      <transition name="modal">
        <div
          v-if="isDayRuleModalOpen"
          class="fixed inset-0 z-50 overflow-y-auto bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 sm:p-6"
          @click.self="isDayRuleModalOpen = false"
        >
          <div class="relative w-full max-w-md rounded-3xl bg-slate-900 border border-slate-800 shadow-2xl p-6 sm:p-7 space-y-5 animate-scale">
            <div class="flex items-center justify-between border-b border-slate-800 pb-3">
              <h2 class="text-xl font-bold text-white tracking-tight">
                Chỉnh Sửa Giá {{ editingDayRule?.dayOfWeekName || editingDayRule?.dayOfWeek }}
              </h2>
              <button
                type="button"
                class="w-8 h-8 rounded-full bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white flex items-center justify-center text-sm"
                @click="isDayRuleModalOpen = false"
              >
                ✕
              </button>
            </div>

            <ErrorAlert v-if="dayRuleFormError" :message="dayRuleFormError" />

            <form class="space-y-4 text-sm" @submit.prevent="handleSaveDayRule">
              <div>
                <label for="dayModifierInput" class="block font-semibold text-slate-300 mb-1">
                  Mức phụ thu / Giảm trừ (₫)
                </label>
                <input
                  id="dayModifierInput"
                  v-model.number="dayRuleModifier"
                  type="number"
                  step="1000"
                  class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white text-sm font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="VD: 10000 hoặc -5000"
                  required
                />
                <p class="text-xs text-slate-400 mt-1">
                  * Nhập số dương (VD: <span class="text-emerald-400">+10000</span>) để tăng giá vé ngày này.
                  <br />
                  * Nhập số âm (VD: <span class="text-rose-400">-5000</span>) để giảm giá vé.
                  <br />
                  * Nhập <span class="text-slate-300">0</span> để giữ nguyên giá tiêu chuẩn.
                </p>
              </div>

              <div class="flex items-center justify-end gap-3 pt-3 border-t border-slate-800">
                <Button variant="secondary" size="md" type="button" :disabled="isSavingDayRule" @click="isDayRuleModalOpen = false">
                  Hủy bỏ
                </Button>
                <Button variant="primary" size="md" type="submit" :loading="isSavingDayRule" class="shadow-lg shadow-indigo-600/30">
                  Lưu thay đổi
                </Button>
              </div>
            </form>
          </div>
        </div>
      </transition>
    </Teleport>

    <!-- MODAL: TIME SLOT CREATE / EDIT -->
    <Teleport to="body">
      <transition name="modal">
        <div
          v-if="isTimeSlotModalOpen"
          class="fixed inset-0 z-50 overflow-y-auto bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 sm:p-6"
          @click.self="isTimeSlotModalOpen = false"
        >
          <div class="relative w-full max-w-lg rounded-3xl bg-slate-900 border border-slate-800 shadow-2xl p-6 sm:p-7 space-y-5 animate-scale">
            <div class="flex items-center justify-between border-b border-slate-800 pb-3">
              <h2 class="text-xl font-bold text-white tracking-tight">
                {{ isEditingTimeSlot ? 'Chỉnh Sửa Khung Giờ' : 'Thêm Khung Giờ Định Giá' }}
              </h2>
              <button
                type="button"
                class="w-8 h-8 rounded-full bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white flex items-center justify-center text-sm"
                @click="isTimeSlotModalOpen = false"
              >
                ✕
              </button>
            </div>

            <ErrorAlert v-if="timeSlotFormError" :message="timeSlotFormError" />

            <form class="space-y-4 text-sm" @submit.prevent="handleSaveTimeSlot">
              <div>
                <label for="timeSlotNameInput" class="block font-semibold text-slate-300 mb-1">
                  Tên khung giờ *
                </label>
                <input
                  id="timeSlotNameInput"
                  v-model="timeSlotForm.name"
                  type="text"
                  class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="VD: Suất Chiếu Sớm (Early Bird), Giờ Vàng (Prime Time)..."
                  required
                />
              </div>

              <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label for="timeSlotStartInput" class="block font-semibold text-slate-300 mb-1">
                    Giờ bắt đầu *
                  </label>
                  <input
                    id="timeSlotStartInput"
                    v-model="timeSlotForm.startTime"
                    type="time"
                    class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    required
                  />
                </div>

                <div>
                  <label for="timeSlotEndInput" class="block font-semibold text-slate-300 mb-1">
                    Giờ kết thúc *
                  </label>
                  <input
                    id="timeSlotEndInput"
                    v-model="timeSlotForm.endTime"
                    type="time"
                    class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    required
                  />
                </div>
              </div>

              <div>
                <label for="timeSlotModifierInput" class="block font-semibold text-slate-300 mb-1">
                  Mức phụ thu / Giảm trừ (₫) *
                </label>
                <input
                  id="timeSlotModifierInput"
                  v-model.number="timeSlotForm.modifier"
                  type="number"
                  step="1000"
                  class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="VD: 15000 hoặc -10000"
                  required
                />
                <p class="text-xs text-slate-400 mt-1">
                  * Nhập số dương (VD: <span class="text-emerald-400">+15000</span>) để tăng giá suất chiếu trong khung giờ này.
                  <br />
                  * Nhập số âm (VD: <span class="text-rose-400">-10000</span>) để khuyến mãi giảm giá.
                </p>
              </div>

              <div class="flex items-center justify-end gap-3 pt-3 border-t border-slate-800">
                <Button variant="secondary" size="md" type="button" :disabled="isSavingTimeSlot" @click="isTimeSlotModalOpen = false">
                  Hủy bỏ
                </Button>
                <Button variant="primary" size="md" type="submit" :loading="isSavingTimeSlot" class="shadow-lg shadow-indigo-600/30">
                  {{ isEditingTimeSlot ? 'Lưu thay đổi' : 'Tạo mới' }}
                </Button>
              </div>
            </form>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.25s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.animate-scale {
  animation: scaleUp 0.25s ease-out;
}

@keyframes scaleUp {
  from {
    transform: scale(0.96);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
