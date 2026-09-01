<script setup lang="ts">
interface Props {
  title?: string
  description?: string
  icon?: string
}

withDefaults(defineProps<Props>(), {
  title: 'Không tìm thấy dữ liệu',
  description: 'Hiện chưa có nội dung nào để hiển thị trong mục này.',
})
</script>

<template>
  <div class="flex flex-col items-center justify-center text-center p-8 sm:p-12 my-6 rounded-xl border border-dashed border-slate-700 bg-slate-800/40">
    <div class="w-14 h-14 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-400 mb-4 shadow-inner">
      <slot name="icon">
        <svg class="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
        </svg>
      </slot>
    </div>

    <h4 class="text-base sm:text-lg font-semibold text-slate-200 mb-1">
      {{ title }}
    </h4>

    <p class="text-xs sm:text-sm text-slate-400 max-w-md mb-6 leading-relaxed">
      {{ description }}
    </p>

    <div v-if="$slots.action" class="flex items-center gap-3">
      <slot name="action" />
    </div>
  </div>
</template>

