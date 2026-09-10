<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import type { ShowtimeDetailResponse, ShowtimeSeatStatusResponse } from '@/types/showtime.types'
import type { BookingDetailResponse } from '@/types/booking.types'
import type { ValidatePromotionResponse, PromotionResponse } from '@/types/promotion.types'
import promotionService from '@/services/promotion.service'
import { formatCurrency, formatDate, formatTime, formatDuration } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Badge from '@/components/common/Badge.vue'
import Button from '@/components/common/Button.vue'
import Modal from '@/components/common/Modal.vue'

interface Props {
  showtime: ShowtimeDetailResponse | null
  selectedSeats: ShowtimeSeatStatusResponse[]
  createdBooking: BookingDetailResponse | null
  foodAmount?: number
  isSubmitting?: boolean
  holdRemainingSeconds?: number
  isHoldExpired?: boolean
  appliedPromotionCode?: string
  hasAdjacencyViolation?: boolean
  adjacencyWarningMessage?: string
}

interface Emits {
  (e: 'removeSeat', seatId: string): void
  (e: 'submitBooking'): void
  (e: 'reselectSeats'): void
  (e: 'proceedToPayment'): void
  (e: 'update:promotionCode', code: string): void
}

const props = withDefaults(defineProps<Props>(), {
  foodAmount: 0,
  isSubmitting: false,
  holdRemainingSeconds: 0,
  isHoldExpired: false,
  appliedPromotionCode: '',
  hasAdjacencyViolation: false,
  adjacencyWarningMessage: '',
})

const emit = defineEmits<Emits>()
const { t, locale } = useI18n()

// Promotion Input & State
const promoInput = ref<string>('')
const isValidatingPromo = ref<boolean>(false)
const promoError = ref<string>('')
const validatedPromo = ref<ValidatePromotionResponse | null>(null)

// Available Promotions Modal & Selection
const showPromoModal = ref<boolean>(false)
const availablePromotions = ref<PromotionResponse[]>([])
const isLoadingPromos = ref<boolean>(false)
const promoFetchError = ref<string>('')

// Estimated gross price calculation before booking creation using authoritative calculatedPrice
const estimatedGross = computed(() => {
  return props.selectedSeats.reduce((sum, s) => {
    if (s.calculatedPrice !== undefined && s.calculatedPrice !== null) {
      return sum + Number(s.calculatedPrice)
    }
    const base = Number(props.showtime?.basePrice) || 0
    const mod = Number(s.priceModifier) || 0
    const cap = Number(s.capacity) || 1
    return sum + (base * cap + mod)
  }, 0)
})

// Estimated food amount from props
const estimatedFood = computed(() => Number(props.foodAmount) || 0)

// Estimated final price with preview discount and food concessions
const estimatedFinal = computed(() => {
  const ticketNet = validatedPromo.value ? validatedPromo.value.finalAmount : estimatedGross.value
  return ticketNet + estimatedFood.value
})

// Format remaining countdown time (MM:SS)
const formattedRemainingTime = computed(() => {
  const s = Math.max(0, props.holdRemainingSeconds)
  const mins = Math.floor(s / 60)
  const secs = s % 60
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
})

async function fetchAvailablePromotions() {
  isLoadingPromos.value = true
  promoFetchError.value = ''
  try {
    const data = await promotionService.getAvailablePromotions()
    availablePromotions.value = data || []
  } catch (err: any) {
    promoFetchError.value = err.response?.data?.message || 'Không thể tải danh sách ưu đãi.'
  } finally {
    isLoadingPromos.value = false
  }
}

function openPromoModal() {
  showPromoModal.value = true
  fetchAvailablePromotions()
}

function isPromoEligible(promo: PromotionResponse): boolean {
  if (!promo.minOrderAmount) return true
  return estimatedGross.value >= Number(promo.minOrderAmount)
}

function selectPromotion(promo: PromotionResponse) {
  promoInput.value = promo.code
  showPromoModal.value = false
  applyPromotion()
}

