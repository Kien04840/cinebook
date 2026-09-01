<script setup lang="ts">
import { formatCurrency, formatDateTime } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Badge from '@/components/common/Badge.vue'
import type { TicketResponse } from '@/types/booking.types'

defineProps<{
  ticket: TicketResponse
  movieTitle?: string
  cinemaName?: string
  auditoriumName?: string
  startTime?: string
  bookingCode?: string
}>()

const { t } = useI18n()
</script>

<template>
  <div class="w-full max-w-sm mx-auto bg-slate-850 border border-slate-700 rounded-2xl overflow-hidden shadow-2xl">
    <!-- Top Notch Header -->
    <div class="bg-indigo-600 px-5 py-4 text-white">
      <div class="flex items-center justify-between">
        <span class="text-xs font-bold uppercase tracking-wider">CineBook E-Ticket</span>
        <Badge variant="neutral" size="sm">
          {{ ticket.ticketStatus === 'VALID' ? t('status.VALID') : ticket.ticketStatus }}
        </Badge>
      </div>
      <h3 class="text-base font-bold mt-1 line-clamp-1">{{ movieTitle || 'Movie' }}</h3>
    </div>

    <!-- Ticket Body -->
    <div class="p-5 space-y-4 text-xs text-slate-300">
      <div class="grid grid-cols-2 gap-3">
        <div>
          <span class="text-slate-400">{{ t('booking.cinemaInfo') }}</span>
          <p class="font-bold text-white mt-0.5">{{ cinemaName }}</p>
          <p class="text-[11px] text-slate-400">{{ auditoriumName }}</p>
        </div>
        <div>
          <span class="text-slate-400">{{ t('booking.showtimeInfo') }}</span>
          <p class="font-bold text-white mt-0.5 font-mono">{{ formatDateTime(startTime) }}</p>
        </div>
      </div>

      <div class="flex items-center justify-between border-t border-slate-800 pt-3">
        <div>
          <span class="text-slate-400">{{ t('booking.stepSelectSeats') }}</span>
          <p class="text-lg font-black text-indigo-400 font-mono">{{ ticket.seatCode }}</p>
        </div>
        <div class="text-right">
          <span class="text-slate-400">{{ t('booking.totalPrice') }}</span>
          <p class="text-base font-black text-emerald-400 font-mono">{{ formatCurrency(ticket.ticketPrice) }}</p>
        </div>
      </div>

      <!-- QR Code Simulation -->
      <div class="border-t border-dashed border-slate-700 pt-4 flex flex-col items-center justify-center gap-2">
        <div class="p-2.5 bg-white rounded-xl shadow-inner">
          <svg class="w-32 h-32 text-slate-900" viewBox="0 0 24 24" fill="currentColor">
            <path d="M2 2h8v8H2V2zm2 2v4h4V4H4zm10-2h8v8h-8V2zm2 2v4h4V4h-4zM2 14h8v8H2v-8zm2 2v4h4v-4H4zm14 0h2v2h-2v-2zm-4 0h2v4h-2v-4zm2 2h2v4h-2v-4zm2 2h2v2h-2v-2zm-6-4h2v2h-2v-2z" />
          </svg>
        </div>
        <span class="font-mono text-[11px] text-slate-400 tracking-wider">{{ ticket.qrCode }}</span>
      </div>
    </div>
  </div>
</template>
