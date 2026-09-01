<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type {
  PromotionResponse,
  PromotionStatus,
  PromotionDiscountType,
  CreatePromotionRequest,
  UpdatePromotionRequest,
} from '@/types/promotion.types'
import promotionService from '@/services/promotion.service'
import { formatCurrency, formatDate, formatStatus } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import { useToast } from '@/composables/useToast'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Badge from '@/components/common/Badge.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const toast = useToast()
const { t } = useI18n()

const promotions = ref<PromotionResponse[]>([])
const activeStatusFilter = ref<string>('ALL')
const searchQuery = ref<string>('')

const currentPage = ref<number>(0)
const totalPages = ref<number>(1)
const totalElements = ref<number>(0)

const isLoading = ref<boolean>(true)
const isActionLoading = ref<Record<string, boolean>>({})
const errorMessage = ref<string>('')

// Create/Edit Modal State
const isModalOpen = ref<boolean>(false)
const isEditing = ref<boolean>(false)
const editingId = ref<string>('')
const isSaving = ref<boolean>(false)
const formError = ref<string>('')

const formData = ref<{
  code: string
  name: string
  description: string
  discountType: PromotionDiscountType
  discountValue: number | null
  minOrderAmount: number | null
  maxDiscountAmount: number | null
  startAt: string
  endAt: string
  usageLimit: number | null
  status: PromotionStatus
}>({
  code: '',
  name: '',
  description: '',
  discountType: 'PERCENTAGE',
  discountValue: null,
  minOrderAmount: null,
  maxDiscountAmount: null,
  startAt: '',
  endAt: '',
  usageLimit: null,
  status: 'ACTIVE',
})

async function fetchPromotions() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const statusParam =
      activeStatusFilter.value !== 'ALL'
        ? (activeStatusFilter.value as PromotionStatus)
        : undefined

    const res = await promotionService.getAdminPromotions({
      status: statusParam,
      q: searchQuery.value.trim() || undefined,
      page: currentPage.value,
      size: 10,
      sort: 'createdAt,desc',
    })

    promotions.value = res.content || []
    totalPages.value = res.totalPages || 1
    totalElements.value = res.totalElements || 0
  } catch (err: any) {
    errorMessage.value =
      err.response?.data?.message || 'Không thể tải danh sách khuyến mãi.'
  } finally {
    isLoading.value = false
  }
}

function handleSearch() {
  currentPage.value = 0
  fetchPromotions()
}

function handleStatusFilter(status: string) {
  activeStatusFilter.value = status
  currentPage.value = 0
  fetchPromotions()
}

function setPage(page: number) {
  currentPage.value = page
  fetchPromotions()
}

function openCreateModal() {
  isEditing.value = false
  editingId.value = ''
  formError.value = ''

  // Default dates: today ~ 30 days later
  const now = new Date()
  const later = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000)

  formData.value = {
    code: '',
    name: '',
    description: '',
    discountType: 'PERCENTAGE',
    discountValue: 10,
    minOrderAmount: null,
    maxDiscountAmount: null,
    startAt: now.toISOString().slice(0, 16),
    endAt: later.toISOString().slice(0, 16),
    usageLimit: null,
    status: 'ACTIVE',
  }
  isModalOpen.value = true
}

function openEditModal(promo: PromotionResponse) {
  isEditing.value = true
  editingId.value = promo.id
  formError.value = ''

  formData.value = {
    code: promo.code,
    name: promo.name,
    description: promo.description || '',
    discountType: promo.discountType,
    discountValue: promo.discountValue,
    minOrderAmount: promo.minOrderAmount || null,
    maxDiscountAmount: promo.maxDiscountAmount || null,
    startAt: promo.startAt.slice(0, 16),
    endAt: promo.endAt.slice(0, 16),
    usageLimit: promo.usageLimit || null,
    status: promo.status,
  }
  isModalOpen.value = true
}

