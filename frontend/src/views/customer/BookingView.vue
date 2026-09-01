<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import type { ShowtimeDetailResponse, ShowtimeSeatStatusResponse } from '@/types/showtime.types'
import type { BookingDetailResponse } from '@/types/booking.types'
import showtimeService from '@/services/showtime.service'
import bookingService from '@/services/booking.service'
import paymentService from '@/services/payment.service'
import { useToast } from '@/composables/useToast'
import { useI18n } from '@/composables/useI18n'
import SeatMap from '@/components/booking/SeatMap.vue'
import SeatLegend from '@/components/booking/SeatLegend.vue'
import BookingSummary from '@/components/booking/BookingSummary.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import Button from '@/components/common/Button.vue'

const route = useRoute()
const toast = useToast()
const { t } = useI18n()

const showtimeId = computed<string>(() => (route.query.showtimeId as string) || '')

const showtime = ref<ShowtimeDetailResponse | null>(null)
const seats = ref<ShowtimeSeatStatusResponse[]>([])
const selectedSeatIds = ref<string[]>([])
const appliedPromotionCode = ref<string>('')

const isLoading = ref<boolean>(true)
const isSubmitting = ref<boolean>(false)
const errorMessage = ref<string>('')
const conflictMessage = ref<string>('')

// Hold State
const createdBooking = ref<BookingDetailResponse | null>(null)
const holdRemainingSeconds = ref<number>(0)
const isHoldExpired = ref<boolean>(false)
let countdownTimer: any = null

const selectedSeatsObjects = computed<ShowtimeSeatStatusResponse[]>(() => {
  return seats.value.filter((s) => selectedSeatIds.value.includes(s.id))
})

async function loadBookingData() {
  if (!showtimeId.value) {
    isLoading.value = false
    errorMessage.value = t('booking.invalidShowtimeError')
    return
  }

  isLoading.value = true
  errorMessage.value = ''
  conflictMessage.value = ''

  try {
    const [stData, seatsData, activeBooking] = await Promise.all([
      showtimeService.getPublicShowtimeDetail(showtimeId.value),
      showtimeService.getShowtimeSeats(showtimeId.value),
      bookingService.getActiveBooking(showtimeId.value).catch(() => null),
    ])

    showtime.value = stData
    seats.value = seatsData
    document.title = `${stData.movie?.title || 'Đặt vé'} — CineBook`

    if (activeBooking && activeBooking.bookingStatus === 'PENDING_PAYMENT') {
      const expiresAtMs = new Date(activeBooking.holdExpiresAt).getTime()
      if (expiresAtMs > Date.now()) {
        createdBooking.value = activeBooking
        selectedSeatIds.value = activeBooking.seats.map((s) => s.seatId)
        if (activeBooking.promotion?.code) {
          appliedPromotionCode.value = activeBooking.promotion.code
        }
        isHoldExpired.value = false
        startCountdown(activeBooking.holdExpiresAt)
        toast.info(
          'Khôi phục đơn đặt vé',
          `Bạn đang có đơn đặt vé ${activeBooking.bookingCode} đang giữ chỗ cho suất chiếu này.`
        )
      }
    }
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('booking.loadSeatsError')
  } finally {
    isLoading.value = false
  }
}

function handleToggleSeat(seat: ShowtimeSeatStatusResponse) {
  if (createdBooking.value) return // Block modifications after hold is created

  const idx = selectedSeatIds.value.indexOf(seat.id)
  if (idx > -1) {
    selectedSeatIds.value.splice(idx, 1)
  } else {
    if (selectedSeatIds.value.length >= 8) {
      toast.warning(t('booking.maxSeatsReached'))
      return
    }
    selectedSeatIds.value.push(seat.id)
  }
}

function handleRemoveSeat(seatId: string) {
  if (createdBooking.value) return
  const idx = selectedSeatIds.value.indexOf(seatId)
  if (idx > -1) {
    selectedSeatIds.value.splice(idx, 1)
  }
}

async function handleCreateBooking() {
  if (selectedSeatIds.value.length === 0) {
    toast.warning(t('booking.noSeatsSelected'))
    return
  }

  isSubmitting.value = true
  conflictMessage.value = ''

  try {
    const response = await bookingService.createBooking({
      showtimeId: showtimeId.value,
      seatIds: selectedSeatIds.value,
      promotionCode: appliedPromotionCode.value ? appliedPromotionCode.value.trim().toUpperCase() : undefined,
    })

    createdBooking.value = response
    isHoldExpired.value = false
    startCountdown(response.holdExpiresAt)

    toast.success(
      t('booking.holdSuccessTitle'),
      `Mã đặt vé: ${response.bookingCode}`
    )
  } catch (err: any) {
    const msg = err.response?.data?.message || t('booking.seatConflictDesc')
    const status = err.response?.status

    if (status === 409 || status === 400) {
      conflictMessage.value = msg
      toast.error(t('booking.seatConflictTitle'), msg)
      // Refresh seat map to reflect newest availability
      await refreshSeatMap()
    } else {
      toast.error(t('common.errorTitle'), msg)
    }
  } finally {
    isSubmitting.value = false
  }
}