async function applyPromotion() {
  promoError.value = ''
  const code = promoInput.value.trim().toUpperCase()
  if (!code) {
    promoError.value = t('booking.promoRequired')
    return
  }

  if (estimatedGross.value <= 0) {
    promoError.value = t('booking.promoNoSeats')
    return
  }

  isValidatingPromo.value = true
  try {
    const res = await promotionService.validatePromotionCode(code, estimatedGross.value)
    if (res.valid) {
      validatedPromo.value = res
      emit('update:promotionCode', code)
    } else {
      validatedPromo.value = null
      promoError.value = res.message || t('booking.promoInvalid')
      emit('update:promotionCode', '')
    }
  } catch (err: any) {
    validatedPromo.value = null
    promoError.value = err.response?.data?.message || t('booking.promoInvalid')
    emit('update:promotionCode', '')
  } finally {
    isValidatingPromo.value = false
  }
}

function removePromotion() {
  promoInput.value = ''
  validatedPromo.value = null
  promoError.value = ''
  emit('update:promotionCode', '')
}

// Pre-populate promo input if parent passed appliedPromotionCode
watch(
  () => props.appliedPromotionCode,
  (newCode) => {
    if (newCode && !promoInput.value) {
      promoInput.value = newCode
      if (estimatedGross.value > 0 && !validatedPromo.value) {
        applyPromotion()
      }
    }
  },
  { immediate: true }
)

// If selected seats change and discount was applied, revalidate preview
watch(
  () => estimatedGross.value,
  (newGross) => {
    if (validatedPromo.value && newGross > 0) {
      applyPromotion()
    } else if (newGross === 0) {
      removePromotion()
    }
  }
)

onMounted(() => {
  fetchAvailablePromotions()
})
</script>

