<script setup lang="ts">
interface Props {
  hoverable?: boolean
  padding?: 'none' | 'sm' | 'md' | 'lg'
}

withDefaults(defineProps<Props>(), {
  hoverable: false,
  padding: 'md',
})

const paddingClasses = {
  none: 'p-0',
  sm: 'p-4',
  md: 'p-6',
  lg: 'p-8',
}
</script>

<template>
  <div
    :class="[
      'bg-slate-800 border border-slate-700/80 rounded-xl transition-all duration-200 overflow-hidden shadow-sm',
      hoverable ? 'hover:border-slate-600 hover:shadow-md hover:shadow-black/20' : '',
    ]"
  >
    <div v-if="$slots.header" class="px-6 py-4 border-b border-slate-700/80 flex items-center justify-between">
      <slot name="header" />
    </div>

    <div :class="paddingClasses[padding]">
      <slot />
    </div>

    <div v-if="$slots.footer" class="px-6 py-3.5 border-t border-slate-700/80 bg-slate-850/50 flex items-center justify-between">
      <slot name="footer" />
    </div>
  </div>
</template>

