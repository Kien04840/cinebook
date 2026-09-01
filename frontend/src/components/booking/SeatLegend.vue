<script setup lang="ts">
import { useI18n } from '@/composables/useI18n'

const { t } = useI18n()
</script>

<template>
  <div class="p-3.5 sm:p-4 rounded-2xl bg-slate-800/80 border border-slate-700/80 shadow-sm select-none">
    <div class="flex flex-wrap items-center justify-center gap-4 sm:gap-6 text-xs text-slate-300 font-medium">
      <!-- Standard Seat -->
      <div class="flex items-center gap-2">
        <div class="w-6 h-6 rounded-lg bg-slate-750 border border-slate-600 shadow-sm flex items-center justify-center text-[10px] text-slate-400 font-bold">
          A1
        </div>
        <span>{{ t('booking.standardSeat') }}</span>
      </div>

      <!-- VIP Seat -->
      <div class="flex items-center gap-2">
        <div class="w-6 h-6 rounded-lg bg-amber-950/70 border border-amber-500/80 shadow-sm shadow-amber-500/10 flex items-center justify-center text-[10px] text-amber-300 font-bold">
          VIP
        </div>
        <span>{{ t('booking.vipSeat') }}</span>
      </div>

      <!-- Couple Seat -->
      <div class="flex items-center gap-2">
        <div class="w-10 h-6 rounded-lg bg-rose-950/70 border border-rose-500/80 shadow-sm shadow-rose-500/10 flex items-center justify-center text-[10px] text-rose-300 font-bold">
          CPL
        </div>
        <span>{{ t('booking.coupleSeat') }}</span>
      </div>

      <!-- Selected Seat -->
      <div class="flex items-center gap-2">
        <div class="w-6 h-6 rounded-lg bg-emerald-600 border border-emerald-400 text-white shadow-md shadow-emerald-600/30 flex items-center justify-center text-[10px] font-black ring-2 ring-emerald-500/50">
          ✓
        </div>
        <span class="text-emerald-400 font-semibold">{{ t('booking.selectedSeat') }}</span>
      </div>

      <!-- Held Seat -->
      <div class="flex items-center gap-2">
        <div class="w-6 h-6 rounded-lg bg-amber-900/40 border border-amber-700/60 text-amber-500/70 flex items-center justify-center text-[10px] font-bold cursor-not-allowed">
          ⏳
        </div>
        <span class="text-slate-400">{{ t('booking.heldSeat') }}</span>
      </div>

      <!-- Sold Seat -->
      <div class="flex items-center gap-2">
        <div class="w-6 h-6 rounded-lg bg-slate-800 border border-slate-750 text-slate-600 flex items-center justify-center text-[10px] font-bold cursor-not-allowed">
          ✕
        </div>
        <span class="text-slate-500">{{ t('booking.soldSeat') }}</span>
      </div>
    </div>
  </div>
</template>

