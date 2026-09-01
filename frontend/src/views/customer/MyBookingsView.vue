<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { BookingSummaryResponse, BookingDetailResponse, BookingStatus } from '@/types/booking.types'
import type { RefundResponse } from '@/types/refund.types'
import bookingService from '@/services/booking.service'
import paymentService from '@/services/payment.service'
import { formatCurrency, formatDate, formatTime } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import { useToast } from '@/composables/useToast'
import TicketModal from '@/components/ticket/TicketModal.vue'
import RefundModal from '@/components/payment/RefundModal.vue'
import RefundDetailModal from '@/components/payment/RefundDetailModal.vue'
import Modal from '@/components/common/Modal.vue'
import Button from '@/components/common/Button.vue'
import Badge from '@/components/common/Badge.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const toast = useToast()
const { t } = useI18n()

const bookings = ref<BookingSummaryResponse[]>([])
const activeFilter = ref<string>('ALL') // 'ALL' | 'PAID' | 'PENDING_PAYMENT' | 'CANCELLED' | 'EXPIRED' | 'REFUNDED'

const currentPage = ref<number>(0)
const totalPages = ref<number>(1)
const totalElements = ref<number>(0)

const isLoading = ref<boolean>(true)
const isActionLoading = ref<Record<string, boolean>>({})
const errorMessage = ref<string>('')

const isCancelModalOpen = ref<boolean>(false)
const cancelTargetBooking = ref<BookingSummaryResponse | BookingDetailResponse | null>(null)
const isCancelling = ref<boolean>(false)

// Ticket Modal State
const isTicketModalOpen = ref<boolean>(false)
const selectedBookingDetail = ref<BookingDetailResponse | null>(null)

// Refund Modals State
const isRefundModalOpen = ref<boolean>(false)
const selectedRefundBooking = ref<BookingSummaryResponse | null>(null)
const isRefunding = ref<boolean>(false)

const isRefundDetailModalOpen = ref<boolean>(false)
const selectedRefundDetail = ref<RefundResponse | null>(null)

async function fetchMyBookings() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const statusParam = activeFilter.value !== 'ALL' ? (activeFilter.value as BookingStatus) : undefined
    const res = await bookingService.getMyBookings({
      page: currentPage.value,
      size: 10,
      status: statusParam,
      sort: 'createdAt,desc',
    })

    bookings.value = res.content || []
    totalPages.value = res.totalPages || 1
    totalElements.value = res.totalElements || 0
  } catch (err: any) {
    errorMessage.value =
      err.response?.data?.message || t('myBookings.loadError')
  } finally {
    isLoading.value = false
  }
}

function setFilter(filter: string) {
  activeFilter.value = filter
  currentPage.value = 0
  fetchMyBookings()
}

