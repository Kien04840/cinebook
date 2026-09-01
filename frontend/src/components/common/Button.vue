<script setup lang="ts">
import Spinner from './Spinner.vue'

interface Props {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost' | 'link'
  size?: 'sm' | 'md' | 'lg'
  type?: 'button' | 'submit' | 'reset'
  disabled?: boolean
  loading?: boolean
  block?: boolean
}

withDefaults(defineProps<Props>(), {
  variant: 'primary',
  size: 'md',
  type: 'button',
  disabled: false,
  loading: false,
  block: false,
})

const variantClasses = {
  primary: 'bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 text-white shadow-sm focus:ring-indigo-500 border border-transparent',
  secondary: 'bg-slate-700 hover:bg-slate-600 active:bg-slate-800 text-slate-100 border border-slate-600 focus:ring-slate-400',
  danger: 'bg-rose-600 hover:bg-rose-700 active:bg-rose-800 text-white shadow-sm focus:ring-rose-500 border border-transparent',
  ghost: 'bg-transparent hover:bg-slate-800 text-slate-300 hover:text-white border border-transparent focus:ring-slate-500',
  link: 'bg-transparent text-indigo-400 hover:text-indigo-300 underline-offset-4 hover:underline p-0 border-0 focus:ring-0 focus:outline-none',
}

const sizeClasses = {
  sm: 'px-3 py-1.5 text-xs font-medium rounded-md gap-1.5',
  md: 'px-4 py-2 text-sm font-medium rounded-lg gap-2',
  lg: 'px-6 py-3 text-base font-semibold rounded-xl gap-2.5',
}
</script>

<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :class="[
      'inline-flex items-center justify-center transition-all duration-150 select-none focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-slate-900',
      variant !== 'link' && sizeClasses[size],
      variantClasses[variant],
      block ? 'w-full' : '',
      (disabled || loading) ? 'opacity-50 cursor-not-allowed pointer-events-none' : 'cursor-pointer',
    ]"
  >
    <Spinner v-if="loading" size="sm" color="text-current" />
    <slot name="prefix" />
    <slot />
    <slot name="suffix" />
  </button>
</template>

