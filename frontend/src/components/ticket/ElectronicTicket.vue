<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import QRCode from 'qrcode'
import { formatCurrency, formatDateTime } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Badge from '@/components/common/Badge.vue'
import type { BookingDetailResponse, TicketResponse } from '@/types/booking.types'

interface Props {
  booking?: BookingDetailResponse | null
  ticket?: TicketResponse
  movieTitle?: string
  cinemaName?: string
  auditoriumName?: string
  startTime?: string
  bookingCode?: string
}

const props = defineProps<Props>()
const { t } = useI18n()

const qrDataUrl = ref<string>('')

// Effective values: prefer booking if available
const effectiveMovieTitle = computed(() => {
  return props.booking?.showtime?.movie?.title || props.movieTitle || 'Movie'
})

const effectiveCinemaName = computed(() => {
  return props.booking?.showtime?.cinema?.name || props.cinemaName || ''
})

const effectiveAuditoriumName = computed(() => {
  return props.booking?.showtime?.auditorium?.name || props.auditoriumName || ''
})

const effectiveStartTime = computed(() => {
  return props.booking?.showtime?.startTime || props.startTime || ''
})

const effectiveBookingCode = computed(() => {
  return props.booking?.bookingCode || props.bookingCode || ''
})

const effectiveTotalAmount = computed(() => {
  return props.booking?.totalAmount ?? props.ticket?.ticketPrice ?? 0
})

// QR code content: ALWAYS use checkInCode for bookings
const qrValue = computed(() => {
  if (props.booking?.checkInCode) {
    return props.booking.checkInCode
  }
  if (props.ticket?.qrCode) {
    return props.ticket.qrCode
  }
  return ''
})

// Seats list
const seats = computed(() => {
  if (props.booking?.seats && props.booking.seats.length > 0) {
    return props.booking.seats
  }
  if (props.ticket?.seatCode) {
    return [{ seatCode: props.ticket.seatCode, seatTypeName: '' }]
  }
  return []
})

// Ticket status
const isPaidOrValid = computed(() => {
  if (props.booking) {
    return props.booking.bookingStatus === 'PAID'
  }
  return props.ticket?.ticketStatus === 'VALID'
})

watch(
  qrValue,
  async (newVal) => {
    if (!newVal) {
      qrDataUrl.value = ''
      return
    }
    try {
      qrDataUrl.value = await QRCode.toDataURL(newVal, {
        width: 220,
        margin: 1,
        color: {
          dark: '#0f172a',
          light: '#ffffff',
        },
      })
    } catch (err) {
      console.error('Failed to generate QR Code', err)
      qrDataUrl.value = ''
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="w-full max-w-md mx-auto bg-slate-900 border border-slate-700/80 rounded-3xl overflow-hidden shadow-2xl transition-all">
    <!-- Top Notch Header -->
    <div class="bg-gradient-to-r from-indigo-600 to-indigo-700 px-6 py-4 text-white">
      <div class="flex items-center justify-between">
        <span class="text-xs font-bold uppercase tracking-wider text-indigo-100">CineBook E-Ticket Pass</span>
        <Badge :variant="isPaidOrValid ? 'success' : 'neutral'" size="sm">
          {{ isPaidOrValid ? t('status.VALID') : (booking?.bookingStatus || ticket?.ticketStatus) }}
        </Badge>
      </div>
      <h3 class="text-lg font-bold mt-1 text-white line-clamp-1">{{ effectiveMovieTitle }}</h3>
      <p class="text-xs text-indigo-200 mt-0.5 font-mono">
        {{ t('booking.bookingCode') }}: <span class="font-bold text-white">{{ effectiveBookingCode }}</span>
      </p>
    </div>

    <!-- Ticket Body -->
    <div class="p-6 space-y-5 text-xs text-slate-300">
      <!-- Cinema & Showtime Info -->
      <div class="grid grid-cols-2 gap-4">
        <div>
          <span class="text-slate-400 font-medium">{{ t('booking.cinemaInfo') }}</span>
          <p class="font-bold text-white text-sm mt-0.5">{{ effectiveCinemaName }}</p>
          <p class="text-[11px] text-slate-400">{{ effectiveAuditoriumName }}</p>
        </div>
        <div>
          <span class="text-slate-400 font-medium">{{ t('booking.showtimeInfo') }}</span>
          <p class="font-bold text-white text-sm mt-0.5 font-mono">{{ formatDateTime(effectiveStartTime) }}</p>
        </div>
      </div>

      <!-- Seats List Section -->
      <div class="border-t border-slate-800 pt-4 space-y-2">
        <div class="flex items-center justify-between">
          <span class="text-slate-400 font-medium">
            {{ t('booking.stepSelectSeats') }} ({{ seats.length }} ghế)
          </span>
          <span class="text-emerald-400 font-mono font-bold text-sm">
            {{ formatCurrency(effectiveTotalAmount) }}
          </span>
        </div>
        <!-- Seats Badges -->
        <div class="flex flex-wrap gap-2 pt-1">
          <div
            v-for="seat in seats"
            :key="seat.seatCode"
            class="px-3 py-1.5 rounded-xl bg-slate-800 border border-slate-700 text-slate-200 flex items-center gap-1.5"
          >
            <span class="font-bold font-mono text-indigo-400 text-sm">{{ seat.seatCode }}</span>
            <span v-if="seat.seatTypeName" class="text-[10px] text-slate-400">({{ seat.seatTypeName }})</span>
          </div>
        </div>
      </div>

      <!-- Single QR Code for the whole booking -->
      <div class="border-t border-dashed border-slate-700/80 pt-5 flex flex-col items-center justify-center gap-3">
        <div class="p-3 bg-white rounded-2xl shadow-lg flex items-center justify-center">
          <img
            v-if="qrDataUrl"
            :src="qrDataUrl"
            alt="Check-in QR Code"
            class="w-40 h-40 object-contain"
          />
          <div v-else class="w-40 h-40 flex items-center justify-center text-slate-400">
            <svg class="w-8 h-8 animate-spin" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
          </div>
        </div>

        <div class="text-center space-y-1">
          <p class="text-[11px] text-slate-400 font-medium">
            Quét mã QR này để soát toàn bộ vé trong đơn hàng
          </p>
          <p class="font-mono text-[10px] text-slate-500 tracking-wider">
            ID: {{ qrValue }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