async function refreshSeatMap() {
  if (!showtimeId.value) return
  try {
    const seatsData = await showtimeService.getShowtimeSeats(showtimeId.value)
    seats.value = seatsData

    // Remove any selected seat that is no longer AVAILABLE and not held by current user
    const availableOrOwnIds = new Set(
      seatsData.filter((s) => s.availabilityStatus === 'AVAILABLE' || !!s.isHeldByCurrentUser).map((s) => s.id)
    )
    selectedSeatIds.value = selectedSeatIds.value.filter((id) => availableOrOwnIds.has(id))
  } catch (err) {
    console.error('Failed to refresh seat map', err)
  }
}

function startCountdown(expiresAtIso: string) {
  stopCountdown()
  if (!expiresAtIso) return

  const targetTime = new Date(expiresAtIso).getTime()

  const tick = () => {
    const now = Date.now()
    const diff = Math.max(0, Math.floor((targetTime - now) / 1000))
    holdRemainingSeconds.value = diff

    if (diff <= 0) {
      isHoldExpired.value = true
      stopCountdown()
      toast.error(t('booking.holdExpiredTitle'), t('booking.holdExpiredDesc'))
    }
  }

  tick()
  countdownTimer = setInterval(tick, 1000)
}

function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

async function handleReselectSeats() {
  if (createdBooking.value) {
    try {
      await bookingService.cancelBooking(createdBooking.value.id, { reason: 'Khách hàng chọn lại ghế' })
    } catch (e) {
      console.warn('Could not cancel booking on reselect', e)
    }
  }
  stopCountdown()
  createdBooking.value = null
  isHoldExpired.value = false
  holdRemainingSeconds.value = 0
  selectedSeatIds.value = []
  await refreshSeatMap()
}

async function handleProceedToPayment() {
  if (!createdBooking.value) return
  isSubmitting.value = true

  try {
    const res = await paymentService.initiatePayment(createdBooking.value.id, {
      paymentMethod: 'VNPAY',
    })
    window.location.href = res.paymentUrl
  } catch (err: any) {
    const msg = err.response?.data?.message || 'Không thể khởi tạo thanh toán VNPay.'
    toast.error(t('common.errorTitle'), msg)
    isSubmitting.value = false
  }
}

onMounted(() => {
  loadBookingData()
})

onUnmounted(() => {
  stopCountdown()
})
</script>

<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-12 space-y-8">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl sm:text-3xl font-bold text-white tracking-tight flex items-center gap-2.5">
          <span class="w-2.5 h-6 rounded-full bg-indigo-600 inline-block"></span>
          {{ t('booking.pageTitle') }}
        </h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ showtime?.movie?.title ? `${showtime.movie.title} • ${showtime.cinema?.name}` : 'Chọn ghế xem phim trực tuyến' }}
        </p>
      </div>

      <router-link
        to="/showtimes"
        class="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-400 hover:text-indigo-400 transition-colors"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
        </svg>
        {{ t('booking.backToShowtimes') }}
      </router-link>
    </div>

    <!-- Error State -->
    <div v-if="errorMessage" class="space-y-4">
      <ErrorAlert :message="errorMessage" @retry="loadBookingData" />
      <div class="text-center pt-4">
        <router-link to="/showtimes">
          <Button variant="secondary" size="md">{{ t('booking.backToShowtimes') }}</Button>
        </router-link>
      </div>
    </div>

    <!-- Loading Skeleton (Maintains exact page layout height) -->
    <div v-else-if="isLoading" class="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start animate-pulse">
      <div class="lg:col-span-8 space-y-6">
        <div class="h-16 rounded-2xl bg-slate-800"></div>
        <div class="h-96 rounded-2xl bg-slate-800"></div>
      </div>
      <div class="lg:col-span-4">
        <div class="h-96 rounded-2xl bg-slate-800"></div>
      </div>
    </div>

    <!-- Main Booking Experience Layout -->
    <div v-else class="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
      <!-- Left: Seat Legend & Cinema Seat Map (8 cols) -->
      <div class="lg:col-span-8 space-y-6">
        <!-- Conflict Alert Banner if seat conflict occurred -->
        <div
          v-if="conflictMessage"
          class="p-4 rounded-2xl bg-rose-950/70 border border-rose-800 text-rose-200 text-xs flex items-start gap-3 shadow-lg animate-shake"
        >
          <svg class="w-5 h-5 text-rose-400 shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <div class="space-y-0.5">
            <p class="font-bold text-white">{{ t('booking.seatConflictTitle') }}</p>
            <p>{{ conflictMessage }}</p>
          </div>
        </div>

        <!-- Seat Legend -->
        <SeatLegend />

        <!-- Seat Map Grid -->
        <SeatMap
          :seats="seats"
          :selected-seat-ids="selectedSeatIds"
          :disabled="!!createdBooking || isSubmitting"
          @toggle-seat="handleToggleSeat"
        />
      </div>

      <!-- Right: Booking Summary & Hold Panel (4 cols) -->
      <div class="lg:col-span-4 sticky top-24">
        <BookingSummary
          :showtime="showtime"
          :selected-seats="selectedSeatsObjects"
          :created-booking="createdBooking"
          :is-submitting="isSubmitting"
          :hold-remaining-seconds="holdRemainingSeconds"
          :is-hold-expired="isHoldExpired"
          :applied-promotion-code="appliedPromotionCode"
          @update:promotion-code="appliedPromotionCode = $event"
          @remove-seat="handleRemoveSeat"
          @submit-booking="handleCreateBooking"
          @reselect-seats="handleReselectSeats"
          @proceed-to-payment="handleProceedToPayment"
        />
      </div>
    </div>
  </div>
</template>
