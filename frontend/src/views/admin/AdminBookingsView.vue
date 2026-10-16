<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import type { BookingDetailResponse, BookingSummaryResponse, BookingStatus } from '@/types/booking.types'
import bookingService from '@/services/booking.service'
import { formatCurrency, formatStandardDateTime, formatDate, formatTime } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Input from '@/components/common/Input.vue'
import Badge from '@/components/common/Badge.vue'
import Pagination from '@/components/common/Pagination.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import Modal from '@/components/common/Modal.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useToast } from '@/composables/useToast'

const { t } = useI18n()
const toast = useToast()

const bookings = ref<BookingSummaryResponse[]>([])
const isLoading = ref(true)
const errorMessage = ref('')

// Filters
const searchQuery = ref('')
const selectedStatus = ref<string>('ALL')
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const pageSize = ref(10)

// Modal states
const isDetailModalOpen = ref(false)
const selectedBooking = ref<BookingDetailResponse | null>(null)
const isCancelModalOpen = ref(false)
const cancelReason = ref('')
const isCancelling = ref(false)

function getStatusBadgeVariant(status: BookingStatus) {
  switch (status) {
    case 'PAID':
      return 'success'
    case 'PENDING_PAYMENT':
      return 'warning'
    case 'CANCELLED':
      return 'danger'
    case 'REFUNDED':
      return 'info'
    case 'EXPIRED':
      return 'neutral'
    default:
      return 'neutral'
  }
}

function getStatusLabel(status: BookingStatus) {
  switch (status) {
    case 'PAID':
      return t('status.PAID')
    case 'PENDING_PAYMENT':
      return t('status.PENDING_PAYMENT')
    case 'CANCELLED':
      return t('status.CANCELLED')
    case 'REFUNDED':
      return t('status.REFUNDED')
    case 'EXPIRED':
      return t('status.EXPIRED')
    default:
      return status
  }
}

function hasRefundInfo(booking: BookingDetailResponse): boolean {
  return !!booking.payments?.some((p) => !!p.refund)
}

async function fetchBookings() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const res = await bookingService.getAdminBookings({
      q: searchQuery.value.trim() || undefined,
      status: selectedStatus.value !== 'ALL' ? (selectedStatus.value as BookingStatus) : undefined,
      page: currentPage.value,
      size: pageSize.value,
    })

    bookings.value = res.content || []
    totalPages.value = res.totalPages || 0
    totalElements.value = res.totalElements || 0
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
  } finally {
    isLoading.value = false
  }
}

async function openDetailModal(booking: BookingSummaryResponse) {
  try {
    const detail = await bookingService.getBookingDetail(booking.id)
    selectedBooking.value = detail
    isDetailModalOpen.value = true
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  }
}

function promptCancelBooking(booking: BookingSummaryResponse) {
  selectedBooking.value = null
  bookingService.getBookingDetail(booking.id).then((b) => {
    selectedBooking.value = b
  }).catch(() => {})
  cancelReason.value = ''
  isCancelModalOpen.value = true
}

async function confirmCancelBooking() {
  if (!selectedBooking.value) return

  isCancelling.value = true
  try {
    await bookingService.cancelAdminBooking(selectedBooking.value.id, {
      reason: cancelReason.value.trim() || undefined,
    })
    toast.success(t('myBookings.cancelSuccess'))
    isCancelModalOpen.value = false
    await fetchBookings()
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('myBookings.cancelFailed'))
  } finally {
    isCancelling.value = false
  }
}

function onPageChange(page: number) {
  currentPage.value = page
  fetchBookings()
}

// Watch filters with debounce
let debounceTimer: any = null
watch(searchQuery, () => {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    currentPage.value = 0
    fetchBookings()
  }, 400)
})

watch(selectedStatus, () => {
  currentPage.value = 0
  fetchBookings()
})

