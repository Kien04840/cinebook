<script setup lang="ts">
import { ref, watch, computed } from 'vue'

interface Props {
  src?: string | null
  alt?: string
  aspectRatio?: '2/3' | '16/9' | '1/1' | 'auto'
  imgClass?: string
  fallbackText?: string
  eager?: boolean
  rounded?: string
}

const props = withDefaults(defineProps<Props>(), {
  src: '',
  alt: 'Movie visual',
  aspectRatio: '2/3',
  imgClass: '',
  fallbackText: '',
  eager: false,
  rounded: 'rounded-xl',
})

const isLoaded = ref<boolean>(false)
const hasError = ref<boolean>(false)

const aspectClass = computed(() => {
  switch (props.aspectRatio) {
    case '2/3':
      return 'aspect-[2/3]'
    case '16/9':
      return 'aspect-video'
    case '1/1':
      return 'aspect-square'
    case 'auto':
    default:
      return ''
  }
})

function handleLoad() {
  isLoaded.value = true
  hasError.value = false
}

function handleError() {
  isLoaded.value = false
  hasError.value = true
}

watch(
  () => props.src,
  (newSrc) => {
    if (!newSrc || !newSrc.trim()) {
      isLoaded.value = false
      hasError.value = true
    } else {
      isLoaded.value = false
      hasError.value = false
    }
  },
  { immediate: true }
)
</script>

<template>
  <div
    :class="[
      'relative w-full overflow-hidden bg-slate-900 select-none flex items-center justify-center',
      aspectClass,
      rounded,
    ]"
  >
    <!-- Shimmer Placeholder while loading -->
    <div
      v-if="!isLoaded && !hasError && src"
      class="absolute inset-0 z-0 bg-slate-800/80 animate-shimmer"
      aria-hidden="true"
    />

    <!-- Real Image -->
    <img
      v-if="src && !hasError"
      :src="src"
      :alt="alt"
      :loading="eager ? 'eager' : 'lazy'"
      :fetchpriority="eager ? 'high' : 'auto'"
      decoding="async"
      :class="[
        'w-full h-full object-cover transition-opacity duration-300',
        isLoaded ? 'opacity-100' : 'opacity-0',
        imgClass,
      ]"
      @load="handleLoad"
      @error="handleError"
    />

    <!-- Cinematic Fallback State when Error or Empty URL -->
    <div
      v-if="hasError || !src"
      class="absolute inset-0 flex flex-col items-center justify-center p-3 text-center bg-gradient-to-b from-slate-850 to-slate-950 border border-slate-800 text-slate-400"
    >
      <slot name="fallback">
        <div class="w-10 h-10 rounded-xl bg-slate-800/80 border border-slate-700/60 flex items-center justify-center text-slate-400 mb-1.5 shadow-inner">
          <svg class="w-5 h-5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M7 4v16M17 4v16M3 8h4m10 0h4M3 12h18M3 16h4m10 0h4M4 20h16a1 1 0 001-1V5a1 1 0 00-1-1H4a1 1 0 00-1 1v14a1 1 0 001 1z" />
          </svg>
        </div>
        <p class="text-[11px] font-medium text-slate-300 line-clamp-2 max-w-[90%] leading-tight">
          {{ fallbackText || alt || 'CineBook' }}
        </p>
      </slot>
    </div>
  </div>
</template>

