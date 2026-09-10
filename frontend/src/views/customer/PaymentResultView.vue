<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import type { PaymentResultResponse } from '@/types/payment.types'
import type { BookingDetailResponse } from '@/types/booking.types'
import paymentService from '@/services/payment.service'
import bookingService from '@/services/booking.service'
import { formatCurrency, getErrorMessage } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import { useToast } from '@/composables/useToast'
import ElectronicTicket from '@/components/ticket/ElectronicTicket.vue'
import Button from '@/components/common/Button.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

/**
 * View hiển thị kết quả thanh toán từ Cổng thanh toán VNPay Sandbox (Payment Result View).
 * 
 * Luồng hoạt động:
 * 1. Tiếp nhận tham số redirect từ VNPay (vnp_TxnRef, vnp_Amount, vnp_ResponseCode, vnp_SecureHash,...).
 * 2. Gọi API Backend `/api/v1/payments/vnpay/return` để xác thực chữ ký số HMAC-SHA512.
 * 3. Lấy thông tin chi tiết đơn hàng (Booking Detail) từ Backend.
 * 4. Xử lý các kịch bản kết quả:
 *    - Thành công (isSuccess): Đơn hàng PAID và vé điện tử đã phát hành -> Hiển thị vé điện tử kèm mã QR Code.
 *    - Đang xử lý (isProcessing - Polling Fallback): Phản hồi từ VNPay là "00" nhưng IPN webhook chưa kịp chốt đơn,
 *      frontend tự động thăm dò (poll) 3 lần (2s, 3s, 5s) để đón trạng thái mới nhất.
 *    - Bị hủy (isCancelled): Khách hàng bấm hủy trên cổng VNPay (mã 24) -> Cho phép bấm "Thử lại thanh toán".
 *    - Thất bại / Hết hạn: Hiển thị thông báo chi tiết lỗi tương ứng.
 */
const route = useRoute()
const toast = useToast()
const { t } = useI18n()

const isLoading = ref<boolean>(true)
const isRetryingPayment = ref<boolean>(false)
const isCancellingBooking = ref<boolean>(false)
const errorMessage = ref<string>('')

const paymentResult = ref<PaymentResultResponse | null>(null)
const bookingDetail = ref<BookingDetailResponse | null>(null)

// Countdown timer cho thời gian giữ chỗ
const remainingSeconds = ref<number>(0)
let holdTimer: any = null

// Cơ chế Polling xử lý trường hợp bất đồng bộ giữa Return URL và Webhook IPN
const pollCount = ref<number>(0)
const maxPolls = 3
const pollDelays = [2000, 3000, 5000] // Khoảng thời gian giãn cách thăm dò (ms)
let pollTimeout: any = null

// 1. Giao dịch thành công: Đơn hàng đã PAID và đã phát hành vé
const isSuccess = computed<boolean>(() => {
  return bookingDetail.value?.bookingStatus === 'PAID' && (bookingDetail.value?.tickets?.length || 0) > 0
})

// 2. Khách hàng chủ động hủy giao dịch trên cổng VNPay hoặc hủy đơn
const isCancelled = computed<boolean>(() => {
  return (
    paymentResult.value?.responseCode === '24' ||
    paymentResult.value?.paymentStatus === 'CANCELLED' ||
    bookingDetail.value?.bookingStatus === 'CANCELLED'
  )
})

// 3. Giao dịch đang chờ xác nhận từ máy chủ
const isProcessing = computed<boolean>(() => {
  if (isSuccess.value || isCancelled.value) return false
  return (
    paymentResult.value?.responseCode === '00' &&
    bookingDetail.value?.bookingStatus === 'PENDING_PAYMENT'
  )
})

const isBookingCancelled = computed<boolean>(() => {
  return bookingDetail.value?.bookingStatus === 'CANCELLED'
})

const isHoldStillValid = computed<boolean>(() => {
  if (!bookingDetail.value?.holdExpiresAt) return false
  if (bookingDetail.value.bookingStatus !== 'PENDING_PAYMENT') return false
  return remainingSeconds.value > 0
})

const formattedRemainingTime = computed<string>(() => {
  const m = Math.floor(remainingSeconds.value / 60)
  const s = remainingSeconds.value % 60
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
})

function updateRemainingTime() {
  if (!bookingDetail.value?.holdExpiresAt || bookingDetail.value.bookingStatus !== 'PENDING_PAYMENT') {
    remainingSeconds.value = 0
    return
  }
  const diffMs = new Date(bookingDetail.value.holdExpiresAt).getTime() - Date.now()
  remainingSeconds.value = Math.max(0, Math.floor(diffMs / 1000))
  if (remainingSeconds.value <= 0 && holdTimer) {
    clearInterval(holdTimer)
    holdTimer = null
  }
}

