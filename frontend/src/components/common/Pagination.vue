<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import Button from './Button.vue'

interface Props {
  currentPage: number
  totalPages: number
  totalElements?: number
  pageSize?: number
}

const props = withDefaults(defineProps<Props>(), {
  currentPage: 0,
  totalPages: 1,
  totalElements: 0,
  pageSize: 10,
})

const emit = defineEmits<{
  (e: 'update:currentPage', page: number): void
  (e: 'page-change', page: number): void
}>()

const { t } = useI18n()

const hasPrevious = computed(() => props.currentPage > 0)
const hasNext = computed(() => props.currentPage < props.totalPages - 1)

const startItem = computed(() => props.currentPage * props.pageSize + 1)
const endItem = computed(() => Math.min((props.currentPage + 1) * props.pageSize, props.totalElements))

const visiblePages = computed(() => {
  const pages: (number | string)[] = []
  const total = props.totalPages
  const current = props.currentPage

  if (total <= 7) {
    for (let i = 0; i < total; i++) {
      pages.push(i)
    }
  } else {
    pages.push(0)
    if (current > 2) {
      pages.push('ellipsis-start')
    }
    const start = Math.max(1, current - 1)
    const end = Math.min(total - 2, current + 1)
    for (let i = start; i <= end; i++) {
      pages.push(i)
    }
    if (current < total - 3) {
      pages.push('ellipsis-end')
    }
    pages.push(total - 1)
  }
  return pages
})

function changePage(page: number) {
  if (page >= 0 && page < props.totalPages && page !== props.currentPage) {
    emit('update:currentPage', page)
    emit('page-change', page)
  }
}
</script>

<template>
  <div class="flex flex-col sm:flex-row items-center justify-between gap-3 px-4 py-3 border-t border-slate-700/80 text-xs sm:text-sm text-slate-400">
    <div>
      <span v-if="totalElements > 0">
        {{ t('common.paginationInfo', { start: startItem, end: endItem, total: totalElements }) }}
      </span>
      <span v-else>
        {{ t('common.pageInfo', { current: currentPage + 1, total: Math.max(totalPages, 1) }) }}
      </span>
    </div>

    <div class="flex items-center gap-1.5 flex-wrap justify-center">
      <Button
        variant="secondary"
        size="sm"
        :disabled="!hasPrevious"
        @click="changePage(currentPage - 1)"
      >
        <template #prefix>
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </template>
        {{ t('common.prev') }}
      </Button>

      <div class="flex items-center gap-1">
        <template v-for="(p, idx) in visiblePages" :key="idx">
          <span v-if="typeof p === 'string'" class="px-1 text-slate-500 select-none">...</span>
          <button
            v-else
            type="button"
            :class="[
              'min-w-[32px] h-8 px-2 rounded-lg text-xs font-bold transition-all focus:outline-none focus:ring-2 focus:ring-indigo-500',
              currentPage === p
                ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/30'
                : 'bg-slate-800 text-slate-300 hover:bg-slate-700 hover:text-white'
            ]"
            @click="changePage(p)"
          >
            {{ p + 1 }}
          </button>
        </template>
      </div>

      <Button
        variant="secondary"
        size="sm"
        :disabled="!hasNext"
        @click="changePage(currentPage + 1)"
      >
        {{ t('common.next') }}
        <template #suffix>
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
          </svg>
        </template>
      </Button>
    </div>
  </div>
</template>
