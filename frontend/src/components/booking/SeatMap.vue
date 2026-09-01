<script setup lang="ts">
import { computed } from 'vue'
import type { ShowtimeSeatStatusResponse } from '@/types/showtime.types'
import { useI18n } from '@/composables/useI18n'

interface Props {
  seats: ShowtimeSeatStatusResponse[]
  selectedSeatIds: string[]
  maxSeats?: number
  disabled?: boolean
}

interface Emits {
  (e: 'toggleSeat', seat: ShowtimeSeatStatusResponse): void
}

const props = withDefaults(defineProps<Props>(), {
  maxSeats: 8,
  disabled: false,
})

const emit = defineEmits<Emits>()
const { t } = useI18n()

interface SeatRowGroup {
  rowLabel: string
  seats: ShowtimeSeatStatusResponse[]
}

// Group seats by rowLabel and sort rows and seats inside
const seatRows = computed<SeatRowGroup[]>(() => {
  const rowMap = new Map<string, ShowtimeSeatStatusResponse[]>()

  props.seats.forEach((seat) => {
    const row = seat.rowLabel || '?'
    if (!rowMap.has(row)) {
      rowMap.set(row, [])
    }
    rowMap.get(row)!.push(seat)
  })

  // Sort rows alphabetically (A, B, C, ...)
  const sortedRowLabels = Array.from(rowMap.keys()).sort((a, b) => a.localeCompare(b))

  return sortedRowLabels.map((rowLabel) => {
    const rowSeats = rowMap.get(rowLabel)!
    // Sort seats by seatNumber ascending
    rowSeats.sort((a, b) => (a.seatNumber || 0) - (b.seatNumber || 0))
    return {
      rowLabel,
      seats: rowSeats,
    }
  })
})

function isSeatSelected(seatId: string): boolean {
  return props.selectedSeatIds.includes(seatId)
}

function isSeatInteractive(seat: ShowtimeSeatStatusResponse): boolean {
  if (props.disabled) return false
  return seat.availabilityStatus === 'AVAILABLE' || isSeatSelected(seat.id) || !!seat.isHeldByCurrentUser
}

function handleSeatClick(seat: ShowtimeSeatStatusResponse) {
  if (!isSeatInteractive(seat)) return
  emit('toggleSeat', seat)
}

function getSeatTypeClass(seat: ShowtimeSeatStatusResponse, isSelected: boolean): string {
  if (isSelected) {
    return 'bg-emerald-600 border-emerald-400 text-white font-black shadow-lg shadow-emerald-600/40 ring-2 ring-emerald-400/80 scale-105 z-10'
  }

  // Not selected - style by availability & seat type
  switch (seat.availabilityStatus) {
    case 'HELD':
      if (seat.isHeldByCurrentUser) {
        return 'bg-emerald-950/70 border-emerald-500/80 text-emerald-300 shadow-sm ring-1 ring-emerald-500/50 cursor-pointer'
      }
      return 'bg-amber-900/40 border-amber-700/50 text-amber-500/60 opacity-60 cursor-not-allowed'
    case 'SOLD':
      return 'bg-slate-800 border-slate-750 text-slate-600 opacity-40 cursor-not-allowed'
    case 'BLOCKED':
      return 'bg-slate-900 border-slate-900 text-slate-700 opacity-30 cursor-not-allowed'
    case 'AVAILABLE':
    default: {
      const type = (seat.seatTypeName || '').toUpperCase()
      if (type.includes('VIP')) {
        return 'bg-amber-950/60 border-amber-500/70 text-amber-200 hover:border-amber-400 hover:bg-amber-900/80 shadow-sm shadow-amber-500/10 cursor-pointer'
      }
      if (type.includes('COUPLE') || type.includes('SWEET')) {
        return 'bg-rose-950/60 border-rose-500/70 text-rose-200 hover:border-rose-400 hover:bg-rose-900/80 shadow-sm shadow-rose-500/10 cursor-pointer'
      }
      // Standard Seat
      return 'bg-slate-750 border-slate-600/80 text-slate-200 hover:border-indigo-400 hover:bg-slate-700 hover:text-white shadow-sm cursor-pointer'
    }
  }
}