onMounted(() => {
  fetchBookings()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-white tracking-tight">{{ t('adminBookings.title') }}</h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ t('adminBookings.subtitle') }}
        </p>
      </div>

      <Button variant="secondary" size="md" :loading="isLoading" @click="fetchBookings">
        <template #prefix>
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </template>
        {{ t('common.refresh') }}
      </Button>
    </div>

    <!-- Error Alert -->
    <ErrorAlert v-if="errorMessage" :message="errorMessage" />

    <!-- Filters Bar -->
    <Card padding="sm">
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div class="sm:col-span-2">
          <Input
            v-model="searchQuery"
            :placeholder="t('adminBookings.searchPlaceholder')"
            clearable
          >
            <template #prefix>
              <svg class="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </template>
          </Input>
        </div>

        <div>
          <select
            v-model="selectedStatus"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="ALL">{{ t('adminBookings.allStatuses') }}</option>
            <option value="PAID">{{ t('status.PAID') }}</option>
            <option value="PENDING_PAYMENT">{{ t('status.PENDING_PAYMENT') }}</option>
            <option value="CANCELLED">{{ t('status.CANCELLED') }}</option>
            <option value="REFUNDED">{{ t('status.REFUNDED') }}</option>
            <option value="EXPIRED">{{ t('status.EXPIRED') }}</option>
          </select>
        </div>
      </div>
    </Card>

    <!-- Bookings Table -->
    <Card padding="none">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-sm">
          <thead>
            <tr class="bg-slate-850 border-b border-slate-700/80 text-xs font-semibold uppercase text-slate-400 tracking-wider">
              <th class="px-4 py-3">{{ t('adminBookings.colBookingCode') }}</th>
              <th class="px-4 py-3">{{ t('adminBookings.colCustomer') }}</th>
              <th class="px-4 py-3">{{ t('adminBookings.colMovieShowtime') }}</th>
              <th class="px-4 py-3">{{ t('adminBookings.colSeats') }}</th>
              <th class="px-4 py-3">{{ t('adminBookings.colAmount') }}</th>
              <th class="px-4 py-3">{{ t('adminBookings.colStatus') }}</th>
              <th class="px-4 py-3 text-right">{{ t('adminBookings.colActions') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-700/60">
            <!-- Loading Skeleton Rows -->
            <template v-if="isLoading">
              <tr v-for="i in 6" :key="'skel-bk-' + i" class="animate-pulse">
                <td class="px-4 py-4"><div class="h-4 w-20 bg-slate-800 rounded"></div></td>
                <td class="px-4 py-4">
                  <div class="flex items-center gap-2">
                    <div class="w-7 h-7 rounded-full bg-slate-800"></div>
                    <div class="h-4 w-28 bg-slate-800 rounded"></div>
                  </div>
                </td>
                <td class="px-4 py-4"><div class="h-4 w-36 bg-slate-800 rounded"></div></td>
                <td class="px-4 py-4"><div class="h-4 w-16 bg-slate-800 rounded"></div></td>
                <td class="px-4 py-4"><div class="h-4 w-20 bg-slate-800 rounded"></div></td>
                <td class="px-4 py-4"><div class="h-6 w-20 bg-slate-800 rounded-full"></div></td>
                <td class="px-4 py-4 text-right"><div class="h-8 w-20 bg-slate-800 rounded ml-auto"></div></td>
              </tr>
            </template>

            <!-- Empty State -->
            <tr v-else-if="bookings.length === 0">
              <td colspan="7" class="px-4 py-16 text-center text-slate-400">
                <div class="max-w-sm mx-auto space-y-2">
                  <svg class="w-10 h-10 mx-auto text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z" />
                  </svg>
                  <p class="text-sm font-medium text-slate-300">{{ t('adminBookings.emptyList') }}</p>
                  <p class="text-xs text-slate-500">{{ t('common.emptyDesc') }}</p>
                </div>
              </td>
            </tr>

            <!-- Rows -->
            <tr
              v-for="b in bookings"
              :key="b.id"
              class="hover:bg-slate-750/70 transition-colors"
            >
              <td class="px-4 py-3.5 font-mono text-xs">
                <span class="font-bold text-indigo-400">{{ b.bookingCode }}</span>
                <p v-if="b.createdAt" class="text-[11px] text-slate-500 font-sans mt-0.5">
                  {{ formatStandardDateTime(b.createdAt) }}
                </p>
              </td>
              <td class="px-4 py-3.5">
                <div class="flex items-center gap-2.5">
                  <UserAvatar :src="b.user?.avatarUrl" :name="b.user?.fullName" size="sm" />
                  <div class="text-xs truncate max-w-[150px]">
                    <p class="font-medium text-slate-200 truncate">{{ b.user?.fullName || 'Khách vãng lai' }}</p>
                    <p class="text-[11px] text-slate-400 truncate">{{ b.user?.email || '—' }}</p>
                  </div>
                </div>
              </td>
              <td class="px-4 py-3.5 text-xs">
                <p class="font-bold text-slate-200 line-clamp-1">{{ b.movieTitle || b.showtime?.movieTitle || '—' }}</p>
                <p class="text-slate-400 text-[11px] mt-0.5">{{ b.cinemaName || b.showtime?.cinemaName }} • {{ b.showtime?.auditoriumName }}</p>
                <p v-if="b.showtime?.startTime" class="text-indigo-400/80 text-[11px] mt-0.5 font-mono">
                  {{ formatDate(b.showtime.startTime) }} {{ formatTime(b.showtime.startTime) }}<template v-if="b.showtime.endTime"> - {{ formatTime(b.showtime.endTime) }}</template>
                </p>
              </td>
              <td class="px-4 py-3.5 font-mono text-xs text-slate-300">
                <div v-if="b.seatCodes && b.seatCodes.length > 0" class="flex flex-wrap gap-1 max-w-[160px]">
                  <span
                    v-for="code in b.seatCodes"
                    :key="code"
                    class="px-1.5 py-0.5 rounded bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 text-[11px] font-bold"
                  >
                    {{ code }}
                  </span>
                </div>
                <span v-else class="px-2 py-0.5 rounded bg-slate-800 text-[11px] border border-slate-700 font-bold text-slate-400">
                  {{ b.seatsCount || b.seatCount || 1 }} {{ t('adminReports.unitTickets') }}
                </span>
              </td>
              <td class="px-4 py-3.5 font-mono font-bold text-emerald-400 text-xs">
                {{ formatCurrency(b.totalAmount) }}
              </td>
              <td class="px-4 py-3.5">
                <Badge :variant="getStatusBadgeVariant(b.bookingStatus)">
                  {{ getStatusLabel(b.bookingStatus) }}
                </Badge>
              </td>
              <td class="px-4 py-3.5 text-right">
                <div class="flex items-center justify-end gap-1.5">
                  <Button variant="secondary" size="sm" @click="openDetailModal(b)">
                    {{ t('adminBookings.viewDetailBtn') }}
                  </Button>
                  <Button
                    v-if="b.bookingStatus === 'PENDING_PAYMENT'"
                    variant="danger"
                    size="sm"
                    @click="promptCancelBooking(b)"
                  >
                    {{ t('adminBookings.cancelBtn') }}
                  </Button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="p-4 border-t border-slate-700/80">
        <Pagination
          :current-page="currentPage"
          :total-pages="totalPages"
          @page-change="onPageChange"
        />
      </div>
    </Card>

    <!-- Booking Detail Modal -->
    <Modal
      v-model="isDetailModalOpen"
      :title="t('adminBookings.detailModalTitle')"
      size="lg"
      @close="isDetailModalOpen = false"
    >
      <div v-if="selectedBooking" class="space-y-4 text-xs text-slate-300">
        <!-- Header summary -->
        <div class="flex flex-wrap items-center justify-between gap-2 border-b border-slate-800 pb-3">
          <div>
            <span class="text-slate-400">{{ t('adminBookings.colBookingCode') }}:</span>
            <strong class="font-mono text-base text-indigo-400 ml-2 font-bold">{{ selectedBooking.bookingCode }}</strong>
            <span v-if="selectedBooking.checkInCode" class="ml-3 font-mono px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-xs">
              Check-in: {{ selectedBooking.checkInCode }}
            </span>
          </div>
          <Badge :variant="getStatusBadgeVariant(selectedBooking.bookingStatus)">
            {{ getStatusLabel(selectedBooking.bookingStatus) }}
          </Badge>
        </div>

        <!-- 1. Thông tin đơn đặt vé -->
        <div class="bg-slate-850 p-3.5 rounded-lg border border-slate-800 space-y-2.5">
          <h4 class="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
            <svg class="w-3.5 h-3.5 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
            Thông tin đơn đặt vé
          </h4>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
            <div>
              <p class="text-slate-400">Thời gian đặt vé</p>
              <p class="font-mono font-medium text-white mt-0.5">
                {{ formatStandardDateTime(selectedBooking.createdAt) || '—' }}
              </p>
            </div>
            <div v-if="selectedBooking.updatedAt">
              <p class="text-slate-400">Thời gian cập nhật gần nhất</p>
              <p class="font-mono font-medium text-white mt-0.5">
                {{ formatStandardDateTime(selectedBooking.updatedAt) }}
              </p>
            </div>
            <div v-if="selectedBooking.holdExpiresAt && selectedBooking.bookingStatus === 'PENDING_PAYMENT'">
              <p class="text-slate-400">Thời gian hết hạn giữ chỗ</p>
              <p class="font-mono font-medium text-amber-400 mt-0.5">
                {{ formatStandardDateTime(selectedBooking.holdExpiresAt) }}
              </p>
            </div>
            <div>
              <p class="text-slate-400">Khách hàng</p>
              <p class="font-medium text-white mt-0.5">
                {{ selectedBooking.user?.fullName || 'Khách vãng lai' }}
                <span v-if="selectedBooking.user?.email" class="text-slate-400 font-normal"> ({{ selectedBooking.user.email }})</span>
              </p>
            </div>
          </div>
        </div>

        <!-- 2. Thông tin suất chiếu -->
        <div class="bg-slate-850 p-3.5 rounded-lg border border-slate-800 space-y-2.5">
          <h4 class="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
            <svg class="w-3.5 h-3.5 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
            </svg>
            Thông tin suất chiếu
          </h4>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
            <div>
              <p class="text-slate-400">Phim</p>
              <p class="font-bold text-white text-sm mt-0.5">{{ selectedBooking.showtime?.movie?.title || '—' }}</p>
              <p class="text-[11px] text-slate-400 mt-0.5">
                {{ selectedBooking.showtime?.cinema?.name }} • {{ selectedBooking.showtime?.auditorium?.name }}
                <span v-if="selectedBooking.showtime?.format"> ({{ selectedBooking.showtime.format }})</span>
              </p>
            </div>
            <div>
              <p class="text-slate-400">Thời gian chiếu</p>
              <p class="font-mono font-bold text-indigo-300 text-sm mt-0.5">
                {{ formatDate(selectedBooking.showtime?.startTime) }}
              </p>
              <p class="font-mono text-slate-300 text-xs mt-0.5" v-if="selectedBooking.showtime?.startTime">
                {{ formatTime(selectedBooking.showtime.startTime) }}<template v-if="selectedBooking.showtime?.endTime"> - {{ formatTime(selectedBooking.showtime.endTime) }}</template>
              </p>
            </div>
          </div>
        </div>

        <!-- 3. Thông tin thanh toán (Nếu có thanh toán) -->
        <div v-if="selectedBooking.payments && selectedBooking.payments.length > 0" class="bg-slate-850 p-3.5 rounded-lg border border-slate-800 space-y-2.5">
          <h4 class="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
            <svg class="w-3.5 h-3.5 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
            </svg>
            Thông tin thanh toán
          </h4>
          <div
            v-for="(pm, idx) in selectedBooking.payments"
            :key="pm.id || idx"
            class="space-y-2 border-b border-slate-800/80 last:border-b-0 pb-2.5 last:pb-0"
          >
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div>
                <p class="text-slate-400">Phương thức & Mã GD</p>
                <p class="font-medium text-white mt-0.5">
                  {{ pm.paymentMethod }} — <span class="font-mono text-indigo-300">{{ pm.paymentCode }}</span>
                </p>
                <p v-if="pm.gatewayTransactionId" class="text-[11px] text-slate-400 font-mono mt-0.5">
                  Mã cổng GD: {{ pm.gatewayTransactionId }}
                </p>
              </div>
              <div>
                <p class="text-slate-400">Thời gian thanh toán</p>
                <p v-if="pm.paidAt" class="font-mono font-semibold text-emerald-400 mt-0.5">
                  {{ formatStandardDateTime(pm.paidAt) }}
                </p>
                <p v-else-if="pm.createdAt" class="font-mono text-slate-300 mt-0.5">
                  Khởi tạo: {{ formatStandardDateTime(pm.createdAt) }} ({{ pm.paymentStatus }})
                </p>
                <p v-else class="text-slate-400 mt-0.5">—</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 4. Thông tin hủy / hoàn tiền (Nếu có) -->
        <div
          v-if="selectedBooking.cancelledAt || hasRefundInfo(selectedBooking)"
          class="bg-rose-500/5 p-3.5 rounded-lg border border-rose-500/20 space-y-2.5"
        >
          <h4 class="text-xs font-semibold uppercase tracking-wider text-rose-400 flex items-center gap-1.5">
            <svg class="w-3.5 h-3.5 text-rose-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            Thông tin hủy & hoàn tiền
          </h4>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
            <div v-if="selectedBooking.cancelledAt">
              <p class="text-slate-400">Thời gian hủy</p>
              <p class="font-mono font-medium text-rose-300 mt-0.5">
                {{ formatStandardDateTime(selectedBooking.cancelledAt) }}
              </p>
              <p v-if="selectedBooking.cancelledReason" class="text-[11px] text-slate-400 mt-0.5">
                Lý do: {{ selectedBooking.cancelledReason }}
              </p>
            </div>
            <!-- Refund info from payments -->
            <template v-for="pm in selectedBooking.payments" :key="'ref-' + pm.id">
              <div v-if="pm.refund">
                <p class="text-slate-400">Thời gian xử lý hoàn tiền</p>
                <p class="font-mono font-medium text-amber-300 mt-0.5">
                  {{ formatStandardDateTime(pm.refund.processedAt || pm.refund.createdAt) }}
                </p>
                <p class="text-[11px] text-slate-400 mt-0.5">
                  Mã hoàn: <span class="font-mono text-slate-200">{{ pm.refund.refundCode }}</span> • {{ formatCurrency(pm.refund.amount) }} ({{ pm.refund.refundStatus }})
                </p>
                <p v-if="pm.refund.refundReason" class="text-[11px] text-slate-400">
                  Lý do hoàn: {{ pm.refund.refundReason }}
                </p>
              </div>
            </template>
          </div>
        </div>

        <!-- 5. Thông tin vé & Ghế -->
        <div class="bg-slate-850 p-3.5 rounded-lg border border-slate-800 space-y-2.5">
          <h4 class="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center justify-between">
            <span class="flex items-center gap-1.5">
              <svg class="w-3.5 h-3.5 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z" />
              </svg>
              Danh sách vé ({{ selectedBooking.tickets?.length || selectedBooking.seats?.length || 0 }})
            </span>
          </h4>
          <!-- If tickets exist with ticket details -->
          <div v-if="selectedBooking.tickets && selectedBooking.tickets.length > 0" class="divide-y divide-slate-800">
            <div
              v-for="t in selectedBooking.tickets"
              :key="t.id"
              class="py-2 flex items-center justify-between text-xs"
            >
              <div class="flex items-center gap-2.5">
                <span class="px-2 py-0.5 rounded bg-indigo-500/10 text-indigo-400 font-mono font-bold text-xs border border-indigo-500/20">
                  {{ t.seatCode }}
                </span>
                <span v-if="t.createdAt" class="text-slate-400 text-[11px] font-mono">
                  Tạo vé: {{ formatStandardDateTime(t.createdAt) }}
                </span>
              </div>
              <div class="flex items-center gap-2">
                <span class="font-mono text-slate-300">{{ formatCurrency(t.ticketPrice) }}</span>
                <Badge size="sm" :variant="t.ticketStatus === 'USED' ? 'neutral' : t.ticketStatus === 'VALID' ? 'success' : 'danger'">
                  {{ t.ticketStatus }}
                </Badge>
              </div>
            </div>
          </div>
          <!-- Fallback to seats if tickets not yet generated (e.g. pending) -->
          <div v-else-if="selectedBooking.seats && selectedBooking.seats.length > 0" class="divide-y divide-slate-800">
            <div
              v-for="s in selectedBooking.seats"
              :key="s.seatId"
              class="py-2 flex items-center justify-between text-xs"
            >
              <div class="flex items-center gap-2">
                <span class="px-2 py-0.5 rounded bg-indigo-500/10 text-indigo-400 font-mono font-bold text-xs border border-indigo-500/20">
                  {{ s.seatCode }}
                </span>
                <span class="text-slate-400 text-[11px]">{{ s.seatTypeName }}</span>
              </div>
              <span class="font-mono text-slate-300">{{ formatCurrency(s.price) }}</span>
            </div>
          </div>
        </div>

        <!-- 6. Bắp nước (F&B) nếu có -->
        <div v-if="selectedBooking.foods && selectedBooking.foods.length > 0" class="bg-slate-850 p-3.5 rounded-lg border border-slate-800 space-y-2">
          <h4 class="text-xs font-semibold uppercase tracking-wider text-slate-400">Bắp nước & Combo</h4>
          <div class="divide-y divide-slate-800">
            <div
              v-for="(f, idx) in selectedBooking.foods"
              :key="f.foodItemId || idx"
              class="py-1.5 flex items-center justify-between text-xs"
            >
              <span class="text-slate-300">{{ f.foodName }} × {{ f.quantity }}</span>
              <span class="font-mono text-slate-300">{{ formatCurrency(f.subtotal) }}</span>
            </div>
          </div>
        </div>

        <!-- 7. Tổng kết thanh toán -->
        <div class="bg-slate-800/60 p-3.5 rounded-lg border border-slate-700/60 space-y-1.5 text-xs">
          <div v-if="selectedBooking.grossAmount" class="flex justify-between text-slate-400">
            <span>Tiền vé:</span>
            <span class="font-mono">{{ formatCurrency(selectedBooking.grossAmount) }}</span>
          </div>
          <div v-if="selectedBooking.foodAmount" class="flex justify-between text-slate-400">
            <span>Tiền bắp nước:</span>
            <span class="font-mono">{{ formatCurrency(selectedBooking.foodAmount) }}</span>
          </div>
          <div v-if="selectedBooking.discountAmount" class="flex justify-between text-rose-400">
            <span>Khuyến mãi:</span>
            <span class="font-mono">-{{ formatCurrency(selectedBooking.discountAmount) }}</span>
          </div>
          <div class="flex justify-between items-center pt-2 border-t border-slate-700 font-bold text-sm">
            <span class="text-white">{{ t('adminBookings.colAmount') }}:</span>
            <span class="font-mono text-emerald-400 text-base">{{ formatCurrency(selectedBooking.totalAmount) }}</span>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end">
          <Button variant="secondary" size="md" @click="isDetailModalOpen = false">
            {{ t('common.close') }}
          </Button>
        </div>
      </template>
    </Modal>

    <!-- Cancel Modal -->
    <Modal
      v-model="isCancelModalOpen"
      :title="t('adminBookings.cancelModalTitle')"
      @close="isCancelModalOpen = false"
    >
      <div class="space-y-4 text-sm text-slate-300">
        <p>
          {{ t('myBookings.cancelConfirmDesc', { code: selectedBooking?.bookingCode || '' }) }}
        </p>

        <Input
          v-model="cancelReason"
          :placeholder="t('adminBookings.cancelReasonPlaceholder')"
        />
      </div>

      <template #footer>
        <div class="flex justify-end gap-3">
          <Button variant="secondary" size="md" @click="isCancelModalOpen = false">
            {{ t('common.cancel') }}
          </Button>
          <Button
            variant="danger"
            size="md"
            :loading="isCancelling"
            @click="confirmCancelBooking"
          >
            {{ t('adminBookings.confirmCancelBtn') }}
          </Button>
        </div>
      </template>
    </Modal>
  </div>
</template>
