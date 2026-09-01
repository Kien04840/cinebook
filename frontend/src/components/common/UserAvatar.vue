<script setup lang="ts">
import { ref, computed, watch } from 'vue'

interface Props {
  src?: string | null
  name?: string | null
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl'
  bordered?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  src: null,
  name: '',
  size: 'md',
  bordered: false,
})

const hasError = ref(false)

watch(() => props.src, () => {
  hasError.value = false
})

const initials = computed(() => {
  if (!props.name || !props.name.trim()) return 'U'
  const parts = props.name.trim().split(/\s+/)
  if (parts.length === 1) {
    return parts[0].substring(0, 2).toUpperCase()
  }
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
})

const sizeClasses = {
  xs: 'w-6 h-6 text-[10px]',
  sm: 'w-8 h-8 text-xs',
  md: 'w-10 h-10 text-sm font-semibold',
  lg: 'w-14 h-14 text-base font-bold',
  xl: 'w-20 h-20 text-xl font-black',
}

const bgColors = [
  'bg-indigo-600',
  'bg-emerald-600',
  'bg-sky-600',
  'bg-amber-600',
  'bg-rose-600',
  'bg-purple-600',
  'bg-teal-600',
]

const colorClass = computed(() => {
  if (!props.name) return 'bg-indigo-600'
  let hash = 0
  for (let i = 0; i < props.name.length; i++) {
    hash = props.name.charCodeAt(i) + ((hash << 5) - hash)
  }
  const idx = Math.abs(hash) % bgColors.length
  return bgColors[idx]
})
</script>

<template>
  <div
    :class="[
      'rounded-full flex items-center justify-center select-none overflow-hidden shrink-0 transition-transform',
      sizeClasses[size],
      bordered ? 'ring-2 ring-indigo-500/50 ring-offset-2 ring-offset-slate-900 shadow-md' : 'border border-slate-700/80',
      src && !hasError ? 'bg-slate-800' : colorClass,
    ]"
  >
    <img
      v-if="src && !hasError"
      :src="src"
      :alt="name || 'User Avatar'"
      class="w-full h-full object-cover"
      @error="hasError = true"
    />
    <span v-else class="text-white uppercase tracking-wider font-mono">
      {{ initials }}
    </span>
  </div>
</template>
