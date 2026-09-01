<script setup lang="ts">
interface Props {
  variant?: 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'primary'
  size?: 'sm' | 'md'
  rounded?: boolean
}

withDefaults(defineProps<Props>(), {
  variant: 'neutral',
  size: 'md',
  rounded: true,
})

const variantClasses = {
  success: 'bg-emerald-950/60 text-emerald-300 border-emerald-700/60',
  warning: 'bg-amber-950/60 text-amber-300 border-amber-700/60',
  danger: 'bg-rose-950/60 text-rose-300 border-rose-700/60',
  info: 'bg-sky-950/60 text-sky-300 border-sky-700/60',
  neutral: 'bg-slate-800 text-slate-300 border-slate-700',
  primary: 'bg-indigo-950/60 text-indigo-300 border-indigo-700/60',
}

const sizeClasses = {
  sm: 'px-2 py-0.5 text-xs',
  md: 'px-2.5 py-1 text-xs font-medium',
}
</script>

<template>
  <span
    :class="[
      'inline-flex items-center gap-1.5 border font-medium tracking-wide',
      rounded ? 'rounded-full' : 'rounded-md',
      variantClasses[variant],
      sizeClasses[size],
    ]"
  >
    <slot name="prefix" />
    <slot />
  </span>
</template>

