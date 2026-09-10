<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  variant?: 'text' | 'title' | 'avatar' | 'poster' | 'button' | 'card' | 'table-row' | 'custom'
  width?: string
  height?: string
  rounded?: string
  animated?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'text',
  width: '',
  height: '',
  rounded: '',
  animated: true,
})

const variantDefaults = computed(() => {
  switch (props.variant) {
    case 'title':
      return {
        width: props.width || 'w-2/3',
        height: props.height || 'h-7',
        rounded: props.rounded || 'rounded-lg',
      }
    case 'avatar':
      return {
        width: props.width || 'w-10',
        height: props.height || 'h-10',
        rounded: props.rounded || 'rounded-full',
      }
    case 'poster':
      return {
        width: props.width || 'w-full',
        height: props.height || 'aspect-[2/3]',
        rounded: props.rounded || 'rounded-xl',
      }
    case 'button':
      return {
        width: props.width || 'w-28',
        height: props.height || 'h-10',
        rounded: props.rounded || 'rounded-lg',
      }
    case 'card':
      return {
        width: props.width || 'w-full',
        height: props.height || 'h-48',
        rounded: props.rounded || 'rounded-2xl',
      }
    case 'table-row':
      return {
        width: props.width || 'w-full',
        height: props.height || 'h-12',
        rounded: props.rounded || 'rounded-md',
      }
    case 'custom':
      return {
        width: props.width,
        height: props.height,
        rounded: props.rounded || 'rounded-lg',
      }
    case 'text':
    default:
      return {
        width: props.width || 'w-full',
        height: props.height || 'h-4',
        rounded: props.rounded || 'rounded',
      }
  }
})
</script>

<template>
  <div
    role="status"
    aria-busy="true"
    aria-label="Đang tải..."
    :class="[
      'bg-slate-800/80 border border-slate-750/50 overflow-hidden relative shrink-0',
      variantDefaults.width,
      variantDefaults.height,
      variantDefaults.rounded,
      animated ? 'animate-shimmer' : '',
    ]"
  >
    <span class="sr-only">Đang tải...</span>
    <slot />
  </div>
</template>

