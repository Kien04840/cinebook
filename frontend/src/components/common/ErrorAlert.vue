<script setup lang="ts">
import Button from './Button.vue'

interface Props {
  title?: string
  message: string
  retryable?: boolean
}

withDefaults(defineProps<Props>(), {
  title: 'Đã có lỗi xảy ra',
  retryable: true,
})

const emit = defineEmits<{
  (e: 'retry'): void
}>()
</script>

<template>
  <div class="rounded-xl border border-rose-800/80 bg-rose-950/40 p-4 sm:p-5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
    <div class="flex items-start gap-3.5">
      <div class="p-2 rounded-lg bg-rose-900/50 text-rose-400 shrink-0 mt-0.5 sm:mt-0">
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
      </div>

      <div>
        <h5 class="text-sm font-semibold text-rose-200">
          {{ title }}
        </h5>
        <p class="text-xs sm:text-sm text-rose-300/90 mt-0.5">
          {{ message }}
        </p>
      </div>
    </div>

    <div v-if="retryable" class="shrink-0 w-full sm:w-auto">
      <Button
        variant="danger"
        size="sm"
        block
        @click="emit('retry')"
      >
        <template #prefix>
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </template>
        Thử lại
      </Button>
    </div>
  </div>
</template>

