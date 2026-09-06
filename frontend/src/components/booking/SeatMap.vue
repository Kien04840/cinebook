<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import type { ShowtimeSeatStatusResponse } from '@/types/showtime.types'
import { useI18n } from '@/composables/useI18n'
import { buildSeatGrid } from '@/utils/seatGrid'
import { getSeatColorConfig } from '@/utils/seatTypePresentation'

interface Props {
  seats: ShowtimeSeatStatusResponse[]
  selectedSeatIds: string[]
  columnsCount?: number
  maxSeats?: number
  disabled?: boolean
}

interface Emits {
  (e: 'toggleSeat', seat: ShowtimeSeatStatusResponse): void
}

const props = withDefaults(defineProps<Props>(), {
  columnsCount: undefined,
  maxSeats: 8,
  disabled: false,
})

const emit = defineEmits<Emits>()
const { t } = useI18n()

// Container measurement for adaptive sizing
const mapViewportRef = ref<HTMLElement | null>(null)
const containerWidth = ref<number>(800)
let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  if (mapViewportRef.value) {
    containerWidth.value = mapViewportRef.value.clientWidth
    resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        if (entry.contentRect.width > 0) {
          containerWidth.value = entry.contentRect.width
        }
      }
    })
    resizeObserver.observe(mapViewportRef.value)
  }
})

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})

// Reusable logical grid calculation
const seatGrid = computed(() => {
  return buildSeatGrid(props.seats, props.columnsCount)
})

const isMobile = computed(() => containerWidth.value < 640)

// Adaptive gap sizing
const gapSize = computed(() => {
  const cols = seatGrid.value.columnsCount
  if (isMobile.value) return 6
  if (cols <= 10) return 8
  if (cols <= 14) return 6
  return 4
})

