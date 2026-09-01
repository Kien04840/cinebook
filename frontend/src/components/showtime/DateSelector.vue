<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from '@/composables/useI18n'

interface Props {
  modelValue: string // YYYY-MM-DD
  daysCount?: number
}

interface Emits {
  (e: 'update:modelValue', value: string): void
}

const props = withDefaults(defineProps<Props>(), {
  daysCount: 14,
})

const emit = defineEmits<Emits>()
const { t, locale } = useI18n()

interface DayItem {
  iso: string
  dayOfWeek: string
  dayNumber: string
  monthNumber: string
  isToday: boolean
}

const days = computed<DayItem[]>(() => {
  const list: DayItem[] = []
  const today = new Date()

  for (let i = 0; i < props.daysCount; i++) {
    const d = new Date()
    d.setDate(today.getDate() + i)

    const year = d.getFullYear()
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const iso = `${year}-${month}-${day}`

    let dayOfWeek = ''
    if (i === 0) {
      dayOfWeek = t('showtimes.today')
    } else {
      const viDays = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7']
      const enDays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
      dayOfWeek = locale.value === 'en' ? enDays[d.getDay()] : viDays[d.getDay()]
    }

    const monthLabel = locale.value === 'en' ? `M${month}` : `Th${month}`

    list.push({
      iso,
      dayOfWeek,
      dayNumber: day,
      monthNumber: monthLabel,
      isToday: i === 0,
    })
  }

  return list
})

function selectDate(iso: string) {
  emit('update:modelValue', iso)
}
</script>

<template>
  <div class="relative w-full">
    <!-- Date Pills Scroll Container -->
    <div
      class="flex items-center gap-2.5 overflow-x-auto pb-2 scrollbar-thin scrollbar-thumb-slate-700 scrollbar-track-transparent select-none focus:outline-none"
      role="radiogroup"
      :aria-label="t('showtimes.selectDate')"
    >
      <button
        v-for="item in days"
        :key="item.iso"
        type="button"
        role="radio"
        :aria-checked="modelValue === item.iso"
        :class="[
          'flex flex-col items-center justify-center min-w-[72px] sm:min-w-[80px] py-2.5 px-3 rounded-2xl border transition-all duration-200 cursor-pointer focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 focus:ring-offset-slate-900',
          modelValue === item.iso
            ? 'bg-indigo-600 border-indigo-500 text-white shadow-lg shadow-indigo-600/30 scale-[1.02]'
            : 'bg-slate-800/90 border-slate-700/80 text-slate-300 hover:bg-slate-750 hover:border-slate-600 hover:text-white',
        ]"
        @click="selectDate(item.iso)"
      >
        <span
          :class="[
            'text-[11px] font-semibold tracking-wider uppercase',
            modelValue === item.iso ? 'text-indigo-200' : 'text-slate-400',
          ]"
        >
          {{ item.dayOfWeek }}
        </span>

        <span class="text-lg sm:text-xl font-black tracking-tight my-0.5">
          {{ item.dayNumber }}
        </span>

        <span
          :class="[
            'text-[10px] font-medium',
            modelValue === item.iso ? 'text-indigo-200' : 'text-slate-500',
          ]"
        >
          {{ item.monthNumber }}
        </span>
      </button>
    </div>
  </div>
</template>