<template>
  <div class="rounded-2xl bg-slate-800/90 border border-slate-700/80 p-5 sm:p-6 shadow-xl space-y-6">
    <!-- Showtime & Movie Header Summary -->
    <div v-if="showtime" class="flex items-start gap-4 pb-5 border-b border-slate-700/80">
      <!-- Mini Poster -->
      <div class="w-16 sm:w-20 aspect-[2/3] rounded-xl overflow-hidden bg-slate-900 border border-slate-700 shrink-0 shadow-md">
        <img
          v-if="showtime.movie?.posterUrl"
          :src="showtime.movie.posterUrl"
          :alt="showtime.movie.title"
          class="w-full h-full object-cover"
        />
        <div v-else class="w-full h-full flex items-center justify-center text-[10px] text-slate-500 p-1 text-center">
          {{ showtime.movie?.title }}
        </div>
      </div>

      <!-- Movie & Showtime Info -->
      <div class="space-y-1.5 flex-1 min-w-0">
        <div class="flex items-center gap-1.5">
          <Badge v-if="showtime.movie?.ageRating" variant="warning" size="sm" class="font-black">
            {{ showtime.movie.ageRating }}
          </Badge>
          <Badge variant="primary" size="sm">
            {{ showtime.format }}
          </Badge>
        </div>

        <h3 class="text-base sm:text-lg font-bold text-white tracking-tight truncate" :title="showtime.movie?.title">
          {{ showtime.movie?.title }}
        </h3>

        <p v-if="showtime.movie?.durationMinutes" class="text-xs text-slate-400">
          ⏱️ {{ formatDuration(showtime.movie.durationMinutes, locale) }}
        </p>

        <p class="text-xs text-slate-300 font-medium truncate">
          🏢 {{ showtime.cinema?.name }}
        </p>

        <p class="text-xs text-slate-400">
          📍 {{ showtime.auditorium?.name }} • {{ formatDate(showtime.startTime) }}
        </p>

        <p class="text-xs font-semibold text-indigo-300">
          ⏰ {{ formatTime(showtime.startTime) }} ~ {{ formatTime(showtime.endTime) }}
        </p>
      </div>
    </div>

    <!-- Active Hold / Success State -->
    <div v-if="createdBooking" class="space-y-4">
      <!-- Hold Countdown Alert Box -->
      <div
        :class="[
          'p-4 rounded-xl border flex items-center justify-between gap-4 transition-colors',
          isHoldExpired
            ? 'bg-rose-950/60 border-rose-800 text-rose-200'
            : holdRemainingSeconds <= 30
              ? 'bg-rose-950/70 border-rose-500 text-rose-200 animate-pulse'
              : holdRemainingSeconds < 60
                ? 'bg-amber-950/70 border-amber-500 text-amber-200 animate-pulse'
                : 'bg-indigo-950/60 border-indigo-700/80 text-indigo-200'
        ]"
      >
        <div class="space-y-0.5">
          <p class="text-xs font-semibold uppercase tracking-wider">
            {{ isHoldExpired ? t('booking.holdExpiredTitle') : t('booking.holdTimerLabel') }}
          </p>
          <p class="text-xs opacity-80">
            {{ isHoldExpired ? t('booking.holdExpiredDesc') : t('booking.holdSuccessSubtitle') }}
          </p>
        </div>

        <div v-if="!isHoldExpired" class="text-2xl sm:text-3xl font-mono font-black shrink-0 text-white drop-shadow tabular-nums tracking-wider">
          {{ formattedRemainingTime }}
        </div>
      </div>

      <!-- Booking Code & Authoritative Details -->
      <div class="p-4 rounded-xl bg-slate-900/90 border border-slate-700/80 space-y-3 text-xs">
        <div class="flex items-center justify-between pb-2 border-b border-slate-800">
          <span class="text-slate-400">{{ t('booking.bookingCode') }}</span>
          <span class="font-mono font-bold text-indigo-400 text-sm tracking-wider">{{ createdBooking.bookingCode }}</span>
        </div>

        <div class="flex items-center justify-between">
          <span class="text-slate-400">{{ t('booking.selectedSeats') }}</span>
          <span class="font-bold text-slate-200">
            {{ createdBooking.seats?.map(s => s.seatCode).join(', ') }}
          </span>
        </div>

        <!-- Ticket price -->
        <div class="flex items-center justify-between text-slate-400">
          <span>{{ t('booking.ticketPriceLabel') }}</span>
          <span>{{ formatCurrency(createdBooking.grossAmount) }}</span>
        </div>

        <!-- Food Concessions Snapshot -->
        <div v-if="createdBooking.foods && createdBooking.foods.length > 0" class="pt-2 border-t border-slate-800 space-y-1.5">
          <div class="flex items-center justify-between text-slate-300">
            <span class="font-semibold">🍿 {{ t('booking.foodPriceLabel') }}</span>
            <span class="font-mono text-emerald-400 font-bold">{{ formatCurrency(createdBooking.foodAmount || 0) }}</span>
          </div>
          <div
            v-for="food in createdBooking.foods"
            :key="food.id"
            class="flex items-center justify-between text-[11px] text-slate-400 pl-2"
          >
            <span>{{ food.foodName }} × {{ food.quantity }}</span>
            <span class="font-mono">{{ formatCurrency(food.subtotal) }}</span>
          </div>
        </div>

        <!-- Applied Promotion Snapshot -->
        <div v-if="createdBooking.discountAmount > 0" class="flex items-center justify-between text-emerald-400">
          <span class="flex items-center gap-1.5">
            <span>🏷️ {{ t('booking.discountAmount') }} ({{ createdBooking.promotion?.code || t('booking.discount') }})</span>
          </span>
          <span class="font-bold">-{{ formatCurrency(createdBooking.discountAmount) }}</span>
        </div>

        <div class="pt-2 border-t border-slate-800 flex items-center justify-between text-sm">
          <span class="font-bold text-white">{{ t('booking.finalTotal') }}</span>
          <span class="text-lg font-black text-emerald-400">
            {{ formatCurrency(createdBooking.totalAmount) }}
          </span>
        </div>
      </div>

      <!-- Khối chọn Phương thức thanh toán (Payment Method Selector) -->
      <div class="space-y-3 pt-2">
        <h4 class="text-xs font-bold uppercase tracking-wider text-slate-300">
          Phương thức thanh toán
        </h4>

        <!-- Tùy chọn duy nhất hiện tại: VNPay -->
        <div class="p-3.5 rounded-xl bg-slate-900/90 border-2 border-indigo-500/80 flex items-start gap-3 shadow-md">
          <div class="mt-0.5 text-indigo-400">
            <span class="flex h-4 w-4 rounded-full border-2 border-indigo-500 items-center justify-center">
              <span class="h-2 w-2 rounded-full bg-indigo-500"></span>
            </span>
          </div>
          <div class="flex-1 min-w-0 space-y-0.5">
            <div class="flex items-center justify-between">
              <span class="text-xs font-bold text-white tracking-tight">Cổng thanh toán VNPay</span>
              <span class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">Mặc định</span>
            </div>
            <p class="text-[11px] text-slate-400">
              Thanh toán an toàn qua cổng VNPay (Thẻ ATM nội địa, Internet Banking, VNPAY-QR, Thẻ quốc tế).
            </p>
          </div>
        </div>

        <!-- Hướng dẫn kiểm thử môi trường Sandbox -->
        <div class="p-3 rounded-xl bg-amber-950/40 border border-amber-800/60 text-amber-200 text-xs space-y-1.5">
          <div class="flex items-center gap-1.5 font-bold text-amber-300">
            <svg class="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>Môi trường thử nghiệm VNPay Sandbox</span>
          </div>
          <p class="text-[11px] text-amber-200/90 leading-relaxed">
            Để giao dịch thành công trên cổng Sandbox:
            <br />1. Chọn <strong>"Thẻ nội địa và tài khoản ngân hàng"</strong>
            <br />2. Chọn ngân hàng <strong>"NCB"</strong>
            <br />3. Dùng thông tin thẻ kiểm thử do VNPAY cung cấp để thanh toán.
          </p>
        </div>
      </div>

      <!-- Action CTA Buttons -->
      <div class="space-y-2 pt-2">
        <Button
          v-if="!isHoldExpired"
          variant="primary"
          size="lg"
          block
          class="shadow-xl shadow-indigo-600/30"
          @click="emit('proceedToPayment')"
        >
          <template #prefix>
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v6a2 2 0 002 2zm7-5a2 2 0 11-4 0 2 2 0 014 0z" />
            </svg>
          </template>
          {{ t('booking.proceedToPaymentBtn') }}
        </Button>

        <Button
          v-else
          variant="secondary"
          size="lg"
          block
          @click="emit('reselectSeats')"
        >
          {{ t('booking.reselectSeatsBtn') }}
        </Button>
      </div>
    </div>

    <!-- Seat Selection State (Before Hold Creation) -->
    <div v-else class="space-y-4">
      <!-- Selected Seats List -->
      <div class="space-y-2">
        <div class="flex items-center justify-between text-xs">
          <span class="font-semibold text-slate-300">
            {{ t('booking.selectedSeatsTitle', { count: selectedSeats.length }) }}
          </span>
          <span class="text-slate-400">
            {{ t('booking.maxSeatsNote') }}
          </span>
        </div>

        <div v-if="selectedSeats.length === 0" class="p-4 rounded-xl bg-slate-900/60 border border-slate-700/60 text-center text-xs text-slate-500">
          {{ t('booking.noSeatsSelected') }}
        </div>

        <transition-group
          v-else
          tag="div"
          name="fade-fast"
          class="flex flex-wrap gap-2 max-h-40 overflow-y-auto pr-1"
        >
          <div
            v-for="seat in selectedSeats"
            :key="seat.id"
            class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-slate-900 border border-slate-700 text-xs text-slate-200 transition-all duration-150"
          >
            <span class="font-bold text-white">{{ seat.seatCode }}</span>
            <span class="text-[10px] text-slate-400">({{ seat.seatTypeName }})</span>
            <span v-if="seat.calculatedPrice" class="text-[10px] font-mono text-emerald-400 font-bold ml-0.5">
              {{ formatCurrency(seat.calculatedPrice) }}
            </span>
            <button
              type="button"
              class="text-slate-400 hover:text-rose-400 ml-1 p-0.5"
              aria-label="Bỏ chọn ghế"
              @click="emit('removeSeat', seat.id)"
            >
              ✕
            </button>
          </div>
        </transition-group>
      </div>

      <!-- Promotion Code Input Section -->
      <div class="pt-3 border-t border-slate-700/80 space-y-2.5">
        <div class="flex items-center justify-between">
          <label for="promoCodeInput" class="block text-xs font-semibold text-slate-300">
            🏷️ {{ t('booking.promoInputLabel') }}
          </label>
          <span v-if="validatedPromo" class="text-[11px] text-emerald-400 font-semibold flex items-center gap-1">
            <span>✓</span> {{ t('booking.promoAppliedSuccess') }}
          </span>
        </div>

        <transition name="fade-fast" mode="out-in">
          <!-- If Promotion Applied: Display applied promotion: '✓ Giảm X đ [Bỏ chọn]' -->
          <div
            v-if="validatedPromo"
            key="applied"
            class="p-3 rounded-xl bg-emerald-950/60 border border-emerald-500/70 text-xs shadow-sm space-y-1.5"
          >
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2 font-bold text-emerald-300">
                <span class="inline-flex items-center justify-center w-5 h-5 rounded-full bg-emerald-500/20 text-emerald-400 font-black text-xs">
                  ✓
                </span>
                <span class="text-sm font-black text-white">
                  Giảm {{ formatCurrency(validatedPromo.discountAmount) }}
                </span>
              </div>
              <button
                type="button"
                class="text-xs font-bold text-rose-400 hover:text-rose-300 hover:underline px-1.5 py-0.5 rounded transition-colors cursor-pointer"
                :title="t('booking.deselectPromoBtn')"
                @click="removePromotion"
              >
                [{{ t('booking.deselectPromoBtn') }}]
              </button>
            </div>

            <div class="flex items-center gap-2 text-[11px] text-slate-300 pt-1 border-t border-emerald-800/40">
              <span class="font-mono font-bold text-emerald-400 uppercase tracking-wider px-1.5 py-0.5 rounded bg-emerald-900/60 border border-emerald-700/50">
                {{ validatedPromo.code }}
              </span>
              <span v-if="validatedPromo.name" class="text-slate-300 truncate">
                {{ validatedPromo.name }}
              </span>
            </div>
          </div>

          <!-- Input Box & Available Promotions Button If Not Applied -->
          <div v-else key="input" class="space-y-2">
            <!-- Button '🎁 Chọn ưu đãi' to open available promotions modal -->
            <button
              type="button"
              class="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl bg-indigo-950/50 border border-indigo-700/70 hover:bg-indigo-900/60 hover:border-indigo-500 text-indigo-200 transition-all text-xs font-semibold shadow-sm group cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
              :disabled="selectedSeats.length === 0"
              @click="openPromoModal"
            >
              <div class="flex items-center gap-2">
                <span class="text-sm">🎁</span>
                <span>{{ t('booking.selectPromoBtn') }}</span>
                <span
                  v-if="availablePromotions.length > 0"
                  class="px-1.5 py-0.5 rounded-full bg-indigo-500/30 text-indigo-300 text-[10px] font-bold border border-indigo-500/40"
                >
                  {{ availablePromotions.length }}
                </span>
              </div>
              <span class="text-indigo-400 group-hover:text-indigo-200 group-hover:translate-x-0.5 transition-all text-xs">
                Xem danh sách →
              </span>
            </button>

            <!-- Manual code input and 'Áp dụng' button intact -->
            <div class="space-y-1.5">
              <div class="flex items-center gap-2">
                <input
                  id="promoCodeInput"
                  v-model="promoInput"
                  type="text"
                  class="flex-1 px-3 py-2 rounded-xl bg-slate-900 border border-slate-700 text-white font-mono text-xs uppercase placeholder:normal-case placeholder:font-sans placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  :placeholder="t('booking.promoPlaceholder')"
                  :disabled="selectedSeats.length === 0 || isValidatingPromo"
                  @keyup.enter="applyPromotion"
                />
                <Button
                  variant="secondary"
                  size="sm"
                  :disabled="selectedSeats.length === 0 || !promoInput.trim() || isValidatingPromo"
                  :loading="isValidatingPromo"
                  @click="applyPromotion"
                >
                  {{ t('booking.applyPromoBtn') }}
                </Button>
              </div>

              <p v-if="promoError" class="text-[11px] text-rose-400 font-medium">
                ⚠️ {{ promoError }}
              </p>
            </div>
          </div>
        </transition>
      </div>

      <!-- Estimated Price Preview Breakdown -->
      <div class="pt-3 border-t border-slate-700/80 space-y-1.5 text-xs">
        <!-- Ticket price -->
        <div class="flex items-center justify-between text-slate-400">
          <span>{{ t('booking.ticketPriceLabel') }} ({{ selectedSeats.length }} vé)</span>
          <span class="font-medium text-slate-300">
            {{ formatCurrency(estimatedGross) }}
          </span>
        </div>

        <!-- Food price -->
        <div v-if="estimatedFood > 0" class="flex items-center justify-between text-slate-400">
          <span>🍿 {{ t('booking.foodPriceLabel') }}</span>
          <span class="font-medium text-slate-300">
            {{ formatCurrency(estimatedFood) }}
          </span>
        </div>

        <!-- Discount -->
        <div v-if="validatedPromo && validatedPromo.discountAmount > 0" class="flex items-center justify-between text-emerald-400">
          <span class="flex items-center gap-1">
            <span>🏷️ {{ t('booking.discountAmount') }}</span>
            <span class="text-[10px] font-mono font-semibold">({{ validatedPromo.code }})</span>
          </span>
          <span class="font-bold">
            -{{ formatCurrency(validatedPromo.discountAmount) }}
          </span>
        </div>

        <!-- Final total -->
        <div class="flex items-center justify-between pt-1 text-sm font-bold text-white border-t border-slate-700/60">
          <span>{{ t('booking.finalTotal') }}</span>
          <span class="text-base font-black text-emerald-400">
            {{ formatCurrency(estimatedFinal) }}
          </span>
        </div>

        <p class="text-[10px] text-slate-500 italic leading-normal pt-1">
          * {{ t('booking.authoritativeNotice') }}
        </p>
      </div>

      <!-- Submit CTA Button -->
      <div class="pt-2 space-y-1.5">
        <Button
          variant="primary"
          size="lg"
          block
          :disabled="selectedSeats.length === 0 || isSubmitting || hasAdjacencyViolation"
          :loading="isSubmitting"
          class="shadow-xl shadow-indigo-600/30"
          @click="emit('submitBooking')"
        >
          <template #prefix>
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
          </template>
          {{ isSubmitting ? t('booking.creatingHold') : t('booking.holdSeatsBtn') }}
        </Button>

        <p v-if="hasAdjacencyViolation" class="text-[11px] text-amber-400 text-center font-medium leading-snug">
          ⚠️ {{ adjacencyWarningMessage || t('booking.orphanSeatGenericWarning') }}
        </p>
      </div>
    </div>

    <!-- Available Promotions Modal -->
    <Modal
      v-model="showPromoModal"
      :title="t('booking.availablePromosTitle')"
      size="md"
    >
      <div class="space-y-4">
        <p class="text-xs text-slate-400">
          {{ t('booking.availablePromosSubtitle') }}
        </p>

        <!-- Loading State -->
        <div v-if="isLoadingPromos" class="space-y-3 py-2">
          <div v-for="i in 3" :key="i" class="h-24 rounded-xl bg-slate-700/40 animate-pulse"></div>
        </div>

        <!-- Error State -->
        <div
          v-else-if="promoFetchError"
          class="p-4 rounded-xl bg-rose-950/50 border border-rose-800 text-rose-200 text-xs space-y-2"
        >
          <p>{{ promoFetchError }}</p>
          <Button variant="secondary" size="sm" @click="fetchAvailablePromotions">
            Thử lại
          </Button>
        </div>

        <!-- Empty State -->
        <div
          v-else-if="availablePromotions.length === 0"
          class="py-10 text-center space-y-3 text-slate-400"
        >
          <div class="w-12 h-12 mx-auto rounded-full bg-slate-800 flex items-center justify-center text-2xl">
            🎟️
          </div>
          <p class="text-xs font-medium">{{ t('booking.noAvailablePromos') }}</p>
        </div>

        <!-- Promotions Cards List -->
        <div v-else class="space-y-3 max-h-[60vh] overflow-y-auto pr-1">
          <div
            v-for="promo in availablePromotions"
            :key="promo.id"
            :class="[
              'p-3.5 rounded-xl border transition-all space-y-2.5',
              isPromoEligible(promo)
                ? 'bg-slate-900/90 border-slate-700/80 hover:border-indigo-500/80 shadow-md'
                : 'bg-slate-900/50 border-slate-800/80 opacity-70'
            ]"
          >
            <!-- Card Header: Code, discount badge & eligibility badge -->
            <div class="flex items-start justify-between gap-2">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-mono font-bold text-xs uppercase tracking-wider px-2 py-0.5 rounded bg-indigo-950/80 text-indigo-300 border border-indigo-700/60">
                  {{ promo.code }}
                </span>
                <span class="px-2 py-0.5 rounded text-[11px] font-bold bg-emerald-950/80 text-emerald-300 border border-emerald-700/60">
                  {{ promo.discountType === 'PERCENTAGE' ? `Giảm ${promo.discountValue}%` : `Giảm ${formatCurrency(promo.discountValue)}` }}
                </span>
              </div>
              <span
                :class="[
                  'px-2 py-0.5 rounded text-[10px] font-bold shrink-0',
                  isPromoEligible(promo)
                    ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40'
                    : 'bg-slate-800 text-slate-400 border border-slate-700'
                ]"
              >
                {{ isPromoEligible(promo) ? t('booking.promoEligible') : 'Chưa đủ ĐK' }}
              </span>
            </div>

            <!-- Card Body: Name & Description -->
            <div class="space-y-1">
              <h4 class="text-xs sm:text-sm font-bold text-white tracking-tight">
                {{ promo.name }}
              </h4>
              <p v-if="promo.description" class="text-xs text-slate-400 leading-relaxed">
                {{ promo.description }}
              </p>
            </div>

            <!-- Card Conditions & Dates -->
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1 pt-2 text-[11px] text-slate-400 border-t border-slate-800">
              <span v-if="promo.minOrderAmount">
                🛒 {{ t('booking.minOrderAmountReq', { amount: formatCurrency(promo.minOrderAmount) }) }}
              </span>
              <span v-if="promo.maxDiscountAmount && promo.discountType === 'PERCENTAGE'">
                ⚡ {{ t('booking.maxDiscountAmountNote', { amount: formatCurrency(promo.maxDiscountAmount) }) }}
              </span>
              <span>
                📅 {{ t('booking.promoExpiry', { date: formatDate(promo.endAt) }) }}
              </span>
              <span v-if="promo.remainingUses !== undefined && promo.remainingUses !== null">
                🎟️ {{ t('booking.promoRemainingUses', { count: promo.remainingUses }) }}
              </span>
            </div>

            <!-- Card Action Footer -->
            <div class="flex items-center justify-between pt-1">
              <div class="text-[11px]">
                <span v-if="!isPromoEligible(promo)" class="text-amber-400 font-medium">
                  {{ t('booking.promoIneligible', { amount: formatCurrency((promo.minOrderAmount || 0) - estimatedGross) }) }}
                </span>
              </div>

              <Button
                variant="primary"
                size="sm"
                :disabled="!isPromoEligible(promo) || isValidatingPromo"
                :loading="isValidatingPromo && promoInput === promo.code"
                @click="selectPromotion(promo)"
              >
                {{ t('booking.applyPromoAction') }}
              </Button>
            </div>
          </div>
        </div>
      </div>
    </Modal>
  </div>
</template>