async function handleSavePromotion() {
  formError.value = ''

  if (!formData.value.name.trim()) {
    formError.value = 'Vui lòng nhập tên chương trình khuyến mãi.'
    return
  }

  if (!isEditing.value && !formData.value.code.trim()) {
    formError.value = 'Vui lòng nhập mã khuyến mãi (Code).'
    return
  }

  if (!formData.value.discountValue || formData.value.discountValue <= 0) {
    formError.value = 'Giá trị giảm giá phải lớn hơn 0.'
    return
  }

  if (formData.value.discountType === 'PERCENTAGE' && formData.value.discountValue > 100) {
    formError.value = 'Phần trăm giảm giá không được vượt quá 100%.'
    return
  }

  if (!formData.value.startAt || !formData.value.endAt) {
    formError.value = 'Vui lòng chọn thời gian bắt đầu và kết thúc.'
    return
  }

  if (new Date(formData.value.endAt).getTime() <= new Date(formData.value.startAt).getTime()) {
    formError.value = 'Thời gian kết thúc phải lớn hơn thời gian bắt đầu.'
    return
  }

  isSaving.value = true
  try {
    if (isEditing.value) {
      const updatePayload: UpdatePromotionRequest = {
        name: formData.value.name.trim(),
        description: formData.value.description.trim() || undefined,
        minOrderAmount: formData.value.minOrderAmount || undefined,
        maxDiscountAmount: formData.value.maxDiscountAmount || undefined,
        startAt: formData.value.startAt,
        endAt: formData.value.endAt,
        usageLimit: formData.value.usageLimit || undefined,
      }
      await promotionService.updatePromotion(editingId.value, updatePayload)
      toast.success('Cập nhật khuyến mãi thành công!')
    } else {
      const createPayload: CreatePromotionRequest = {
        code: formData.value.code.trim().toUpperCase(),
        name: formData.value.name.trim(),
        description: formData.value.description.trim() || undefined,
        discountType: formData.value.discountType,
        discountValue: formData.value.discountValue,
        minOrderAmount: formData.value.minOrderAmount || undefined,
        maxDiscountAmount: formData.value.maxDiscountAmount || undefined,
        startAt: formData.value.startAt,
        endAt: formData.value.endAt,
        usageLimit: formData.value.usageLimit || undefined,
        status: formData.value.status,
      }
      await promotionService.createPromotion(createPayload)
      toast.success('Tạo khuyến mãi mới thành công!')
    }

    isModalOpen.value = false
    await fetchPromotions()
  } catch (err: any) {
    formError.value =
      err.response?.data?.message || 'Không thể lưu chương trình khuyến mãi.'
  } finally {
    isSaving.value = false
  }
}

async function handleToggleStatus(promo: PromotionResponse) {
  const newStatus: PromotionStatus = promo.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  const actionText = newStatus === 'ACTIVE' ? 'kích hoạt' : 'tạm ngưng'

  if (!confirm(`Bạn có chắc chắn muốn ${actionText} mã khuyến mãi ${promo.code}?`)) {
    return
  }

  isActionLoading.value[promo.id] = true
  try {
    await promotionService.updatePromotionStatus(promo.id, { status: newStatus })
    toast.success(`Đã ${actionText} mã khuyến mãi ${promo.code}`)
    await fetchPromotions()
  } catch (err: any) {
    toast.error('Lỗi', err.response?.data?.message || `Không thể ${actionText} mã khuyến mãi.`)
  } finally {
    isActionLoading.value[promo.id] = false
  }
}

function getStatusBadgeVariant(status: PromotionStatus): 'success' | 'danger' | 'neutral' {
  switch (status) {
    case 'ACTIVE':
      return 'success'
    case 'INACTIVE':
      return 'danger'
    case 'EXPIRED':
      return 'neutral'
    default:
      return 'neutral'
  }
}

