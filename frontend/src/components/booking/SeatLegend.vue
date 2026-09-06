<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import { getSeatLegendClass } from '@/utils/seatTypePresentation'

interface SeatTypeLegendItem {
  id?: string
  name: string
  capacity?: number
  colorToken?: string
  icon?: string
}

interface Props {
  seatTypes?: SeatTypeLegendItem[]
}

const props = withDefaults(defineProps<Props>(), {
  seatTypes: () => [],
})

const { t } = useI18n()

const displayTypes = computed(() => {
  if (props.seatTypes && props.seatTypes.length > 0) {
    // Unique by name / colorToken
    const seen = new Set<string>()
    const list: SeatTypeLegendItem[] = []
    for (const st of props.seatTypes) {
      const key = st.name || st.colorToken || ''
      if (!seen.has(key)) {
        seen.add(key)
        list.push(st)
      }
    }
    return list
  }

  // Fallback defaults
  return [
    { name: t('booking.standardSeat'), capacity: 1, colorToken: 'slate' },
    { name: t('booking.vipSeat'), capacity: 1, colorToken: 'amber' },
    { name: t('booking.coupleSeat'), capacity: 2, colorToken: 'rose' },
    { name: 'Premium', capacity: 1, colorToken: 'indigo' },
  ]
})
</script>

<template>
  <div class="p-3.5 sm:p-4 rounded-2xl bg-slate-800/80 border border-slate-700/80 shadow-sm select-none">
    <div class="flex flex-wrap items-center justify-center gap-4 sm:gap-6 text-xs text-slate-300 font-medium">
      <!-- Dynamic Seat Types from domain -->
      <div
        v-for="st in displayTypes"
        :key="st.name"
        class="flex items-center gap-2"
      >
        <div
          :class="[
            'h-6 rounded-lg border shadow-sm flex items-center justify-center text-[10px] font-bold px-1.5',
            st.capacity && st.capacity > 1 ? 'w-10' : 'w-6',
            getSeatLegendClass(st.colorToken),
          ]"
        >
          <span v-if="st.capacity && st.capacity > 1">2x</span>
          <span v-else>●</span>
        </div>
        <span>
          {{ st.name }}
          <span v-if="st.capacity && st.capacity > 1" class="text-[10px] text-slate-400 font-normal">
            (2 người)
          </span>
        </span>
      </div>

      <!-- Selected Seat Status -->
      <div class="flex items-center gap-2">
        <div class="w-6 h-6 rounded-lg bg-emerald-600 border border-emerald-400 text-white shadow-md shadow-emerald-600/30 flex items-center justify-center text-[10px] font-black ring-2 ring-emerald-500/50">
          ✓
        </div>
        <span class="text-emerald-400 font-semibold">{{ t('booking.selectedSeat') }}</span>
      </div>

      <!-- Held Seat Status -->
      <div class="flex items-center gap-2">
        <div class="w-6 h-6 rounded-lg bg-amber-900/40 border border-amber-700/60 text-amber-500/70 flex items-center justify-center text-[10px] font-bold cursor-not-allowed">
          ⏳
        </div>
        <span class="text-slate-400">{{ t('booking.heldSeat') }}</span>
      </div>

      <!-- Sold Seat Status -->
      <div class="flex items-center gap-2">
        <div class="w-6 h-6 rounded-lg bg-slate-800 border border-slate-750 text-slate-600 flex items-center justify-center text-[10px] font-bold cursor-not-allowed">
          ✕
        </div>
        <span class="text-slate-500">{{ t('booking.soldSeat') }}</span>
      </div>
    </div>
  </div>
</template>

