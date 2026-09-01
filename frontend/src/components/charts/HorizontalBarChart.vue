<script setup lang="ts">
import { computed } from 'vue'

export interface BarItem {
  label: string
  value: number
  subValue?: string
  color?: string
}

interface Props {
  items: BarItem[]
  valueFormat?: (val: number) => string
  emptyText?: string
}

const props = withDefaults(defineProps<Props>(), {
  valueFormat: (val: number) => val.toLocaleString(),
  emptyText: 'Chưa có dữ liệu thống kê',
})

const maxValue = computed(() => {
  if (!props.items || props.items.length === 0) return 1
  return Math.max(...props.items.map((i) => i.value), 1)
})
</script>

<template>
  <div class="w-full">
    <!-- Empty State -->
    <div
      v-if="!items || items.length === 0"
      class="h-48 flex items-center justify-center text-slate-500 text-xs text-center border border-dashed border-slate-800 rounded-xl"
    >
      {{ emptyText }}
    </div>

    <!-- Items List -->
    <div v-else class="space-y-3.5">
      <div
        v-for="(item, idx) in items"
        :key="idx"
        class="space-y-1 group"
      >
        <div class="flex items-center justify-between text-xs">
          <span class="font-medium text-slate-200 group-hover:text-white truncate max-w-[200px] sm:max-w-xs transition-colors">
            {{ item.label }}
          </span>
          <div class="flex items-center gap-2 shrink-0 font-mono text-[11px]">
            <span v-if="item.subValue" class="text-slate-400 font-sans text-[10px]">
              {{ item.subValue }}
            </span>
            <span class="font-bold text-indigo-400">
              {{ valueFormat(item.value) }}
            </span>
          </div>
        </div>

        <!-- Progress Bar Track -->
        <div class="w-full h-2.5 rounded-full bg-slate-800 overflow-hidden border border-slate-700/60 p-0.5">
          <div
            class="h-full rounded-full transition-all duration-500 ease-out bg-gradient-to-r from-indigo-600 to-indigo-400 group-hover:from-indigo-500 group-hover:to-indigo-300"
            :style="{
              width: `${Math.max((item.value / maxValue) * 100, 2)}%`,
              backgroundColor: item.color || undefined,
            }"
          ></div>
        </div>
      </div>
    </div>
  </div>
</template>
