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
import { formatCurrency } from '@/utils/formatters'
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

const legendSeatTypes = computed(() => {
  const map = new Map<string, { id?: string; name: string; capacity?: number; colorToken?: string; icon?: string }>()
  for (const s of seats.value) {
    if (s.seatTypeName && !map.has(s.seatTypeName)) {
      map.set(s.seatTypeName, {
        name: s.seatTypeName,
        capacity: s.capacity,
        colorToken: s.colorToken,
        icon: s.icon,
      })
    }
  }
  return Array.from(map.values())
})

// Hold State
const createdBooking = ref<BookingDetailResponse | null>(null)
const holdRemainingSeconds = ref<number>(0)
const isHoldExpired = ref<boolean>(false)
let countdownTimer: any = null

const selectedSeatsObjects = computed<ShowtimeSeatStatusResponse[]>(() => {
  return seats.value.filter((s) => selectedSeatIds.value.includes(s.id))
})

const mobileDisplayPrice = computed<number>(() => {
  if (createdBooking.value) {
    return createdBooking.value.totalAmount
  }
  if (!showtime.value) return 0
  const base = Number(showtime.value.basePrice) || 0
  return selectedSeatsObjects.value.reduce((sum, s) => {
    const mod = Number(s.priceModifier) || 0
    return sum + (base + mod)
  }, 0)
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
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-12 space-y-8 pb-28 lg:pb-12">
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

    <transition name="fade-fast" mode="out-in">
      <!-- Error State -->
      <div v-if="errorMessage" key="error" class="space-y-4">
        <ErrorAlert :message="errorMessage" @retry="loadBookingData" />
        <div class="text-center pt-4">
          <router-link to="/showtimes">
            <Button variant="secondary" size="md">{{ t('booking.backToShowtimes') }}</Button>
          </router-link>
        </div>
      </div>

      <!-- Loading Skeleton (Maintains exact page layout height) -->
      <div v-else-if="isLoading" key="loading" class="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        <div class="lg:col-span-8 space-y-6">
          <div class="h-16 rounded-2xl bg-slate-800/70 border border-slate-700/60 animate-shimmer"></div>
          <div class="h-96 rounded-2xl bg-slate-800/70 border border-slate-700/60 animate-shimmer"></div>
        </div>
        <div class="lg:col-span-4">
          <div class="h-96 rounded-2xl bg-slate-800/70 border border-slate-700/60 animate-shimmer"></div>
        </div>
      </div>

      <!-- Main Booking Experience Layout -->
      <div v-else key="content" class="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
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
          <SeatLegend :seat-types="legendSeatTypes" />

          <!-- Seat Map Grid -->
          <SeatMap
            :seats="seats"
            :selected-seat-ids="selectedSeatIds"
            :columns-count="showtime?.auditorium?.columnsCount"
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
    </transition>

    <!-- Mobile Sticky Summary Bar (Hidden on desktop lg:) -->
    <div
      v-if="!isLoading && !errorMessage"
      class="fixed bottom-0 left-0 right-0 z-30 lg:hidden bg-slate-900/95 backdrop-blur-md border-t border-slate-800 px-4 py-3 pb-safe shadow-2xl transition-all duration-300"
    >
      <div class="max-w-md mx-auto flex items-center justify-between gap-3">
        <!-- Left: Summary info -->
        <div class="space-y-0.5 min-w-0">
          <div class="flex items-center gap-1.5 truncate">
            <span class="text-xs font-semibold text-slate-300 truncate">
              {{ createdBooking ? createdBooking.bookingCode : (selectedSeatIds.length > 0 ? t('booking.selectedSeatsTitle', { count: selectedSeatIds.length }) : t('booking.noSeatsSelected')) }}
            </span>
            <span v-if="!createdBooking && selectedSeatIds.length > 0" class="text-[11px] text-slate-400 shrink-0">
              ({{ selectedSeatsObjects.map(s => s.seatCode).join(', ') }})
            </span>
          </div>
          <div class="flex items-baseline gap-1.5">
            <span class="text-base font-black text-emerald-400">
              {{ formatCurrency(mobileDisplayPrice) }}
            </span>
            <span v-if="!createdBooking && selectedSeatIds.length > 0" class="text-[10px] text-slate-500">
              (tạm tính)
            </span>
          </div>
        </div>

        <!-- Right: Action CTA -->
        <div class="shrink-0">
          <Button
            v-if="createdBooking && !isHoldExpired"
            variant="primary"
            size="md"
            class="shadow-lg shadow-indigo-600/30"
            @click="handleProceedToPayment"
          >
            {{ t('booking.proceedToPaymentBtn') }}
          </Button>
          <Button
            v-else-if="createdBooking && isHoldExpired"
            variant="secondary"
            size="md"
            @click="handleReselectSeats"
          >
            {{ t('booking.reselectSeatsBtn') }}
          </Button>
          <Button
            v-else
            variant="primary"
            size="md"
            :disabled="selectedSeatIds.length === 0 || isSubmitting"
            :loading="isSubmitting"
            class="shadow-lg shadow-indigo-600/30"
            @click="handleCreateBooking"
          >
            {{ isSubmitting ? t('booking.creatingHold') : t('booking.holdSeatsBtn') }}
          </Button>
        </div>
      </div>
    </div>
  </div>
</template>