function setPage(page: number) {
  currentPage.value = page
  fetchMyBookings()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function openTicketModal(bookingId: string) {
  isActionLoading.value[bookingId] = true
  try {
    const detail = await bookingService.getBookingDetail(bookingId)
    selectedBookingDetail.value = detail
    isTicketModalOpen.value = true
  } catch (err: any) {
    toast.error(t('common.errorTitle'), err.response?.data?.message || 'Không thể tải chi tiết vé.')
  } finally {
    isActionLoading.value[bookingId] = false
  }
}

function openRefundModal(b: BookingSummaryResponse) {
  selectedRefundBooking.value = b
  isRefundModalOpen.value = true
}

async function handleConfirmRefund(reason: string) {
  if (!selectedRefundBooking.value) return
  isRefunding.value = true

  const bookingId = selectedRefundBooking.value.id
  try {
    // 1. Fetch booking detail to obtain payment ID
    const detail = await bookingService.getBookingDetail(bookingId)
    const successPayment = detail.payments?.find((p) => p.status === 'SUCCESS') || detail.payments?.[0]
    if (!successPayment) {
      throw new Error('Không tìm thấy giao dịch thanh toán hợp lệ để hoàn tiền.')
    }

    // 2. Call authoritative refund endpoint
    const refundRes = await paymentService.refundPayment(successPayment.id, { reason })
    toast.success(
      t('refund.refundSuccessTitle'),
      `${t('refund.refundCode')}: ${refundRes.refundCode} (${formatCurrency(refundRes.amount)})`
    )

    isRefundModalOpen.value = false
    await fetchMyBookings()
  } catch (err: any) {
    const msg = err.response?.data?.message || err.message || t('refund.refundFailed')
    toast.error(t('common.errorTitle'), msg)
  } finally {
    isRefunding.value = false
  }
}

async function openRefundReceipt(bookingId: string) {
  isActionLoading.value[bookingId] = true
  try {
    const detail = await bookingService.getBookingDetail(bookingId)
    const payment = detail.payments?.[0]
    if (!payment) {
      throw new Error('Không tìm thấy thông tin giao dịch thanh toán.')
    }

    const refund = await paymentService.getRefundDetail(payment.id)
    selectedRefundDetail.value = refund
    isRefundDetailModalOpen.value = true
  } catch (err: any) {
    toast.error(t('common.errorTitle'), err.response?.data?.message || err.message || 'Không thể tải biên lai hoàn tiền.')
  } finally {
    isActionLoading.value[bookingId] = false
  }
}

async function handleContinuePayment(bookingId: string) {
  isActionLoading.value[bookingId] = true
  try {
    const res = await paymentService.initiatePayment(bookingId, { paymentMethod: 'VNPAY' })
    window.location.href = res.paymentUrl
  } catch (err: any) {
    const msg = err.response?.data?.message || 'Không thể khởi tạo thanh toán.'
    toast.error(t('common.errorTitle'), msg)
    await fetchMyBookings()
  } finally {
    isActionLoading.value[bookingId] = false
  }
}

function openCancelConfirmModal(booking: BookingSummaryResponse | BookingDetailResponse | string) {
  if (typeof booking === 'string') {
    const found = bookings.value.find((b) => b.id === booking)
    if (found) {
      cancelTargetBooking.value = found
    } else if (selectedBookingDetail.value?.id === booking) {
      cancelTargetBooking.value = selectedBookingDetail.value
    } else {
      cancelTargetBooking.value = null
    }
  } else {
    cancelTargetBooking.value = booking
  }
  isCancelModalOpen.value = true
}

async function executeCancelBooking() {
  if (!cancelTargetBooking.value) return
  const bookingId = cancelTargetBooking.value.id
  isCancelling.value = true
  try {
    await bookingService.cancelBooking(bookingId, { reason: 'Khách hàng tự hủy trên web' })
    toast.success(t('myBookings.cancelSuccess'))
    isCancelModalOpen.value = false
    if (isTicketModalOpen.value && selectedBookingDetail.value?.id === bookingId) {
      isTicketModalOpen.value = false
    }
    await fetchMyBookings()
  } catch (err: any) {
    toast.error(t('common.errorTitle'), err.response?.data?.message || t('myBookings.cancelFailed'))
  } finally {
    isCancelling.value = false
  }
}

const cancelTargetMovieTitle = computed(() => {
  if (!cancelTargetBooking.value) return 'Vé xem phim'
  const b = cancelTargetBooking.value
  if ('showtime' in b && b.showtime) {
    if ('movieTitle' in b.showtime && (b.showtime as any).movieTitle) {
      return (b.showtime as any).movieTitle
    }
    if ('movie' in b.showtime && (b.showtime as any).movie?.title) {
      return (b.showtime as any).movie.title
    }
  }
  if ('movieTitle' in b && (b as any).movieTitle) {
    return (b as any).movieTitle
  }
  return 'Vé xem phim'
})

function isRefundEligible(b: BookingSummaryResponse): boolean {
  const startTime = b.showtime?.startTime || b.showtimeStartTime
  if (b.bookingStatus !== 'PAID' || !startTime) return false
  const showtimeTime = new Date(startTime).getTime()
  const twoHoursMs = 2 * 60 * 60 * 1000
  return showtimeTime - Date.now() >= twoHoursMs
}

function getStatusBadgeVariant(status: BookingStatus): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (status) {
    case 'PAID':
      return 'success'
    case 'PENDING_PAYMENT':
      return 'warning'
    case 'CANCELLED':
      return 'danger'
    case 'EXPIRED':
      return 'neutral'
    case 'REFUNDED':
      return 'info'
    default:
      return 'neutral'
  }
}