onMounted(() => {
  fetchPromotions()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Top Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-white tracking-tight flex items-center gap-2">
          🏷️ {{ t('adminPromotions.title') }}
        </h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ t('adminPromotions.subtitle') }}
        </p>
      </div>

      <Button variant="primary" size="md" class="shadow-lg shadow-indigo-600/30" @click="openCreateModal">
        <template #prefix>
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
        </template>
        {{ t('adminPromotions.createBtn') }}
      </Button>
    </div>

    <!-- Filter & Search Controls -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-slate-900 p-4 rounded-2xl border border-slate-800">
      <!-- Search Input -->
      <div class="relative flex-1 max-w-md">
        <input
          v-model="searchQuery"
          type="text"
          class="w-full pl-9 pr-4 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white text-xs placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          :placeholder="t('adminPromotions.searchPlaceholder')"
          @keyup.enter="handleSearch"
        />
        <svg class="w-4 h-4 text-slate-500 absolute left-3 top-2.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
      </div>

      <!-- Status Filter Tabs -->
      <div class="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0">
        <button
          v-for="status in ['ALL', 'ACTIVE', 'INACTIVE', 'EXPIRED']"
          :key="status"
          type="button"
          :class="[
            'px-3 py-1.5 rounded-lg text-xs font-bold transition-all',
            activeStatusFilter === status
              ? 'bg-indigo-600 text-white shadow-sm'
              : 'bg-slate-800 text-slate-400 hover:text-slate-200'
          ]"
          @click="handleStatusFilter(status)"
        >
          {{ status === 'ALL' ? 'Tất cả' : status }}
        </button>
      </div>
    </div>

    <!-- Error State -->
    <ErrorAlert v-if="errorMessage" :message="errorMessage" @retry="fetchPromotions" />

    <!-- Table Card -->
    <Card v-else padding="none">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-xs">
          <thead>
            <tr class="bg-slate-850 border-b border-slate-700/80 text-[11px] font-bold uppercase text-slate-400 tracking-wider">
              <th class="px-4 py-3.5">{{ t('adminPromotions.colCode') }}</th>
              <th class="px-4 py-3.5">{{ t('adminPromotions.colName') }}</th>
              <th class="px-4 py-3.5">{{ t('adminPromotions.colDiscount') }}</th>
              <th class="px-4 py-3.5">{{ t('adminPromotions.colPeriod') }}</th>
              <th class="px-4 py-3.5">{{ t('adminPromotions.colUsage') }}</th>
              <th class="px-4 py-3.5">{{ t('adminPromotions.colStatus') }}</th>
              <th class="px-4 py-3.5 text-right">{{ t('adminPromotions.colActions') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-800">
            <!-- Loading Skeleton -->
            <tr v-if="isLoading" v-for="n in 5" :key="n" class="animate-pulse">
              <td colspan="7" class="px-4 py-4"><div class="h-5 rounded bg-slate-800"></div></td>
            </tr>

            <!-- Empty State -->
            <tr v-else-if="promotions.length === 0">
              <td colspan="7" class="px-4 py-12 text-center text-slate-500">
                {{ t('adminPromotions.emptyList') }}
              </td>
            </tr>

            <!-- Data Rows -->
            <tr v-else v-for="promo in promotions" :key="promo.id" class="hover:bg-slate-800/50 transition-colors">
              <td class="px-4 py-3.5 font-mono font-bold text-indigo-400">
                {{ promo.code }}
              </td>

              <td class="px-4 py-3.5">
                <p class="font-bold text-white max-w-[200px] truncate" :title="promo.name">{{ promo.name }}</p>
                <p v-if="promo.description" class="text-[10px] text-slate-400 truncate max-w-[200px]">{{ promo.description }}</p>
              </td>

              <td class="px-4 py-3.5 font-semibold text-emerald-400">
                <template v-if="promo.discountType === 'PERCENTAGE'">
                  {{ promo.discountValue }}%
                  <span v-if="promo.maxDiscountAmount" class="block text-[10px] text-slate-400 font-normal">
                    (Max: {{ formatCurrency(promo.maxDiscountAmount) }})
                  </span>
                </template>
                <template v-else>
                  {{ formatCurrency(promo.discountValue) }}
                </template>
                <span v-if="promo.minOrderAmount" class="block text-[10px] text-slate-500 font-normal">
                  Đơn tối thiểu: {{ formatCurrency(promo.minOrderAmount) }}
                </span>
              </td>

              <td class="px-4 py-3.5 text-slate-300 text-[11px] whitespace-nowrap">
                <span>{{ formatDate(promo.startAt) }}</span>
                <span class="text-slate-500 block">đến {{ formatDate(promo.endAt) }}</span>
              </td>

              <td class="px-4 py-3.5">
                <div class="space-y-1">
                  <div class="flex justify-between text-[10px] text-slate-400">
                    <span>{{ promo.usedCount }} / {{ promo.usageLimit || '∞' }}</span>
                  </div>
                  <div v-if="promo.usageLimit" class="w-24 h-1.5 rounded-full bg-slate-800 overflow-hidden">
                    <div
                      class="h-full bg-indigo-500 rounded-full"
                      :style="{ width: `${Math.min(100, (promo.usedCount / promo.usageLimit) * 100)}%` }"
                    ></div>
                  </div>
                </div>
              </td>

              <td class="px-4 py-3.5">
                <Badge :variant="getStatusBadgeVariant(promo.status)" size="sm">
                  {{ formatStatus(promo.status) }}
                </Badge>
              </td>

              <td class="px-4 py-3.5 text-right space-x-1.5 whitespace-nowrap">
                <Button variant="ghost" size="sm" @click="openEditModal(promo)">
                  {{ t('common.edit') }}
                </Button>

                <Button
                  variant="ghost"
                  size="sm"
                  :loading="isActionLoading[promo.id]"
                  :class="promo.status === 'ACTIVE' ? 'text-amber-400 hover:text-amber-300' : 'text-emerald-400 hover:text-emerald-300'"
                  @click="handleToggleStatus(promo)"
                >
                  {{ promo.status === 'ACTIVE' ? t('adminPromotions.deactivateBtn') : t('adminPromotions.activateBtn') }}
                </Button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="p-4 border-t border-slate-800 flex items-center justify-between">
        <span class="text-xs text-slate-400">
          Tổng cộng {{ totalElements }} khuyến mãi
        </span>

        <div class="flex items-center gap-2">
          <Button variant="secondary" size="sm" :disabled="currentPage === 0" @click="setPage(currentPage - 1)">
            ←
          </Button>
          <span class="text-xs text-slate-300 px-2 font-semibold">
            {{ currentPage + 1 }} / {{ totalPages }}
          </span>
          <Button variant="secondary" size="sm" :disabled="currentPage >= totalPages - 1" @click="setPage(currentPage + 1)">
            →
          </Button>
        </div>
      </div>
    </Card>

    <!-- Create / Edit Promotion Modal -->
    <Teleport to="body">
      <transition name="modal">
        <div
          v-if="isModalOpen"
          class="fixed inset-0 z-50 overflow-y-auto bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 sm:p-6"
          @click.self="isModalOpen = false"
        >
          <div class="relative w-full max-w-2xl rounded-3xl bg-slate-900 border border-slate-800 shadow-2xl p-6 sm:p-8 space-y-5 animate-scale">
            <div class="flex items-center justify-between border-b border-slate-800 pb-3">
              <h2 class="text-lg font-bold text-white tracking-tight">
                {{ isEditing ? t('adminPromotions.editModalTitle') : t('adminPromotions.createModalTitle') }}
              </h2>
              <button
                type="button"
                class="w-8 h-8 rounded-full bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white flex items-center justify-center text-sm"
                @click="isModalOpen = false"
              >
                ✕
              </button>
            </div>

            <ErrorAlert v-if="formError" :message="formError" />

            <form class="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs" @submit.prevent="handleSavePromotion">
              <!-- Code (Immutable in Edit) -->
              <div>
                <label for="promoCodeInput" class="block font-semibold text-slate-300 mb-1">
                  {{ t('adminPromotions.formCode') }} *
                </label>
                <input
                  id="promoCodeInput"
                  v-model="formData.code"
                  type="text"
                  :disabled="isEditing"
                  class="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono uppercase focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-50"
                  placeholder="VD: SUMMER2026"
                  required
                />
              </div>

              <!-- Name -->
              <div>
                <label for="promoNameInput" class="block font-semibold text-slate-300 mb-1">
                  {{ t('adminPromotions.formName') }} *
                </label>
                <input
                  id="promoNameInput"
                  v-model="formData.name"
                  type="text"
                  class="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="VD: Giảm 20% mùa hè"
                  required
                />
              </div>

              <!-- Description (full width) -->
              <div class="sm:col-span-2">
                <label for="promoDescInput" class="block font-semibold text-slate-300 mb-1">
                  {{ t('adminPromotions.formDescription') }}
                </label>
                <input
                  id="promoDescInput"
                  v-model="formData.description"
                  type="text"
                  class="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="Mô tả ngắn gọn về chương trình ưu đãi..."
                />
              </div>

              <!-- Discount Type -->
              <div>
                <label for="promoDiscountTypeSelect" class="block font-semibold text-slate-300 mb-1">
                  {{ t('adminPromotions.formDiscountType') }} *
                </label>
                <select
                  id="promoDiscountTypeSelect"
                  v-model="formData.discountType"
                  :disabled="isEditing"
                  class="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-50"
                >
                  <option value="PERCENTAGE">Giảm theo % (PERCENTAGE)</option>
                  <option value="FIXED_AMOUNT">Giảm số tiền cố định (FIXED_AMOUNT)</option>
                </select>
              </div>

              <!-- Discount Value -->
              <div>
                <label for="promoDiscountValueInput" class="block font-semibold text-slate-300 mb-1">
                  {{ formData.discountType === 'PERCENTAGE' ? 'Giá trị giảm (%)' : 'Số tiền giảm (₫)' }} *
                </label>
                <input
                  id="promoDiscountValueInput"
                  v-model.number="formData.discountValue"
                  type="number"
                  step="any"
                  :disabled="isEditing"
                  class="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-50"
                  placeholder="VD: 20 hoặc 50000"
                  required
                />
              </div>

              <!-- Max Discount (for PERCENTAGE) -->
              <div v-if="formData.discountType === 'PERCENTAGE'">
                <label for="promoMaxDiscountInput" class="block font-semibold text-slate-300 mb-1">
                  Giảm tối đa (₫)
                </label>
                <input
                  id="promoMaxDiscountInput"
                  v-model.number="formData.maxDiscountAmount"
                  type="number"
                  step="1000"
                  class="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="VD: 50000"
                />
              </div>

              <!-- Min Order Amount -->
              <div>
                <label for="promoMinOrderInput" class="block font-semibold text-slate-300 mb-1">
                  Đơn hàng tối thiểu (₫)
                </label>
                <input
                  id="promoMinOrderInput"
                  v-model.number="formData.minOrderAmount"
                  type="number"
                  step="1000"
                  class="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="VD: 100000 (để trống nếu không yêu cầu)"
                />
              </div>

              <!-- Usage Limit -->
              <div>
                <label for="promoUsageLimitInput" class="block font-semibold text-slate-300 mb-1">
                  Số lượng phát hành (Usage Limit)
                </label>
                <input
                  id="promoUsageLimitInput"
                  v-model.number="formData.usageLimit"
                  type="number"
                  class="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="VD: 100 (để trống nếu không giới hạn)"
                />
              </div>

              <!-- Start Date -->
              <div>
                <label for="promoStartAtInput" class="block font-semibold text-slate-300 mb-1">
                  Bắt đầu có hiệu lực *
                </label>
                <input
                  id="promoStartAtInput"
                  v-model="formData.startAt"
                  type="datetime-local"
                  class="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  required
                />
              </div>

              <!-- End Date -->
              <div>
                <label for="promoEndAtInput" class="block font-semibold text-slate-300 mb-1">
                  Hết hiệu lực *
                </label>
                <input
                  id="promoEndAtInput"
                  v-model="formData.endAt"
                  type="datetime-local"
                  class="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  required
                />
              </div>

              <!-- Action Buttons (full width) -->
              <div class="sm:col-span-2 pt-4 border-t border-slate-800 flex items-center justify-end gap-3">
                <Button variant="secondary" size="md" :disabled="isSaving" @click="isModalOpen = false">
                  {{ t('common.cancel') }}
                </Button>
                <Button variant="primary" size="md" :loading="isSaving" type="submit">
                  {{ isEditing ? t('common.saveChanges') : t('adminPromotions.createSubmitBtn') }}
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
