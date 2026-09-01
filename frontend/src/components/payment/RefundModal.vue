<script setup lang="ts">
import { ref, computed } from 'vue'
import type { BookingDetailResponse, BookingSummaryResponse } from '@/types/booking.types'
import { formatCurrency, formatDate, formatTime } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Button from '@/components/common/Button.vue'

interface Props {
  isOpen: boolean
  booking: BookingDetailResponse | BookingSummaryResponse | null
  isLoading?: boolean
}

interface Emits {
  (e: 'close'): void
  (e: 'confirm', reason: string): void
}

const props = withDefaults(defineProps<Props>(), {
  isLoading: false,
})

const emit = defineEmits<Emits>()
const { t } = useI18n()

const refundReason = ref<string>('')

function handleConfirm() {
  emit('confirm', refundReason.value.trim())
}
const movieTitle = computed(() => {
  if (!props.booking) return ''
  if ('movieTitle' in props.booking && props.booking.movieTitle) return props.booking.movieTitle
  if (props.booking.showtime) {
    if ('movieTitle' in props.booking.showtime && (props.booking.showtime as any).movieTitle) {
      return (props.booking.showtime as any).movieTitle
    }
    if ('movie' in props.booking.showtime && (props.booking.showtime as any).movie?.title) {
      return (props.booking.showtime as any).movie.title
    }
  }
  return 'Phim Chiếu Rạp'
})

const showtimeText = computed(() => {
  if (!props.booking) return '---'
  const timeStr = ('showtimeStartTime' in props.booking && props.booking.showtimeStartTime)
    ? props.booking.showtimeStartTime
    : props.booking.showtime?.startTime
  if (!timeStr) return '---'
  return `${formatTime(timeStr)} ${formatDate(timeStr)}`
})
</script>

<template>
  <Teleport to="body">
    <transition name="modal">
      <div
        v-if="isOpen && booking"
        class="fixed inset-0 z-50 overflow-y-auto bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4"
        @click.self="emit('close')"
      >
        <div class="relative w-full max-w-lg rounded-3xl bg-slate-900 border border-slate-800 shadow-2xl p-6 sm:p-7 space-y-6 animate-scale">
          <!-- Modal Header -->
          <div class="flex items-center justify-between border-b border-slate-800 pb-4">
            <div class="flex items-center gap-2">
              <span class="text-2xl">💸</span>
              <h2 class="text-xl font-bold text-white tracking-tight">
                {{ t('refund.modalTitle') }}
              </h2>
            </div>

            <button
              type="button"
              class="w-8 h-8 rounded-full bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white flex items-center justify-center transition-colors"
              :aria-label="t('common.close')"
              @click="emit('close')"
            >
              ✕
            </button>
          </div>

          <!-- Summary Info & Policy Alert -->
          <div class="space-y-4 text-sm">
            <div class="p-4 rounded-2xl bg-slate-950/70 border border-slate-800/80 space-y-2.5">
              <div class="flex justify-between text-slate-400">
                <span>{{ t('refund.movieTitle') }}:</span>
                <span class="font-bold text-white truncate max-w-[240px]">
                  {{ movieTitle }}
                </span>
              </div>

              <div class="flex justify-between text-slate-400">
                <span>{{ t('refund.showtime') }}:</span>
                <span class="text-slate-200 font-medium">
                  {{ showtimeText }}
                </span>
              </div>

              <div class="pt-2.5 border-t border-slate-800 flex justify-between items-center text-sm">
                <span class="font-bold text-white">{{ t('refund.refundAmount') }}:</span>
                <span class="text-lg font-black text-emerald-400">
                  {{ formatCurrency(booking.totalAmount) }}
                </span>
              </div>
            </div>

            <!-- 2-Hour Policy Notice Alert -->
            <div class="p-4 rounded-xl bg-amber-950/40 border border-amber-500/40 text-amber-200 space-y-1.5 text-xs sm:text-sm">
              <p class="font-bold flex items-center gap-1.5 text-amber-300 text-sm">
                <span>⚠️</span> {{ t('refund.policyTitle') }}
              </p>
              <p class="leading-relaxed text-amber-200/90 text-xs sm:text-sm">
                {{ t('refund.policyDesc') }}
              </p>
            </div>

            <!-- Optional Reason Input -->
            <div class="space-y-1.5">
              <label for="refundReasonInput" class="block font-semibold text-slate-200 text-sm">
                {{ t('refund.reasonLabel') }}
              </label>
              <textarea
                id="refundReasonInput"
                v-model="refundReason"
                rows="2"
                maxlength="250"
                class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white text-sm placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-500"
                :placeholder="t('refund.reasonPlaceholder')"
              ></textarea>
            </div>
          </div>

          <!-- Modal Action Buttons -->
          <div class="flex items-center justify-end gap-3 pt-2 border-t border-slate-800">
            <Button variant="secondary" size="md" :disabled="isLoading" @click="emit('close')">
              {{ t('common.cancel') }}
            </Button>

            <Button
              variant="danger"
              size="md"
              :loading="isLoading"
              class="shadow-lg shadow-rose-600/30"
              @click="handleConfirm"
            >
              {{ t('refund.confirmRefundBtn') }}
            </Button>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.25s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.animate-scale {
  animation: scaleUp 0.25s ease-out;
}

@keyframes scaleUp {
  from {
    transform: scale(0.96);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