function getAriaLabel(seat: ShowtimeSeatStatusResponse, isSelected: boolean): string {
  const statusStr = isSelected
    ? t('booking.selectedSeat')
    : seat.availabilityStatus === 'AVAILABLE'
      ? t('booking.standardSeat')
      : seat.availabilityStatus === 'HELD'
        ? t('booking.heldSeat')
        : seat.availabilityStatus === 'SOLD'
          ? t('booking.soldSeat')
          : t('booking.blockedSeat')

  return t('booking.seatAriaLabel', {
    code: seat.seatCode || `${seat.rowLabel}${seat.seatNumber}`,
    type: seat.seatTypeName || 'Standard',
    status: statusStr,
  })
}
</script>

<template>
  <div class="relative w-full rounded-2xl bg-slate-900/90 border border-slate-800 p-4 sm:p-8 overflow-hidden select-none shadow-xl">
    <!-- Cinema Screen Curved Visual -->
    <div class="max-w-xl mx-auto mb-10 sm:mb-14 text-center">
      <div class="relative h-10 flex items-center justify-center">
        <!-- Ambient Curved Glow -->
        <div class="absolute inset-x-4 top-0 h-4 border-t-4 border-indigo-500/80 rounded-t-[100px] sm:rounded-t-[140px] shadow-[0_-8px_24px_rgba(99,102,241,0.25)]"></div>
      </div>
      <p class="text-[11px] sm:text-xs font-black uppercase tracking-widest text-indigo-400">
        {{ t('booking.screen') }}
      </p>
      <p class="text-[10px] text-slate-500 mt-0.5">{{ t('booking.screenSubtitle') }}</p>
    </div>

    <!-- Seat Grid Container (Horizontally scrollable on small screens) -->
    <div class="overflow-x-auto pb-4 scrollbar-thin scrollbar-thumb-slate-700 scrollbar-track-transparent">
      <div class="inline-block min-w-full flex flex-col items-center gap-2.5 sm:gap-3 px-2">
        <!-- Rows loop -->
        <div
          v-for="row in seatRows"
          :key="row.rowLabel"
          class="flex items-center gap-2 sm:gap-3"
        >
          <!-- Left Row Label -->
          <span class="w-5 text-center text-xs font-black text-slate-400 select-none">
            {{ row.rowLabel }}
          </span>

          <!-- Seats inside this row -->
          <div class="flex items-center gap-1.5 sm:gap-2">
            <button
              v-for="seat in row.seats"
              :key="seat.id"
              type="button"
              :disabled="!isSeatInteractive(seat)"
              :aria-label="getAriaLabel(seat, isSeatSelected(seat.id))"
              :aria-pressed="isSeatSelected(seat.id)"
              :class="[
                'min-w-[32px] h-8 sm:min-w-[36px] sm:h-9 px-1 rounded-lg border text-xs font-semibold flex items-center justify-center transition-all duration-150 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1 focus:ring-offset-slate-900',
                getSeatTypeClass(seat, isSeatSelected(seat.id)),
                (seat.seatTypeName || '').toUpperCase().includes('COUPLE') ? 'w-16 sm:w-20' : ''
              ]"
              @click="handleSeatClick(seat)"
            >
              <span v-if="isSeatSelected(seat.id)" class="text-[11px]">✓</span>
              <span v-else-if="seat.availabilityStatus === 'HELD'" class="text-[10px]">⏳</span>
              <span v-else-if="seat.availabilityStatus === 'SOLD'" class="text-[10px]">✕</span>
              <span v-else>{{ seat.seatNumber }}</span>
            </button>
          </div>

          <!-- Right Row Label -->
          <span class="w-5 text-center text-xs font-black text-slate-400 select-none">
            {{ row.rowLabel }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

