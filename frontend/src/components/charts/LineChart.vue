<script setup lang="ts">
import { ref, computed } from 'vue'

export interface ChartSeries {
  name: string
  data: number[]
  color: string
  yAxisIndex?: 0 | 1
}

const props = withDefaults(
  defineProps<{
    labels: string[]
    series: ChartSeries[]
    height?: number
    yAxisFormat?: (v: number) => string
    yAxisRightFormat?: (v: number) => string
    emptyText?: string
  }>(),
  {
    height: 280,
    yAxisFormat: (v: number) => v.toLocaleString(),
    yAxisRightFormat: (v: number) => v.toLocaleString(),
    emptyText: 'Chua co du lieu thong ke.',
  }
)

const hoveredIndex = ref<number | null>(null)
const mousePos = ref<{ x: number; y: number }>({ x: 0, y: 0 })

const padding = { top: 20, right: 55, bottom: 35, left: 65 }
const width = 700

const innerWidth = computed(() => width - padding.left - padding.right)
const innerHeight = computed(() => props.height - padding.top - padding.bottom)

const hasData = computed(() => {
  return props.labels.length > 0 && props.series.some((s) => s.data && s.data.length > 0)
})

const maxLeft = computed(() => {
  const leftSeries = props.series.filter((s) => (s.yAxisIndex ?? 0) === 0)
  if (leftSeries.length === 0) return 100
  const max = Math.max(...leftSeries.flatMap((s) => s.data), 0)
  return max === 0 ? 100 : max * 1.15
})

const maxRight = computed(() => {
  const rightSeries = props.series.filter((s) => s.yAxisIndex === 1)
  if (rightSeries.length === 0) return 100
  const max = Math.max(...rightSeries.flatMap((s) => s.data), 0)
  return max === 0 ? 100 : max * 1.15
})

const hasRightAxis = computed(() => props.series.some((s) => s.yAxisIndex === 1))

const leftTicks = computed(() => {
  const count = 4
  const step = maxLeft.value / count
  return Array.from({ length: count + 1 }, (_, i) => i * step)
})

function getX(index: number): number {
  if (props.labels.length <= 1) return padding.left + innerWidth.value / 2
  return padding.left + (index / (props.labels.length - 1)) * innerWidth.value
}

function getY(val: number, isRight: boolean = false): number {
  const max = isRight ? maxRight.value : maxLeft.value
  const normalized = Math.min(Math.max(val / max, 0), 1)
  return padding.top + innerHeight.value - normalized * innerHeight.value
}

function getPath(s: ChartSeries): string {
  if (!s.data || s.data.length === 0) return ''
  const isRight = s.yAxisIndex === 1
  return s.data
    .map((val, idx) => {
      const x = getX(idx)
      const y = getY(val, isRight)
      return `${idx === 0 ? 'M' : 'L'} ${x} ${y}`
    })
    .join(' ')
}

function getAreaPath(s: ChartSeries): string {
  if (!s.data || s.data.length === 0) return ''
  const isRight = s.yAxisIndex === 1
  const linePath = s.data
    .map((val, idx) => {
      const x = getX(idx)
      const y = getY(val, isRight)
      return `${idx === 0 ? 'M' : 'L'} ${x} ${y}`
    })
    .join(' ')
  const lastX = getX(s.data.length - 1)
  const firstX = getX(0)
  const bottomY = padding.top + innerHeight.value
  return `${linePath} L ${lastX} ${bottomY} L ${firstX} ${bottomY} Z`
}

function handleMouseMove(event: MouseEvent) {
  const svg = (event.currentTarget as HTMLElement).closest('svg')
  if (!svg || props.labels.length === 0) return

  const rect = svg.getBoundingClientRect()
  const mouseX = ((event.clientX - rect.left) / rect.width) * width
  mousePos.value = { x: event.clientX - rect.left, y: event.clientY - rect.top }

  let closestIdx = 0
  let minDiff = Infinity
  props.labels.forEach((_, idx) => {
    const x = getX(idx)
    const diff = Math.abs(mouseX - x)
    if (diff < minDiff) {
      minDiff = diff
      closestIdx = idx
    }
  })
  hoveredIndex.value = closestIdx
}

function handleMouseLeave() {
  hoveredIndex.value = null
}
</script>