function startHoldTimer() {
  if (holdTimer) {
    clearInterval(holdTimer)
    holdTimer = null
  }
  updateRemainingTime()
  if (remainingSeconds.value > 0) {
    holdTimer = setInterval(() => {
      updateRemainingTime()
    }, 1000)
  }
}

async function processPaymentReturn() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    // 1. Process Return parameters from VNPay redirect
    const returnParams = { ...route.query }
    if (Object.keys(returnParams).length === 0) {
      errorMessage.value = t('paymentResult.missingParamsError')
      isLoading.value = false
      return
    }

    const res = await paymentService.processReturn(returnParams)
    paymentResult.value = res

    // 2. Fetch authoritative booking detail from server
    if (res.bookingId) {
      await fetchBookingDetail(res.bookingId)
    }
  } catch (err: any) {
    errorMessage.value =
      err.response?.data?.message || t('paymentResult.invalidSignatureError')
  } finally {
    isLoading.value = false
  }
}

async function fetchBookingDetail(bookingId: string) {
  try {
    const bData = await bookingService.getBookingDetail(bookingId)
    bookingDetail.value = bData
    startHoldTimer()

    // If payment response code is 00 but IPN hasn't confirmed yet (PENDING_PAYMENT), trigger limited poll
    if (bData.bookingStatus === 'PENDING_PAYMENT' && paymentResult.value?.responseCode === '00') {
      scheduleNextPoll(bookingId)
    }
  } catch (err) {
    console.error('Failed to fetch booking detail', err)
  }
}

function scheduleNextPoll(bookingId: string) {
  if (pollCount.value >= maxPolls) return

  const delay = pollDelays[pollCount.value] || 3000
  pollCount.value++

  pollTimeout = setTimeout(async () => {
    try {
      const updated = await bookingService.getBookingDetail(bookingId)
      bookingDetail.value = updated
      startHoldTimer()
      if (updated.bookingStatus === 'PENDING_PAYMENT') {
        scheduleNextPoll(bookingId)
      }
    } catch (err) {
      console.warn('Poll error', err)
    }
  }, delay)
}

async function handleManualRefresh() {
  if (!paymentResult.value?.bookingId) return
  isLoading.value = true
  await fetchBookingDetail(paymentResult.value.bookingId)
  isLoading.value = false
  toast.info(t('paymentResult.refreshedNotice'))
}

async function handleRetryPayment() {
  if (!bookingDetail.value?.id) return
  isRetryingPayment.value = true

  try {
    const res = await paymentService.initiatePayment(bookingDetail.value.id, {
      paymentMethod: 'VNPAY',
    })
    window.location.href = res.paymentUrl
  } catch (err: any) {
    const msg = getErrorMessage(err, t('paymentResult.retryFailedError'))
    toast.error(msg, t('common.errorTitle'))
  } finally {
    isRetryingPayment.value = false
  }
}

async function handleCancelBooking() {
  if (!bookingDetail.value?.id) return
  isCancellingBooking.value = true

  try {
    const updated = await bookingService.cancelBooking(bookingDetail.value.id, {
      reason: 'CUSTOMER_CANCELLED',
    })
    bookingDetail.value = updated
    if (holdTimer) {
      clearInterval(holdTimer)
      holdTimer = null
    }
    remainingSeconds.value = 0
    toast.success(t('paymentResult.bookingCancelledNotice'), t('paymentResult.cancelSuccessTitle'))
  } catch (err: any) {
    const msg = getErrorMessage(err, t('common.unknownError'))
    toast.error(msg, t('common.errorTitle'))
  } finally {
    isCancellingBooking.value = false
  }
}

function printAllTickets() {
  window.print()
}

onMounted(() => {
  processPaymentReturn()
})

onUnmounted(() => {
  if (pollTimeout) {
    clearTimeout(pollTimeout)
    pollTimeout = null
  }
  if (holdTimer) {
    clearInterval(holdTimer)
    holdTimer = null
  }
})
</script>