function getStatusLabel(status: BookingStatus): string {
  switch (status) {
    case 'PAID':
      return t('myBookings.statusPaid')
    case 'PENDING_PAYMENT':
      return t('myBookings.statusPending')
    case 'CANCELLED':
      return t('myBookings.statusCancelled')
    case 'EXPIRED':
      return t('myBookings.statusExpired')
    case 'REFUNDED':
      return t('myBookings.statusRefunded')
    default:
      return status
  }
}

function isHoldActive(booking: BookingSummaryResponse): boolean {
  if (booking.bookingStatus !== 'PENDING_PAYMENT' || !booking.holdExpiresAt) return false
  return new Date(booking.holdExpiresAt).getTime() > Date.now()
}

onMounted(() => {
  fetchMyBookings()
})
</script>

<template>
  <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-10 sm:py-14 space-y-8">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-6">
      <div>
        <h1 class="text-2xl sm:text-3xl font-black text-white tracking-tight flex items-center gap-2.5">
          <span class="w-2.5 h-6 rounded-full bg-indigo-600 inline-block"></span>
          {{ t('myBookings.title') }}
        </h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ t('myBookings.subtitle') }}
        </p>
      </div>

      <router-link to="/showtimes">
        <Button variant="primary" size="md" class="shadow-lg shadow-indigo-600/30">
          <template #prefix>
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
          </template>
          {{ t('myBookings.bookMoreTickets') }}
        </Button>
      </router-link>
    </div>

    <!-- Filter Tabs -->
    <div class="flex items-center gap-2 overflow-x-auto pb-2 scrollbar-thin">
      <button
        type="button"
        :class="[
          'px-4 py-2 rounded-xl text-xs font-bold transition-all whitespace-nowrap',
          activeFilter === 'ALL'
            ? 'bg-indigo-600 text-white shadow-md'
            : 'bg-slate-800 text-slate-400 hover:bg-slate-750 hover:text-slate-200'
        ]"
        @click="setFilter('ALL')"
      >
        {{ t('myBookings.filterAll') }}
      </button>

      <button
        type="button"
        :class="[
          'px-4 py-2 rounded-xl text-xs font-bold transition-all whitespace-nowrap',
          activeFilter === 'PAID'
            ? 'bg-emerald-600 text-white shadow-md'
            : 'bg-slate-800 text-slate-400 hover:bg-slate-750 hover:text-slate-200'
        ]"
        @click="setFilter('PAID')"
      >
        {{ t('myBookings.filterPaid') }}
      </button>

      <button
        type="button"
        :class="[
          'px-4 py-2 rounded-xl text-xs font-bold transition-all whitespace-nowrap',
          activeFilter === 'PENDING_PAYMENT'
            ? 'bg-amber-600 text-white shadow-md'
            : 'bg-slate-800 text-slate-400 hover:bg-slate-750 hover:text-slate-200'
        ]"
        @click="setFilter('PENDING_PAYMENT')"
      >
        {{ t('myBookings.filterPending') }}
      </button>

      <button
        type="button"
        :class="[
          'px-4 py-2 rounded-xl text-xs font-bold transition-all whitespace-nowrap',
          activeFilter === 'REFUNDED'
            ? 'bg-blue-600 text-white shadow-md'
            : 'bg-slate-800 text-slate-400 hover:bg-slate-750 hover:text-slate-200'
        ]"
        @click="setFilter('REFUNDED')"
      >
        {{ t('myBookings.filterRefunded') }}
      </button>

      <button
        type="button"
        :class="[
          'px-4 py-2 rounded-xl text-xs font-bold transition-all whitespace-nowrap',
          activeFilter === 'CANCELLED'
            ? 'bg-rose-600 text-white shadow-md'
            : 'bg-slate-800 text-slate-400 hover:bg-slate-750 hover:text-slate-200'
        ]"
        @click="setFilter('CANCELLED')"
      >
        {{ t('myBookings.filterCancelled') }}
      </button>

      <button
        type="button"
        :class="[
          'px-4 py-2 rounded-xl text-xs font-bold transition-all whitespace-nowrap',
          activeFilter === 'EXPIRED'
            ? 'bg-slate-700 text-white shadow-md'
            : 'bg-slate-800 text-slate-400 hover:bg-slate-750 hover:text-slate-200'
        ]"
        @click="setFilter('EXPIRED')"
      >
        {{ t('myBookings.filterExpired') }}
      </button>
    </div>

    <!-- Error State -->
    <div v-if="errorMessage">
      <ErrorAlert :message="errorMessage" @retry="fetchMyBookings" />
    </div>

    <!-- Loading Skeleton -->
    <div v-else-if="isLoading" class="space-y-4 animate-pulse">
      <div v-for="n in 3" :key="n" class="h-40 rounded-2xl bg-slate-800/80"></div>
    </div>

    <!-- Empty State -->
    <div
      v-else-if="bookings.length === 0"
      class="p-12 text-center rounded-3xl bg-slate-900 border border-slate-800 space-y-4"
    >
      <div class="w-16 h-16 rounded-full bg-slate-800 text-slate-500 flex items-center justify-center mx-auto text-2xl">
        🎟️
      </div>
      <h3 class="text-lg font-bold text-white">{{ t('myBookings.emptyTitle') }}</h3>
      <p class="text-xs text-slate-400 max-w-sm mx-auto">{{ t('myBookings.emptyDesc') }}</p>
      <div class="pt-2">
        <router-link to="/movies">
          <Button variant="primary" size="md">{{ t('myBookings.browseMoviesBtn') }}</Button>
        </router-link>
      </div>
    </div>

    <!-- Bookings Cards List -->
    <div v-else class="space-y-4">
      <div
        v-for="b in bookings"
        :key="b.id"
        class="p-5 sm:p-6 rounded-2xl bg-slate-850 border border-slate-800 hover:border-slate-700 transition-all shadow-md space-y-4"
      >
        <!-- Card Top Bar: Booking Code, Created Date & Status Badge -->
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800/80 pb-3 text-xs">
          <div class="flex items-center gap-2 flex-wrap">
            <span class="text-slate-400">{{ t('booking.bookingCode') }}:</span>
            <span class="font-mono font-bold text-indigo-400 text-sm tracking-wide">{{ b.bookingCode }}</span>
            <span class="text-slate-500">• {{ formatDate(b.createdAt) }}</span>
          </div>

          <Badge :variant="getStatusBadgeVariant(b.bookingStatus)" size="sm" class="font-bold">
            {{ getStatusLabel(b.bookingStatus) }}
          </Badge>
        </div>

        <!-- Card Body: Poster + Movie Details + Room/Seats + Price + Actions -->
        <div class="flex flex-col sm:flex-row gap-5 items-start">
          <!-- Left: Movie Poster -->
          <div class="shrink-0 w-24 sm:w-28 aspect-[2/3] rounded-xl overflow-hidden bg-slate-900 border border-slate-700/80 shadow-md">
            <img
              v-if="b.showtime?.moviePosterUrl"
              :src="b.showtime.moviePosterUrl"
              :alt="b.showtime.movieTitle || 'Movie poster'"
              class="w-full h-full object-cover"
            />
            <div v-else class="w-full h-full flex flex-col items-center justify-center p-2 text-center text-slate-500 text-xs bg-slate-800">
              <span class="text-2xl">🎬</span>
              <span class="line-clamp-2 mt-1">{{ b.showtime?.movieTitle || b.movieTitle || 'Phim' }}</span>
            </div>
          </div>

          <!-- Center: Movie, Cinema, Auditorium, Showtime, Seats -->
          <div class="flex-1 space-y-2.5 min-w-0">
            <div class="space-y-1">
              <div class="flex items-center gap-2 flex-wrap">
                <Badge v-if="b.showtime?.movieAgeRating" variant="warning" size="sm" class="font-bold">
                  {{ b.showtime.movieAgeRating }}
                </Badge>
                <Badge v-if="b.showtime?.format" variant="primary" size="sm" class="font-semibold">
                  {{ b.showtime.format }}
                </Badge>
              </div>
              <h3 class="text-base sm:text-lg font-bold text-white tracking-tight line-clamp-2">
                {{ b.showtime?.movieTitle || b.movieTitle || 'Vé xem phim' }}
              </h3>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs text-slate-300">
              <div class="flex items-center gap-1.5 truncate">
                <span class="text-slate-500">🏢 Rạp:</span>
                <span class="font-semibold text-slate-200 truncate">{{ b.showtime?.cinemaName || b.cinemaName || 'Rạp CineBook' }}</span>
                <span v-if="b.showtime?.cinemaCity" class="text-slate-400">({{ b.showtime.cinemaCity }})</span>
              </div>

              <div class="flex items-center gap-1.5">
                <span class="text-slate-500">🚪 Phòng:</span>
                <span class="font-semibold text-slate-200">{{ b.showtime?.auditoriumName || 'Phòng chiếu' }}</span>
              </div>

              <div class="flex items-center gap-1.5">
                <span class="text-slate-500">⏰ Suất chiếu:</span>
                <span class="font-bold text-indigo-400">
                  {{ (b.showtime?.startTime || b.showtimeStartTime) ? formatTime(b.showtime?.startTime || b.showtimeStartTime) : '---' }}
                </span>
                <span class="text-slate-400">
                  • {{ (b.showtime?.startTime || b.showtimeStartTime) ? formatDate(b.showtime?.startTime || b.showtimeStartTime) : '' }}
                </span>
              </div>

              <div class="flex items-center gap-1.5">
                <span class="text-slate-500">🎟️ Số lượng:</span>
                <span class="font-bold text-emerald-400">{{ b.seatCount || b.seatsCount || 1 }} {{ t('myBookings.seatsCountUnit') }}</span>
              </div>
            </div>

            <!-- Pending payment notice -->
            <div v-if="b.bookingStatus === 'PENDING_PAYMENT' && isHoldActive(b)" class="text-xs text-amber-300 flex items-center gap-1.5 bg-amber-500/10 border border-amber-500/20 px-3 py-1.5 rounded-lg w-fit">
              <span>⏳</span>
              <span>Ghế giữ đến <strong>{{ formatTime(b.holdExpiresAt) }}</strong></span>
            </div>
          </div>

          <!-- Right: Total Amount & CTA Actions -->
          <div class="shrink-0 w-full sm:w-auto flex sm:flex-col items-center sm:items-end justify-between gap-3 pt-3 sm:pt-0 border-t sm:border-t-0 border-slate-800">
            <div class="text-left sm:text-right">
              <span class="text-[10px] text-slate-400 block">{{ t('myBookings.totalAmount') }}</span>
              <span class="text-lg sm:text-xl font-black text-emerald-400">
                {{ formatCurrency(b.totalAmount) }}
              </span>
            </div>

            <div class="flex items-center gap-2 flex-wrap justify-end">
              <!-- Always available: View details -->
              <Button
                variant="secondary"
                size="sm"
                :loading="isActionLoading[b.id]"
                @click="openTicketModal(b.id)"
              >
                <template #prefix>
                  <span>👁️</span>
                </template>
                {{ b.bookingStatus === 'PAID' ? t('myBookings.viewTicketBtn') : t('myBookings.viewDetailBtn') }}
              </Button>

              <!-- Actions for PENDING_PAYMENT: Pay Now & Cancel Booking -->
              <template v-if="b.bookingStatus === 'PENDING_PAYMENT'">
                <Button
                  v-if="isHoldActive(b)"
                  variant="primary"
                  size="sm"
                  :loading="isActionLoading[b.id]"
                  class="shadow-md shadow-amber-600/30 bg-amber-600 hover:bg-amber-500 text-white font-semibold"
                  @click="handleContinuePayment(b.id)"
                >
                  {{ t('myBookings.payNowBtn') }}
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  :loading="isActionLoading[b.id]"
                  class="text-rose-400 hover:text-rose-300 hover:bg-rose-500/10 font-semibold"
                  @click="openCancelConfirmModal(b)"
                >
                  {{ t('myBookings.cancelBookingBtn') }}
                </Button>
              </template>

              <!-- Refund button for eligible PAID -->
              <Button
                v-if="b.bookingStatus === 'PAID' && isRefundEligible(b)"
                variant="secondary"
                size="sm"
                :loading="isActionLoading[b.id]"
                class="text-amber-400 hover:text-amber-300"
                @click="openRefundModal(b)"
              >
                <template #prefix>
                  <span>💸</span>
                </template>
                {{ t('refund.requestRefundBtn') }}
              </Button>

              <!-- View Receipt for REFUNDED -->
              <Button
                v-else-if="b.bookingStatus === 'REFUNDED'"
                variant="secondary"
                size="sm"
                :loading="isActionLoading[b.id]"
                class="text-indigo-400 hover:text-indigo-300"
                @click="openRefundReceipt(b.id)"
              >
                <template #prefix>
                  <span>🧾</span>
                </template>
                {{ t('refund.viewReceiptBtn') }}
              </Button>
            </div>
          </div>
        </div>
      </div>

      <!-- Pagination -->
      <Pagination
        v-if="totalPages > 1"
        :current-page="currentPage"
        :total-pages="totalPages"
        :total-elements="totalElements"
        :page-size="10"
        @page-change="setPage"
      />
    </div>

    <!-- Electronic Ticket & Booking Detail Modal -->
    <TicketModal
      :is-open="isTicketModalOpen"
      :booking="selectedBookingDetail"
      :action-loading="selectedBookingDetail ? !!isActionLoading[selectedBookingDetail.id] : false"
      @continue-payment="handleContinuePayment"
      @cancel-booking="openCancelConfirmModal"
      @close="isTicketModalOpen = false"
    />

    <!-- Customer Cancel Booking Confirmation Modal -->
    <Modal
      v-model="isCancelModalOpen"
      size="sm"
      :title="t('myBookings.cancelConfirmTitle')"
    >
      <div class="space-y-4 text-sm">
        <div class="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 flex items-start gap-3">
          <span class="text-2xl shrink-0">⚠️</span>
          <div class="space-y-1 text-slate-200">
            <p class="font-bold text-rose-400 text-sm sm:text-base">
              {{ t('myBookings.cancelConfirmQuestion') }}
            </p>
            <p class="text-xs sm:text-sm text-slate-300 leading-relaxed">
              {{ t('myBookings.cancelConfirmDesc') }}
            </p>
          </div>
        </div>

        <div v-if="cancelTargetBooking" class="text-xs sm:text-sm text-slate-300 p-4 rounded-xl bg-slate-950/70 border border-slate-800 space-y-2">
          <div class="flex justify-between">
            <span class="text-slate-400">Mã đơn:</span>
            <span class="font-mono font-bold text-indigo-400">{{ cancelTargetBooking.bookingCode }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-slate-400">Phim:</span>
            <span class="font-semibold text-white truncate max-w-[200px]">
              {{ cancelTargetMovieTitle }}
            </span>
          </div>
          <div class="flex justify-between pt-1 border-t border-slate-800/80">
            <span class="text-slate-400">Tổng tiền:</span>
            <span class="font-bold text-emerald-400">{{ formatCurrency(cancelTargetBooking.totalAmount) }}</span>
          </div>
        </div>
      </div>

      <template #footer>
        <Button variant="secondary" size="md" :disabled="isCancelling" @click="isCancelModalOpen = false">
          {{ t('myBookings.cancelConfirmBackBtn') }}
        </Button>
        <Button
          variant="danger"
          size="md"
          :loading="isCancelling"
          class="bg-rose-600 hover:bg-rose-500 text-white font-bold shadow-lg shadow-rose-600/30"
          @click="executeCancelBooking"
        >
          {{ t('myBookings.cancelConfirmActionBtn') }}
        </Button>
      </template>
    </Modal>

    <!-- Customer Refund Confirmation Modal -->
    <RefundModal
      :is-open="isRefundModalOpen"
      :booking="selectedRefundBooking"
      :is-loading="isRefunding"
      @confirm="handleConfirmRefund"
      @close="isRefundModalOpen = false"
    />

    <!-- Customer Refund Receipt Modal -->
    <RefundDetailModal
      :is-open="isRefundDetailModalOpen"
      :refund="selectedRefundDetail"
      @close="isRefundDetailModalOpen = false"
    />
  </div>
</template>