<template>
  <div class="relative w-full overflow-hidden">
    <div class="flex flex-wrap items-center justify-end gap-4 mb-3 text-xs">
      <div v-for="s in series" :key="s.name" class="flex items-center gap-2">
        <span class="w-3 h-3 rounded-full shadow-sm" :style="{ backgroundColor: s.color }"></span>
        <span class="text-slate-300 font-medium">{{ s.name }}</span>
      </div>
    </div>

    <div v-if="!hasData" class="flex flex-col items-center justify-center py-12 text-slate-500 text-sm">
      <span class="text-2xl mb-2">📉</span>
      <span>{{ emptyText }}</span>
    </div>

    <div v-else class="relative select-none">
      <svg
        :viewBox="`0 0 ${width} ${height}`"
        class="w-full h-auto overflow-visible cursor-crosshair"
        @mousemove="handleMouseMove"
        @mouseleave="handleMouseLeave"
      >
        <defs>
          <linearGradient
            v-for="(s, idx) in series"
            :id="`gradient-${idx}`"
            :key="idx"
            x1="0"
            y1="0"
            x2="0"
            y2="1"
          >
            <stop offset="0%" :stop-color="s.color" stop-opacity="0.35" />
            <stop offset="100%" :stop-color="s.color" stop-opacity="0.0" />
          </linearGradient>
        </defs>

        <g v-for="tick in leftTicks" :key="tick">
          <line
            :x1="padding.left"
            :y1="getY(tick, false)"
            :x2="width - padding.right"
            :y2="getY(tick, false)"
            stroke="#334155"
            stroke-dasharray="3 3"
            stroke-width="1"
          />
          <text
            :x="padding.left - 8"
            :y="getY(tick, false) + 4"
            text-anchor="end"
            class="text-[10px] fill-slate-400 font-mono font-medium"
          >
            {{ yAxisFormat(tick) }}
          </text>
        </g>

        <g v-if="hasRightAxis">
          <text
            v-for="tick in [0, maxRight * 0.25, maxRight * 0.5, maxRight * 0.75, maxRight]"
            :key="tick"
            :x="width - padding.right + 8"
            :y="getY(tick, true) + 4"
            text-anchor="start"
            class="text-[10px] fill-indigo-300 font-mono font-medium"
          >
            {{ yAxisRightFormat(tick) }}
          </text>
        </g>

        <g>
          <text
            v-for="(label, idx) in labels"
            :key="idx"
            :x="getX(idx)"
            :y="height - 8"
            text-anchor="middle"
            class="text-[10px] fill-slate-400 font-medium"
          >
            {{ label }}
          </text>
        </g>

        <path
          v-for="(s, idx) in series"
          :key="`area-${idx}`"
          :d="getAreaPath(s)"
          :fill="`url(#gradient-${idx})`"
        />

        <path
          v-for="(s, idx) in series"
          :key="`line-${idx}`"
          :d="getPath(s)"
          :stroke="s.color"
          stroke-width="2.5"
          fill="none"
          stroke-linecap="round"
          stroke-linejoin="round"
        />

        <g v-if="hoveredIndex !== null">
          <line
            :x1="getX(hoveredIndex)"
            :y1="padding.top"
            :x2="getX(hoveredIndex)"
            :y2="height - padding.bottom"
            stroke="#94a3b8"
            stroke-width="1.5"
            stroke-dasharray="2 2"
          />

          <circle
            v-for="(s, idx) in series"
            :key="`dot-${idx}`"
            :cx="getX(hoveredIndex)"
            :cy="getY(s.data[hoveredIndex] ?? 0, s.yAxisIndex === 1)"
            r="5"
            :fill="s.color"
            stroke="#0f172a"
            stroke-width="2"
          />
        </g>
      </svg>

      <div
        v-if="hoveredIndex !== null && labels[hoveredIndex]"
        class="absolute z-20 pointer-events-none bg-slate-900/95 backdrop-blur-md border border-slate-700 text-xs rounded-xl px-3.5 py-2.5 shadow-2xl transition-transform"
        :style="{
          left: `${Math.min(Math.max(mousePos.x - 70, 10), innerWidth)}px`,
          top: `10px`,
        }"
      >
        <div class="font-bold text-white mb-1.5 pb-1 border-b border-slate-800">
          {{ labels[hoveredIndex] }}
        </div>
        <div v-for="s in series" :key="s.name" class="flex items-center justify-between gap-4 py-0.5">
          <span class="flex items-center gap-1.5 text-slate-300">
            <span class="w-2 h-2 rounded-full" :style="{ backgroundColor: s.color }"></span>
            <span>{{ s.name }}:</span>
          </span>
          <span class="font-bold font-mono text-white">
            {{ s.yAxisIndex === 1 ? yAxisRightFormat(s.data[hoveredIndex] ?? 0) : yAxisFormat(s.data[hoveredIndex] ?? 0) }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
