<script setup lang="ts">
import { useToastStore } from '@/stores/toast'
import { useI18n } from '@/composables/useI18n'

const { t } = useI18n()

const toastStore = useToastStore()

const typeStyles = {
  success: {
    border: 'border-emerald-700/80',
    bg: 'bg-emerald-950/90',
    text: 'text-emerald-200',
    iconText: 'text-emerald-400',
    title: 'text-emerald-100',
  },
  error: {
    border: 'border-rose-700/80',
    bg: 'bg-rose-950/90',
    text: 'text-rose-200',
    iconText: 'text-rose-400',
    title: 'text-rose-100',
  },
  warning: {
    border: 'border-amber-700/80',
    bg: 'bg-amber-950/90',
    text: 'text-amber-200',
    iconText: 'text-amber-400',
    title: 'text-amber-100',
  },
  info: {
    border: 'border-sky-700/80',
    bg: 'bg-sky-950/90',
    text: 'text-sky-200',
    iconText: 'text-sky-400',
    title: 'text-sky-100',
  },
}
</script>

<template>
  <div
    class="fixed top-4 right-4 z-50 flex flex-col gap-2.5 max-w-sm w-full pointer-events-none px-4 sm:px-0"
    aria-live="polite"
  >
    <TransitionGroup
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0 translate-y-2 scale-95"
      enter-to-class="opacity-100 translate-y-0 scale-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100 scale-100"
      leave-to-class="opacity-0 scale-95"
    >
      <div
        v-for="toast in toastStore.toasts"
        :key="toast.id"
        :class="[
          'pointer-events-auto w-full p-4 rounded-xl border shadow-xl backdrop-blur-md flex items-start gap-3 justify-between',
          typeStyles[toast.type].bg,
          typeStyles[toast.type].border,
        ]"
        role="alert"
      >
        <div class="flex items-start gap-3">
          <div :class="['shrink-0 mt-0.5', typeStyles[toast.type].iconText]">
            <!-- Success -->
            <svg v-if="toast.type === 'success'" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <!-- Error -->
            <svg v-else-if="toast.type === 'error'" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <!-- Warning -->
            <svg v-else-if="toast.type === 'warning'" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <!-- Info -->
            <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>

          <div class="space-y-0.5">
            <h6 v-if="toast.title" :class="['text-xs font-semibold uppercase tracking-wider', typeStyles[toast.type].title]">
              {{ toast.title }}
            </h6>
            <p :class="['text-sm leading-snug', typeStyles[toast.type].text]">
              {{ toast.message }}
            </p>
          </div>
        </div>

        <button
          type="button"
          class="shrink-0 p-1 text-slate-400 hover:text-white rounded-lg hover:bg-white/10 transition-colors focus:outline-none"
          :aria-label="t('common.close')"
          @click="toastStore.remove(toast.id)"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