<template>
  <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-10 sm:py-16 space-y-10">
    <transition name="fade-fast" mode="out-in">
      <!-- 1. Loading Skeleton -->
      <div v-if="isLoading" key="loading" class="space-y-6">
        <div class="max-w-md mx-auto h-12 rounded-2xl bg-slate-800/80 border border-slate-700/60 animate-shimmer"></div>
        <div class="max-w-2xl mx-auto h-64 rounded-3xl bg-slate-800/80 border border-slate-700/60 animate-shimmer"></div>
      </div>

      <!-- 2. Error in Return URL / Signature -->
      <div v-else-if="errorMessage" key="error" class="max-w-2xl mx-auto space-y-6 text-center">
        <ErrorAlert :message="errorMessage" />
        <div class="flex items-center justify-center gap-3">
          <router-link to="/movies">
            <Button variant="secondary" size="md">{{ t('movieDetail.backToCatalog') }}</Button>
          </router-link>
          <router-link to="/my-bookings">
            <Button variant="primary" size="md">{{ t('nav.myBookings') }}</Button>
          </router-link>
        </div>
      </div>

      <!-- 3. Payment States -->
      <div v-else key="content">
        <!-- STATE A: SUCCESS (Authoritative Booking PAID + Tickets) -->
        <div v-if="isSuccess" class="space-y-8">
          <!-- Success Hero Header Banner -->
          <div class="p-6 sm:p-8 rounded-3xl bg-emerald-950/70 border border-emerald-500/80 text-center space-y-3 shadow-2xl shadow-emerald-950/50 print:hidden">
          <div class="w-16 h-16 rounded-full bg-emerald-500/20 border-2 border-emerald-400 text-emerald-400 flex items-center justify-center mx-auto text-3xl font-black shadow-lg">
            ✓
          </div>
          <h1 class="text-2xl sm:text-3xl font-black text-white tracking-tight">
            {{ t('paymentResult.successTitle') }}
          </h1>
          <p class="text-xs sm:text-sm text-emerald-200/90 max-w-lg mx-auto">
            {{ t('paymentResult.successSubtitle') }}
          </p>

          <div class="pt-2 flex flex-wrap items-center justify-center gap-4 text-xs">
            <span class="px-3 py-1.5 rounded-xl bg-slate-900/80 border border-slate-700 text-slate-300 font-mono">
              {{ t('booking.bookingCode') }}: <strong class="text-white">{{ bookingDetail?.bookingCode }}</strong>
            </span>
            <span class="px-3 py-1.5 rounded-xl bg-slate-900/80 border border-slate-700 text-slate-300">
              {{ t('paymentResult.totalPaid') }}: <strong class="text-emerald-400">{{ formatCurrency(bookingDetail?.totalAmount) }}</strong>
            </span>
          </div>
        </div>

        <!-- Electronic Tickets Section -->
        <div class="space-y-6">
          <div class="flex items-center justify-between border-b border-slate-800 pb-3 print:hidden">
            <div>
              <h2 class="text-xl font-bold text-white tracking-tight flex items-center gap-2">
                🎟️ {{ t('ticket.sectionTitle') }}
              </h2>
              <p class="text-xs text-slate-400 mt-0.5">
                {{ t('ticket.sectionSubtitle', { count: bookingDetail?.tickets?.length || 0 }) }}
              </p>
            </div>

            <Button variant="secondary" size="sm" @click="printAllTickets">
              <template #prefix>
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
                </svg>
              </template>
              {{ t('ticket.printBtn') }}
            </Button>
          </div>

          <!-- Unified Electronic Ticket Pass (One QR for multiple seats) -->
          <div v-if="bookingDetail" class="flex justify-center">
            <ElectronicTicket :booking="bookingDetail" />
          </div>
        </div>

        <!-- Success Navigation Actions -->
        <div class="pt-4 flex flex-wrap items-center justify-center gap-4 print:hidden">
          <router-link to="/my-bookings">
            <Button variant="primary" size="lg" class="shadow-xl shadow-indigo-600/30">
              <template #prefix>
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z" />
                </svg>
              </template>
              {{ t('paymentResult.viewMyBookingsBtn') }}
            </Button>
          </router-link>

          <router-link to="/">
            <Button variant="secondary" size="lg">
              {{ t('nav.home') }}
            </Button>
          </router-link>
        </div>
      </div>

      <!-- STATE B: PROCESSING / PENDING (Return arrived before IPN) -->
      <div v-else-if="isProcessing" class="max-w-2xl mx-auto space-y-6 text-center animate-fade-in">
        <div class="p-8 rounded-3xl bg-indigo-950/70 border border-indigo-700/80 space-y-4 shadow-2xl">
          <div class="w-16 h-16 rounded-full bg-indigo-600/20 border-2 border-indigo-500 text-indigo-400 flex items-center justify-center mx-auto text-2xl animate-spin">
            ⏳
          </div>
          <h2 class="text-2xl font-bold text-white tracking-tight">
            {{ t('paymentResult.processingTitle') }}
          </h2>
          <p class="text-xs sm:text-sm text-indigo-200/90 leading-relaxed">
            {{ t('paymentResult.processingDesc') }}
          </p>

          <div class="pt-4 flex flex-wrap items-center justify-center gap-3">
            <Button variant="primary" size="md" @click="handleManualRefresh">
              {{ t('paymentResult.refreshStatusBtn') }}
            </Button>
            <router-link to="/my-bookings">
              <Button variant="secondary" size="md">
                {{ t('nav.myBookings') }}
              </Button>
            </router-link>
          </div>
        </div>
      </div>

      <!-- STATE C: CANCELLED (Customer aborted on VNPay or cancelled booking) -->
      <div v-else-if="isCancelled" class="max-w-2xl mx-auto space-y-6 text-center animate-fade-in">
        <div class="p-8 rounded-3xl bg-amber-950/70 border border-amber-600/80 space-y-4 shadow-2xl">
          <div class="w-16 h-16 rounded-full bg-amber-500/20 border-2 border-amber-400 text-amber-400 flex items-center justify-center mx-auto text-2xl">
            ⚠️
          </div>
          <h2 class="text-2xl font-bold text-white tracking-tight">
            {{ isBookingCancelled ? t('paymentResult.cancelledTitle') : (isHoldStillValid ? t('paymentResult.cancelledTitle') : t('paymentResult.expiredTitle')) }}
          </h2>
          <p class="text-xs sm:text-sm text-amber-200/90 leading-relaxed">
            {{ isBookingCancelled ? t('paymentResult.bookingCancelledDesc') : (isHoldStillValid ? t('paymentResult.cancelledDesc') : t('paymentResult.expiredDesc')) }}
          </p>

          <!-- Countdown timer badge if hold is still valid -->
          <div v-if="isHoldStillValid && !isBookingCancelled" class="inline-flex items-center gap-2 px-4 py-2 rounded-2xl bg-amber-900/60 border border-amber-500/50 text-amber-300 font-mono text-sm">
            <span>⏱️ {{ t('paymentResult.holdRemainingLabel') }}</span>
            <span class="font-black text-white text-base">{{ formattedRemainingTime }}</span>
          </div>

          <div class="pt-4 flex flex-wrap items-center justify-center gap-3">
            <template v-if="isHoldStillValid && !isBookingCancelled">
              <Button
                variant="primary"
                size="md"
                :loading="isRetryingPayment"
                @click="handleRetryPayment"
              >
                {{ t('paymentResult.retryPaymentBtn') }}
              </Button>
              <Button
                variant="danger"
                size="md"
                :loading="isCancellingBooking"
                @click="handleCancelBooking"
              >
                {{ t('paymentResult.cancelBookingBtn') }}
              </Button>
            </template>
            <router-link to="/showtimes">
              <Button variant="secondary" size="md">
                {{ t('paymentResult.reselectShowtimeBtn') }}
              </Button>
            </router-link>
          </div>
        </div>
      </div>

      <!-- STATE D: FAILED -->
      <div v-else class="max-w-2xl mx-auto space-y-6 text-center animate-fade-in">
        <div class="p-8 rounded-3xl bg-rose-950/70 border border-rose-600/80 space-y-4 shadow-2xl">
          <div class="w-16 h-16 rounded-full bg-rose-500/20 border-2 border-rose-400 text-rose-400 flex items-center justify-center mx-auto text-2xl">
            ✕
          </div>
          <h2 class="text-2xl font-bold text-white tracking-tight">
            {{ isBookingCancelled ? t('paymentResult.cancelledTitle') : (isHoldStillValid ? t('paymentResult.failedTitle') : t('paymentResult.expiredTitle')) }}
          </h2>
          <p class="text-xs sm:text-sm text-rose-200/90 leading-relaxed">
            {{ isBookingCancelled ? t('paymentResult.bookingCancelledDesc') : (isHoldStillValid ? (paymentResult?.message || t('paymentResult.failedDesc')) : t('paymentResult.expiredDesc')) }}
          </p>

          <!-- Countdown timer badge if hold is still valid -->
          <div v-if="isHoldStillValid && !isBookingCancelled" class="inline-flex items-center gap-2 px-4 py-2 rounded-2xl bg-rose-900/60 border border-rose-500/50 text-rose-300 font-mono text-sm">
            <span>⏱️ {{ t('paymentResult.holdRemainingLabel') }}</span>
            <span class="font-black text-white text-base">{{ formattedRemainingTime }}</span>
          </div>

          <div class="pt-4 flex flex-wrap items-center justify-center gap-3">
            <template v-if="isHoldStillValid && !isBookingCancelled">
              <Button
                variant="primary"
                size="md"
                :loading="isRetryingPayment"
                @click="handleRetryPayment"
              >
                {{ t('paymentResult.retryPaymentBtn') }}
              </Button>
              <Button
                variant="danger"
                size="md"
                :loading="isCancellingBooking"
                @click="handleCancelBooking"
              >
                {{ t('paymentResult.cancelBookingBtn') }}
              </Button>
            </template>
            <router-link to="/showtimes">
              <Button variant="secondary" size="md">
                {{ t('paymentResult.reselectShowtimeBtn') }}
              </Button>
            </router-link>
          </div>
        </div>
      </div>
    </div>
    </transition>
  </div>
</template>
