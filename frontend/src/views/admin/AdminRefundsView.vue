<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { RefundResponse, RefundStatus } from '@/types/refund.types'
import paymentService from '@/services/payment.service'
import { formatCurrency, formatDate, formatTime, formatStatus } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import { useToast } from '@/composables/useToast'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Badge from '@/components/common/Badge.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import RefundDetailModal from '@/components/payment/RefundDetailModal.vue'

const toast = useToast()
const { t } = useI18n()

const refunds = ref<RefundResponse[]>([])
const activeStatusFilter = ref<string>('ALL')

const currentPage = ref<number>(0)
const totalPages = ref<number>(1)
const totalElements = ref<number>(0)

const isLoading = ref<boolean>(true)
const errorMessage = ref<string>('')

// Detail Modal
const isDetailModalOpen = ref<boolean>(false)
const selectedRefund = ref<RefundResponse | null>(null)

// Admin Manual Refund Modal
const isAdminRefundModalOpen = ref<boolean>(false)
const manualBookingId = ref<string>('')
const manualReason = ref<string>('')
const isProcessingAdminRefund = ref<boolean>(false)
const adminRefundError = ref<string>('')

async function fetchRefunds() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const statusParam =
      activeStatusFilter.value !== 'ALL'
        ? (activeStatusFilter.value as RefundStatus)
        : undefined

    const res = await paymentService.getAdminRefunds({
      status: statusParam,
      page: currentPage.value,
      size: 10,
      sort: 'createdAt,desc',
    })

    refunds.value = res.content || []
    totalPages.value = res.totalPages || 1
    totalElements.value = res.totalElements || 0
  } catch (err: any) {
    errorMessage.value =
      err.response?.data?.message || 'Không thể tải danh sách hoàn tiền.'
  } finally {
    isLoading.value = false
  }
}

function handleStatusFilter(status: string) {
  activeStatusFilter.value = status
  currentPage.value = 0
  fetchRefunds()
}

function setPage(page: number) {
  currentPage.value = page
  fetchRefunds()
}

function openDetailModal(refund: RefundResponse) {
  selectedRefund.value = refund
  isDetailModalOpen.value = true
}

function openAdminRefundModal() {
  manualBookingId.value = ''
  manualReason.value = ''
  adminRefundError.value = ''
  isAdminRefundModalOpen.value = true
}

async function handleExecuteAdminRefund() {
  adminRefundError.value = ''

  if (!manualBookingId.value.trim()) {
    adminRefundError.value = 'Vui lòng nhập ID đơn đặt vé (Booking UUID).'
    return
  }

  isProcessingAdminRefund.value = true
  try {
    const res = await paymentService.adminRefundBooking(manualBookingId.value.trim(), {
      reason: manualReason.value.trim() || 'Admin can thiệp hoàn tiền',
    })

    toast.success(
      'Hoàn tiền thành công!',
      `Mã hoàn tiền: ${res.refundCode} (${formatCurrency(res.amount)})`
    )

    isAdminRefundModalOpen.value = false
    await fetchRefunds()
  } catch (err: any) {
    adminRefundError.value =
      err.response?.data?.message || 'Không thể thực hiện hoàn tiền cho đơn đặt vé này.'
  } finally {
    isProcessingAdminRefund.value = false
  }
}

function getStatusBadgeVariant(status: RefundStatus): 'success' | 'warning' | 'danger' {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'PENDING':
      return 'warning'
    case 'FAILED':
      return 'danger'
    default:
      return 'warning'
  }
}

