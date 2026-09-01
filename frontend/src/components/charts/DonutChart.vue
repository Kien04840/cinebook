<script setup lang="ts">
import { computed } from 'vue'

export interface DonutSegment {
  label: string
  value: number
  color: string
}

const props = withDefaults(
  defineProps<{
    segments: DonutSegment[]
    centerLabel?: string
    centerValue?: string
    size?: number
    strokeWidth?: number
    emptyText?: string
  }>(),
  {
    size: 200,
    strokeWidth: 24,
    centerLabel: 'Ty le lap day',
    emptyText: 'Chua co du lieu.',
  }
)

const radius = computed(() => (props.size - props.strokeWidth) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)

const totalValue = computed(() => props.segments.reduce((sum, s) => sum + s.value, 0))

const calculatedSegments = computed(() => {
  if (totalValue.value === 0) return []
  let accumulated = 0

  return props.segments.map((seg) => {
    const ratio = seg.value / totalValue.value
    const strokeDasharray = `${ratio * circumference.value} ${circumference.value}`
    const strokeDashoffset = -accumulated * circumference.value
    accumulated += ratio

    return {
      ...seg,
      percentage: Math.round(ratio * 100),
      strokeDasharray,
      strokeDashoffset,
    }
  })
})
</script>

<template>
  <div class="flex flex-col sm:flex-row items-center justify-center gap-6 select-none">
    <div class="relative flex items-center justify-center" :style="{ width: `${size}px`, height: `${size}px` }">
      <svg :width="size" :height="size" class="transform -rotate-90">
        <circle
          :cx="size / 2"
          :cy="size / 2"
          :r="radius"
          stroke="#1e293b"
          :stroke-width="strokeWidth"
          fill="none"
        />

        <circle
          v-for="(seg, idx) in calculatedSegments"
          :key="idx"
          :cx="size / 2"
          :cy="size / 2"
          :r="radius"
          :stroke="seg.color"
          :stroke-width="strokeWidth"
          fill="none"
          :stroke-dasharray="seg.strokeDasharray"
          :stroke-dashoffset="seg.strokeDashoffset"
          class="transition-all duration-300"
        />
      </svg>

      <div class="absolute inset-0 flex flex-col items-center justify-center text-center pointer-events-none">
        <span class="text-xl sm:text-2xl font-black text-white tracking-tight font-mono">
          {{ centerValue || `${calculatedSegments[0]?.percentage || 0}%` }}
        </span>
        <span class="text-[11px] text-slate-400 font-medium">
          {{ centerLabel }}
        </span>
      </div>
    </div>

    <div class="flex flex-col gap-2.5 text-xs">
      <div
        v-for="(seg, idx) in calculatedSegments"
        :key="idx"
        class="flex items-center justify-between gap-4 bg-slate-900/60 px-3 py-1.5 rounded-lg border border-slate-800"
      >
        <div class="flex items-center gap-2">
          <span class="w-2.5 h-2.5 rounded-full" :style="{ backgroundColor: seg.color }"></span>
          <span class="text-slate-300 font-medium">{{ seg.label }}</span>
        </div>
        <span class="font-mono font-bold text-white">
          {{ seg.value.toLocaleString() }} ({{ seg.percentage }}%)
        </span>
      </div>
    </div>
  </div>
</template>
