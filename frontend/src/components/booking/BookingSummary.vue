<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { ShowtimeDetailResponse, ShowtimeSeatStatusResponse } from '@/types/showtime.types'
import type { BookingDetailResponse } from '@/types/booking.types'
import type { ValidatePromotionResponse } from '@/types/promotion.types'
import promotionService from '@/services/promotion.service'
import { formatCurrency, formatDate, formatTime, formatDuration } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Badge from '@/components/common/Badge.vue'
import Button from '@/components/common/Button.vue'

interface Props {
  showtime: ShowtimeDetailResponse | null
  selectedSeats: ShowtimeSeatStatusResponse[]
  createdBooking: BookingDetailResponse | null
  isSubmitting?: boolean
  holdRemainingSeconds?: number
  isHoldExpired?: boolean
  appliedPromotionCode?: string
}

interface Emits {
  (e: 'removeSeat', seatId: string): void
  (e: 'submitBooking'): void
  (e: 'reselectSeats'): void
  (e: 'proceedToPayment'): void
  (e: 'update:promotionCode', code: string): void
}

const props = withDefaults(defineProps<Props>(), {
  isSubmitting: false,
  holdRemainingSeconds: 0,
  isHoldExpired: false,
  appliedPromotionCode: '',
})

const emit = defineEmits<Emits>()
const { t, locale } = useI18n()

// Promotion Input & State
const promoInput = ref<string>('')
const isValidatingPromo = ref<boolean>(false)
const promoError = ref<string>('')
const validatedPromo = ref<ValidatePromotionResponse | null>(null)

// Estimated gross price calculation before booking creation
const estimatedGross = computed(() => {
  if (!props.showtime) return 0
  const base = Number(props.showtime.basePrice) || 0
  return props.selectedSeats.reduce((sum, s) => {
    const mod = Number(s.priceModifier) || 0
    return sum + (base + mod)
  }, 0)
})

// Estimated final price with preview discount
const estimatedFinal = computed(() => {
  if (!validatedPromo.value) return estimatedGross.value
  return validatedPromo.value.finalAmount
})

// Format remaining countdown time (MM:SS)
const formattedRemainingTime = computed(() => {
  const s = Math.max(0, props.holdRemainingSeconds)
  const mins = Math.floor(s / 60)
  const secs = s % 60
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
})

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

        <div v-if="!isHoldExpired" class="text-2xl sm:text-3xl font-mono font-black shrink-0 text-white drop-shadow">
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

        <!-- Gross Amount -->
        <div class="flex items-center justify-between text-slate-400">
          <span>{{ t('booking.subtotal') }}</span>
          <span>{{ formatCurrency(createdBooking.grossAmount) }}</span>
        </div>

        <!-- Applied Promotion Snapshot -->
        <div v-if="createdBooking.discountAmount > 0" class="flex items-center justify-between text-emerald-400">
          <span class="flex items-center gap-1.5">
            <span>🏷️ {{ createdBooking.promotion?.code || t('booking.discount') }}</span>
          </span>
          <span class="font-bold">-{{ formatCurrency(createdBooking.discountAmount) }}</span>
        </div>

        <div class="pt-2 border-t border-slate-800 flex items-center justify-between text-sm">
          <span class="font-bold text-white">{{ t('booking.authoritativeTotal') }}</span>
          <span class="text-lg font-black text-emerald-400">
            {{ formatCurrency(createdBooking.totalAmount) }}
          </span>
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
            Tối đa 8 ghế
          </span>
        </div>

        <div v-if="selectedSeats.length === 0" class="p-4 rounded-xl bg-slate-900/60 border border-slate-700/60 text-center text-xs text-slate-500">
          {{ t('booking.noSeatsSelected') }}
        </div>

        <div v-else class="flex flex-wrap gap-2 max-h-40 overflow-y-auto pr-1">
          <div
            v-for="seat in selectedSeats"
            :key="seat.id"
            class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-slate-900 border border-slate-700 text-xs text-slate-200"
          >
            <span class="font-bold text-white">{{ seat.seatCode }}</span>
            <span class="text-[10px] text-slate-400">({{ seat.seatTypeName }})</span>
            <button
              type="button"
              class="text-slate-400 hover:text-rose-400 ml-1 p-0.5"
              aria-label="Bỏ chọn ghế"
              @click="emit('removeSeat', seat.id)"
            >
              ✕
            </button>
          </div>
        </div>
      </div>

      <!-- Promotion Code Input Section -->
      <div class="pt-3 border-t border-slate-700/80 space-y-2">
        <label for="promoCodeInput" class="block text-xs font-semibold text-slate-300">
          🏷️ {{ t('booking.promoInputLabel') }}
        </label>

        <!-- If Promotion Applied -->
        <div
          v-if="validatedPromo"
          class="flex items-center justify-between p-2.5 rounded-xl bg-emerald-950/50 border border-emerald-600/80 text-xs"
        >
          <div class="space-y-0.5">
            <div class="flex items-center gap-2">
              <span class="font-mono font-bold text-emerald-400 uppercase tracking-wider">
                {{ validatedPromo.code }}
              </span>
              <span class="text-[10px] text-emerald-300">
                (-{{ formatCurrency(validatedPromo.discountAmount) }})
              </span>
            </div>
            <p class="text-[10px] text-slate-400 truncate max-w-[200px]">
              {{ validatedPromo.name }}
            </p>
          </div>

          <button
            type="button"
            class="p-1 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-slate-800 transition-colors"
            title="Bỏ mã giảm giá"
            @click="removePromotion"
          >
            ✕
          </button>
        </div>

        <!-- Input Box If Not Applied -->
        <div v-else class="space-y-1.5">
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

      <!-- Estimated Price Preview -->
      <div class="pt-3 border-t border-slate-700/80 space-y-1.5 text-xs">
        <div class="flex items-center justify-between text-slate-400">
          <span>{{ t('booking.subtotal') }}</span>
          <span class="font-medium text-slate-300">
            {{ formatCurrency(estimatedGross) }}
          </span>
        </div>

        <div v-if="validatedPromo" class="flex items-center justify-between text-emerald-400">
          <span>{{ t('booking.discount') }}</span>
          <span class="font-bold">
            -{{ formatCurrency(validatedPromo.discountAmount) }}
          </span>
        </div>

        <div class="flex items-center justify-between pt-1 text-sm font-bold text-white">
          <span>{{ t('booking.estimatedTotal') }}</span>
          <span class="text-base font-black text-emerald-400">
            {{ formatCurrency(estimatedFinal) }}
          </span>
        </div>

        <p class="text-[10px] text-slate-500 italic leading-normal pt-1">
          * {{ t('booking.authoritativeNotice') }}
        </p>
      </div>

      <!-- Submit CTA Button -->
      <div class="pt-2">
        <Button
          variant="primary"
          size="lg"
          block
          :disabled="selectedSeats.length === 0 || isSubmitting"
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
      </div>
    </div>
  </div>
</template>
