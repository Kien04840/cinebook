<script setup lang="ts">
import { ref, computed } from 'vue'

const props = withDefaults(
  defineProps<{
    labels: string[]
    values: number[]
    color?: string
    height?: number
    valueFormat?: (v: number) => string
    emptyText?: string
  }>(),
  {
    color: '#6366f1',
    height: 260,
    valueFormat: (v: number) => v.toLocaleString(),
    emptyText: 'Chua co du lieu thong ke.',
  }
)

const hoveredIndex = ref<number | null>(null)
const mousePos = ref<{ x: number; y: number }>({ x: 0, y: 0 })

const padding = { top: 20, right: 25, bottom: 40, left: 65 }
const width = 600

const innerWidth = computed(() => width - padding.left - padding.right)
const innerHeight = computed(() => props.height - padding.top - padding.bottom)

const hasData = computed(() => props.values.length > 0 && props.values.some((v) => v > 0))

const maxValue = computed(() => {
  const max = Math.max(...props.values, 0)
  return max === 0 ? 100 : max * 1.15
})

const ticks = computed(() => {
  const count = 4
  const step = maxValue.value / count
  return Array.from({ length: count + 1 }, (_, i) => i * step)
})

const barWidth = computed(() => {
  if (props.labels.length === 0) return 30
  const available = innerWidth.value / props.labels.length
  return Math.min(Math.max(available * 0.55, 12), 48)
})

function getBarX(index: number): number {
  const slotWidth = innerWidth.value / props.labels.length
  return padding.left + index * slotWidth + (slotWidth - barWidth.value) / 2
}

function getBarHeight(val: number): number {
  const normalized = Math.min(Math.max(val / maxValue.value, 0), 1)
  return normalized * innerHeight.value
}

function getY(val: number): number {
  const normalized = Math.min(Math.max(val / maxValue.value, 0), 1)
  return padding.top + innerHeight.value - normalized * innerHeight.value
}

function handleMouseMove(event: MouseEvent, index: number) {
  const svg = (event.currentTarget as HTMLElement).closest('svg')
  if (!svg) return
  const rect = svg.getBoundingClientRect()
  mousePos.value = { x: event.clientX - rect.left, y: event.clientY - rect.top }
  hoveredIndex.value = index
}
</script>

<template>
  <div class="relative w-full overflow-hidden">
    <div v-if="!hasData" class="flex flex-col items-center justify-center py-12 text-slate-500 text-sm">
      <span class="text-2xl mb-2">📊</span>
      <span>{{ emptyText }}</span>
    </div>

    <div v-else class="relative select-none">
      <svg
        :viewBox="`0 0 ${width} ${height}`"
        class="w-full h-auto overflow-visible"
        @mouseleave="hoveredIndex = null"
      >
        <defs>
          <linearGradient id="bar-gradient" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" :stop-color="color" stop-opacity="1" />
            <stop offset="100%" :stop-color="color" stop-opacity="0.6" />
          </linearGradient>
        </defs>

        <g v-for="tick in ticks" :key="tick">
          <line
            :x1="padding.left"
            :y1="getY(tick)"
            :x2="width - padding.right"
            :y2="getY(tick)"
            stroke="#334155"
            stroke-dasharray="3 3"
            stroke-width="1"
          />
          <text
            :x="padding.left - 8"
            :y="getY(tick) + 4"
            text-anchor="end"
            class="text-[10px] fill-slate-400 font-mono font-medium"
          >
            {{ valueFormat(tick) }}
          </text>
        </g>

        <g v-for="(val, idx) in values" :key="idx">
          <rect
            :x="getBarX(idx)"
            :y="getY(val)"
            :width="barWidth"
            :height="getBarHeight(val)"
            rx="4"
            ry="4"
            fill="url(#bar-gradient)"
            :class="[
              'cursor-pointer transition-opacity duration-150',
              hoveredIndex !== null && hoveredIndex !== idx ? 'opacity-40' : 'opacity-100'
            ]"
            @mousemove="(e) => handleMouseMove(e, idx)"
          />

          <text
            :x="getBarX(idx) + barWidth / 2"
            :y="height - 12"
            text-anchor="middle"
            class="text-[10px] fill-slate-300 font-medium truncate"
          >
            {{ labels[idx] }}
          </text>
        </g>
      </svg>

      <div
        v-if="hoveredIndex !== null && labels[hoveredIndex]"
        class="absolute z-20 pointer-events-none bg-slate-900/95 backdrop-blur-md border border-slate-700 text-xs rounded-xl px-3 py-2 shadow-2xl"
        :style="{
          left: `${Math.min(Math.max(mousePos.x - 50, 10), innerWidth)}px`,
          top: `10px`,
        }"
      >
        <div class="font-bold text-white mb-0.5">{{ labels[hoveredIndex] }}</div>
        <div class="font-mono font-semibold text-indigo-300">
          {{ valueFormat(values[hoveredIndex] ?? 0) }}
        </div>
      </div>
    </div>
  </div>
</template>