// Adaptive seat sizing: fits 10x16 on desktop while maintaining min 32px on mobile
const seatSize = computed(() => {
  const cols = seatGrid.value.columnsCount
  if (cols === 0) return 36

  if (isMobile.value) {
    return 34 // touch target floor on mobile
  }

  // Desktop fit-to-container
  const rowLabelsWidth = 56 // left + right labels (28px each)
  const paddingX = 32
  const totalGaps = (cols - 1) * gapSize.value
  const availableForSeats = containerWidth.value - rowLabelsWidth - paddingX - totalGaps

  if (availableForSeats <= 0) return 32

  const computedWidth = Math.floor(availableForSeats / cols)
  // Clamp between 28px and 42px on desktop
  return Math.max(28, Math.min(42, computedWidth))
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
    return 'bg-emerald-600 border-emerald-400 text-white font-black shadow-lg shadow-emerald-600/40 ring-2 ring-emerald-400/80 scale-[1.02] z-10'
  }

  // Not selected - style by availability & semantic seat color token
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
      const cfg = getSeatColorConfig(seat.colorToken)
      return `${cfg.seatClass} ${cfg.hoverClass} shadow-sm cursor-pointer`
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
  <div class="relative w-full rounded-2xl bg-slate-900/90 border border-slate-800 p-4 sm:p-8 select-none shadow-xl">
    <!-- Seat Map Viewport: horizontally scrollable on small screens / wide auditoriums -->
    <div
      ref="mapViewportRef"
      class="overflow-x-auto pb-4 scrollbar-thin scrollbar-thumb-slate-700 scrollbar-track-transparent"
    >
      <!-- Seat Map Content: w-max mx-auto centers small maps, starts at left:0 on overflow without clipping -->
      <div class="w-max mx-auto min-w-min px-2 sm:px-4 py-2 flex flex-col items-center">
        <!-- Cinema Screen Curved Visual (Centered relative to logical map width) -->
        <div class="w-full max-w-xl mx-auto mb-8 sm:mb-12 text-center">
          <div class="relative h-10 flex items-center justify-center">
            <!-- Ambient Curved Glow -->
            <div class="absolute inset-x-4 top-0 h-4 border-t-4 border-indigo-500/80 rounded-t-[100px] sm:rounded-t-[140px] shadow-[0_-8px_24px_rgba(99,102,241,0.25)]"></div>
          </div>
          <p class="text-[11px] sm:text-xs font-black uppercase tracking-widest text-indigo-400">
            {{ t('booking.screen') }}
          </p>
          <p class="text-[10px] text-slate-500 mt-0.5">{{ t('booking.screenSubtitle') }}</p>
          <!-- Subtle Mobile Scroll Indicator (only when viewport is narrow) -->
          <p class="sm:hidden text-[11px] text-indigo-300/80 flex items-center justify-center gap-1.5 mt-2 font-medium">
            <span>←</span>
            <span>{{ t('booking.scrollHint') }}</span>
            <span>→</span>
          </p>
        </div>

        <!-- Matrix Area: Column Headers + Rows -->
        <div
          class="flex flex-col items-center"
          :style="{ gap: `${gapSize}px` }"
        >
          <!-- Column Headers Row -->
          <div
            class="flex items-center"
            :style="{ gap: `${gapSize}px` }"
          >
            <!-- Left Header Spacer matching row label width -->
            <span class="w-6 sm:w-7 shrink-0" aria-hidden="true"></span>

            <!-- Column numbers aligned with seat grid tracks -->
            <div
              class="grid"
              :style="{
                gridTemplateColumns: `repeat(${seatGrid.columnsCount}, ${seatSize}px)`,
                gap: `${gapSize}px`,
              }"
            >
              <div
                v-for="col in seatGrid.columnNumbers"
                :key="col"
                class="h-6 flex items-center justify-center text-[10px] sm:text-xs font-semibold text-slate-500 select-none"
              >
                {{ col }}
              </div>
            </div>

            <!-- Right Header Spacer matching row label width -->
            <span class="w-6 sm:w-7 shrink-0" aria-hidden="true"></span>
          </div>

          <!-- Rows Loop -->
          <div
            v-for="row in seatGrid.rows"
            :key="row.rowLabel"
            class="flex items-center"
            :style="{ gap: `${gapSize}px` }"
          >
            <!-- Left Row Label -->
            <span class="w-6 sm:w-7 text-center text-xs font-black text-slate-400 select-none shrink-0">
              {{ row.rowLabel }}
            </span>

            <!-- Seat & Empty Cells in Logical Grid -->
            <div
              class="grid"
              :style="{
                gridTemplateColumns: `repeat(${seatGrid.columnsCount}, ${seatSize}px)`,
                gap: `${gapSize}px`,
              }"
            >
              <template v-for="cell in row.cells" :key="cell.column">
                <!-- Actual Interactive Seat Button -->
                <button
                  v-if="cell.type === 'seat' && cell.seat"
                  :style="{
                    gridColumn: `span ${cell.span}`,
                    height: `${Math.max(32, seatSize)}px`,
                  }"
                  type="button"
                  :disabled="!isSeatInteractive(cell.seat)"
                  :aria-label="getAriaLabel(cell.seat, isSeatSelected(cell.seat.id))"
                  :aria-pressed="isSeatSelected(cell.seat.id)"
                  :title="`${cell.seat.seatCode} (${cell.seat.seatTypeName || 'Standard'})`"
                  :class="[
                    'px-1 rounded-lg border text-xs font-semibold flex items-center justify-center transition-all duration-150 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1 focus:ring-offset-slate-900 touch-manipulation active:scale-95',
                    getSeatTypeClass(cell.seat, isSeatSelected(cell.seat.id)),
                  ]"
                  @click="handleSeatClick(cell.seat)"
                >
                  <span v-if="isSeatSelected(cell.seat.id)" class="text-[11px]">✓</span>
                  <span v-else-if="cell.seat.availabilityStatus === 'HELD'" class="text-[10px]">⏳</span>
                  <span v-else-if="cell.seat.availabilityStatus === 'SOLD'" class="text-[10px]">✕</span>
                  <span v-else-if="cell.seat.availabilityStatus === 'BLOCKED'" class="text-[10px]">⚠</span>
                  <span v-else>{{ cell.seat.seatNumber }}</span>
                </button>

                <!-- Non-interactive Empty Position / Spacer -->
                <div
                  v-else
                  :style="{
                    gridColumn: `span ${cell.span}`,
                    height: `${Math.max(32, seatSize)}px`,
                  }"
                  class="pointer-events-none"
                  aria-hidden="true"
                />
              </template>
            </div>

            <!-- Right Row Label -->
            <span class="w-6 sm:w-7 text-center text-xs font-black text-slate-400 select-none shrink-0">
              {{ row.rowLabel }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