onMounted(() => {
  fetchRefunds()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Top Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-white tracking-tight flex items-center gap-2">
          💸 {{ t('adminRefunds.title') }}
        </h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ t('adminRefunds.subtitle') }}
        </p>
      </div>

      <Button variant="danger" size="md" class="shadow-lg shadow-rose-600/30" @click="openAdminRefundModal">
        <template #prefix>
          <span>⚡</span>
        </template>
        {{ t('adminRefunds.forceRefundBtn') }}
      </Button>
    </div>

    <!-- Filter Bar -->
    <div class="flex items-center gap-2 bg-slate-900 p-4 rounded-2xl border border-slate-800 overflow-x-auto">
      <button
        v-for="status in ['ALL', 'SUCCESS', 'PENDING', 'FAILED']"
        :key="status"
        type="button"
        :class="[
          'px-3.5 py-1.5 rounded-lg text-xs font-bold transition-all',
          activeStatusFilter === status
            ? 'bg-indigo-600 text-white shadow-sm'
            : 'bg-slate-800 text-slate-400 hover:text-slate-200'
        ]"
        @click="handleStatusFilter(status)"
      >
        {{ status === 'ALL' ? 'Tất cả trạng thái' : status }}
      </button>
    </div>

    <!-- Error State -->
    <ErrorAlert v-if="errorMessage" :message="errorMessage" @retry="fetchRefunds" />

    <!-- Table Card -->
    <Card v-else padding="none">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-xs">
          <thead>
            <tr class="bg-slate-850 border-b border-slate-700/80 text-[11px] font-bold uppercase text-slate-400 tracking-wider">
              <th class="px-4 py-3.5">{{ t('adminRefunds.colRefundCode') }}</th>
              <th class="px-4 py-3.5">{{ t('adminRefunds.colBookingCode') }}</th>
              <th class="px-4 py-3.5">{{ t('adminRefunds.colAmount') }}</th>
              <th class="px-4 py-3.5">{{ t('adminRefunds.colReason') }}</th>
              <th class="px-4 py-3.5">{{ t('adminRefunds.colProcessedAt') }}</th>
              <th class="px-4 py-3.5">{{ t('adminRefunds.colStatus') }}</th>
              <th class="px-4 py-3.5 text-right">{{ t('adminRefunds.colActions') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-800">
            <!-- Loading Skeleton -->
            <tr v-if="isLoading" v-for="n in 5" :key="n" class="animate-pulse">
              <td colspan="7" class="px-4 py-4"><div class="h-5 rounded bg-slate-800"></div></td>
            </tr>

            <!-- Empty State -->
            <tr v-else-if="refunds.length === 0">
              <td colspan="7" class="px-4 py-12 text-center text-slate-500">
                {{ t('adminRefunds.emptyList') }}
              </td>
            </tr>

            <!-- Data Rows -->
            <tr v-else v-for="rf in refunds" :key="rf.id" class="hover:bg-slate-800/50 transition-colors">
              <td class="px-4 py-3.5 font-mono font-bold text-indigo-400">
                {{ rf.refundCode }}
              </td>

              <td class="px-4 py-3.5 font-mono font-semibold text-slate-300">
                {{ rf.bookingCode }}
              </td>

              <td class="px-4 py-3.5 font-black text-emerald-400 text-sm">
                {{ formatCurrency(rf.amount) }}
              </td>

              <td class="px-4 py-3.5 text-slate-300 max-w-[200px] truncate" :title="rf.refundReason">
                {{ rf.refundReason || '---' }}
              </td>

              <td class="px-4 py-3.5 text-slate-400 whitespace-nowrap">
                {{ rf.processedAt ? formatTime(rf.processedAt) + ' ' + formatDate(rf.processedAt) : (rf.createdAt ? formatDate(rf.createdAt) : '---') }}
              </td>

              <td class="px-4 py-3.5">
                <Badge :variant="getStatusBadgeVariant(rf.refundStatus)" size="sm">
                  {{ formatStatus(rf.refundStatus) }}
                </Badge>
              </td>

              <td class="px-4 py-3.5 text-right">
                <Button variant="ghost" size="sm" @click="openDetailModal(rf)">
                  {{ t('adminRefunds.viewDetailBtn') }}
                </Button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="p-4 border-t border-slate-800 flex items-center justify-between">
        <span class="text-xs text-slate-400">
          Tổng cộng {{ totalElements }} giao dịch hoàn tiền
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

    <!-- Refund Detail Modal -->
    <RefundDetailModal
      :is-open="isDetailModalOpen"
      :refund="selectedRefund"
      @close="isDetailModalOpen = false"
    />

    <!-- Admin Forceful Refund Modal -->
    <Teleport to="body">
      <transition name="modal">
        <div
          v-if="isAdminRefundModalOpen"
          class="fixed inset-0 z-50 overflow-y-auto bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 sm:p-6"
          @click.self="isAdminRefundModalOpen = false"
        >
          <div class="relative w-full max-w-lg rounded-3xl bg-slate-900 border border-slate-800 shadow-2xl p-6 sm:p-8 space-y-5 animate-scale">
            <div class="flex items-center justify-between border-b border-slate-800 pb-3">
              <div class="flex items-center gap-2.5 text-rose-400">
                <span class="text-xl">⚡</span>
                <h2 class="text-lg font-bold text-white tracking-tight">
                  {{ t('adminRefunds.adminRefundModalTitle') }}
                </h2>
              </div>
              <button
                type="button"
                class="w-8 h-8 rounded-full bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white flex items-center justify-center text-sm"
                @click="isAdminRefundModalOpen = false"
              >
                ✕
              </button>
            </div>

            <!-- Financial Exception Info Box -->
            <div class="p-3.5 rounded-xl bg-slate-950 border border-slate-800 text-xs text-slate-300 space-y-1">
              <p class="font-bold text-amber-300">
                💡 {{ t('adminRefunds.adminRefundNoticeTitle') }}
              </p>
              <p class="text-[11px] text-slate-400 leading-relaxed">
                {{ t('adminRefunds.adminRefundNoticeDesc') }}
              </p>
            </div>

            <ErrorAlert v-if="adminRefundError" :message="adminRefundError" />

            <form class="space-y-4 text-xs" @submit.prevent="handleExecuteAdminRefund">
              <div>
                <label for="adminBookingIdInput" class="block font-semibold text-slate-300 mb-1">
                  {{ t('adminRefunds.bookingIdLabel') }} *
                </label>
                <input
                  id="adminBookingIdInput"
                  v-model="manualBookingId"
                  type="text"
                  class="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white font-mono focus:outline-none focus:ring-2 focus:ring-rose-500"
                  placeholder="Nhập UUID đơn đặt vé (VD: 1e2f98b8-...)"
                  required
                />
              </div>

              <div>
                <label for="adminRefundReasonInput" class="block font-semibold text-slate-300 mb-1">
                  {{ t('refund.reasonLabel') }}
                </label>
                <textarea
                  id="adminRefundReasonInput"
                  v-model="manualReason"
                  rows="2"
                  class="w-full px-3 py-2 rounded-xl bg-slate-950 border border-slate-700 text-white focus:outline-none focus:ring-2 focus:ring-rose-500"
                  placeholder="VD: Xử lý ngoại lệ thanh toán thành công nhưng đơn hàng quá hạn giữ chỗ..."
                ></textarea>
              </div>

              <div class="pt-3 border-t border-slate-800 flex items-center justify-end gap-3">
                <Button variant="secondary" size="md" :disabled="isProcessingAdminRefund" @click="isAdminRefundModalOpen = false">
                  {{ t('common.cancel') }}
                </Button>
                <Button variant="danger" size="md" :loading="isProcessingAdminRefund" type="submit">
                  {{ t('adminRefunds.executeRefundBtn') }}
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

