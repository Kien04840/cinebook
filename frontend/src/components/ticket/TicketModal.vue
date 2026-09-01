<script setup lang="ts">
import { ref, computed } from 'vue'
import type { BookingDetailResponse } from '@/types/booking.types'
import { formatCurrency, formatDate, formatTime } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import ElectronicTicket from './ElectronicTicket.vue'
import Button from '@/components/common/Button.vue'
import Badge from '@/components/common/Badge.vue'

interface Props {
  isOpen: boolean
  booking: BookingDetailResponse | null
  actionLoading?: boolean
}

interface Emits {
  (e: 'close'): void
  (e: 'continue-payment', bookingId: string): void
  (e: 'cancel-booking', bookingId: string): void
}

const props = withDefaults(defineProps<Props>(), {
  actionLoading: false,
})
const emit = defineEmits<Emits>()
const { t } = useI18n()

const currentTicketIndex = ref<number>(0)

const tickets = computed(() => props.booking?.tickets || [])
const currentTicket = computed(() => tickets.value[currentTicketIndex.value] || tickets.value[0])

const seatCodesText = computed(() => {
  if (!props.booking?.seats || props.booking.seats.length === 0) return '---'
  return props.booking.seats.map((s) => s.seatCode).join(', ')
})

function nextTicket() {
  if (tickets.value.length === 0) return
  currentTicketIndex.value = (currentTicketIndex.value + 1) % tickets.value.length
}

function prevTicket() {
  if (tickets.value.length === 0) return
  currentTicketIndex.value =
    (currentTicketIndex.value - 1 + tickets.value.length) % tickets.value.length
}

function printTicket() {
  window.print()
}

function getStatusBadgeVariant(status?: string): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (status) {
    case 'PAID':
      return 'success'
    case 'PENDING_PAYMENT':
      return 'warning'
    case 'CANCELLED':
      return 'danger'
    case 'EXPIRED':
      return 'neutral'
    case 'REFUNDED':
      return 'info'
    default:
      return 'neutral'
  }
}

function getStatusLabel(status?: string): string {
  switch (status) {
    case 'PAID':
      return t('myBookings.statusPaid')
    case 'PENDING_PAYMENT':
      return t('myBookings.statusPending')
    case 'CANCELLED':
      return t('myBookings.statusCancelled')
    case 'EXPIRED':
      return t('myBookings.statusExpired')
    case 'REFUNDED':
      return t('myBookings.statusRefunded')
    default:
      return status || ''
  }
}

function isHoldActive(): boolean {
  if (!props.booking || props.booking.bookingStatus !== 'PENDING_PAYMENT' || !props.booking.holdExpiresAt) return false
  return new Date(props.booking.holdExpiresAt).getTime() > Date.now()
}
</script>

<template>
  <Teleport to="body">
    <transition name="modal">
      <div
        v-if="isOpen && booking"
        class="fixed inset-0 z-50 overflow-y-auto bg-slate-950/85 backdrop-blur-sm flex items-center justify-center p-4 sm:p-6"
        @click.self="emit('close')"
      >
        <div class="relative w-full max-w-3xl rounded-3xl bg-slate-900 border border-slate-800 shadow-2xl p-6 sm:p-8 space-y-6 animate-scale">
          <!-- Modal Header -->
          <div class="flex items-center justify-between border-b border-slate-800 pb-4 print:hidden">
            <div>
              <div class="flex items-center gap-2.5">
                <h2 class="text-lg sm:text-xl font-bold text-white tracking-tight flex items-center gap-2">
                  <span>🎟️</span>
                  <span>{{ booking.bookingStatus === 'PAID' ? t('ticket.modalTitle') : t('booking.stepBookingSummary') }}</span>
                </h2>
                <Badge :variant="getStatusBadgeVariant(booking.bookingStatus)" size="sm">
                  {{ getStatusLabel(booking.bookingStatus) }}
                </Badge>
              </div>
              <p class="text-xs text-slate-400 mt-1 font-mono">
                {{ t('booking.bookingCode') }}: <span class="text-indigo-400 font-bold">{{ booking.bookingCode }}</span>
                <span class="text-slate-500 font-sans ml-2">• {{ t('adminBookings.colCreatedAt') }}: {{ formatDate(booking.createdAt) }}</span>
              </p>
            </div>

            <button
              type="button"
              class="w-9 h-9 rounded-full bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white flex items-center justify-center transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500"
              :aria-label="t('common.close')"
              @click="emit('close')"
            >
              ✕
            </button>
          </div>

          <!-- SECTION 1: PAID with VALID Tickets -> Display Electronic Ticket Carousel -->
          <template v-if="booking.bookingStatus === 'PAID' && tickets.length > 0">
            <!-- Multiple Tickets Tab Selector (if > 1 ticket) -->
            <div v-if="tickets.length > 1" class="flex items-center justify-between gap-2 print:hidden">
              <div class="flex items-center gap-1.5 overflow-x-auto pb-1">
                <button
                  v-for="(tkt, idx) in tickets"
                  :key="tkt.id"
                  type="button"
                  :class="[
                    'px-3 py-1.5 rounded-xl text-xs font-bold transition-all whitespace-nowrap',
                    currentTicketIndex === idx
                      ? 'bg-indigo-600 text-white shadow-md'
                      : 'bg-slate-800 text-slate-400 hover:bg-slate-750 hover:text-slate-200'
                  ]"
                  @click="currentTicketIndex = idx"
                >
                  {{ t('booking.stepSelectSeats') }} {{ tkt.seatCode }} ({{ idx + 1 }}/{{ tickets.length }})
                </button>
              </div>

              <div class="flex items-center gap-1 shrink-0">
                <button
                  type="button"
                  class="w-8 h-8 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 flex items-center justify-center text-sm"
                  aria-label="Previous Ticket"
                  @click="prevTicket"
                >
                  ←
                </button>
                <button
                  type="button"
                  class="w-8 h-8 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 flex items-center justify-center text-sm"
                  aria-label="Next Ticket"
                  @click="nextTicket"
                >
                  →
                </button>
              </div>
            </div>

            <!-- Electronic Ticket View Component -->
            <div v-if="currentTicket" class="flex justify-center">
              <ElectronicTicket :ticket="currentTicket" :booking="booking" />
            </div>
          </template>

          <!-- SECTION 2: General Booking Information for PENDING_PAYMENT / CANCELLED / EXPIRED / REFUNDED -->
          <template v-else>
            <div class="rounded-2xl bg-slate-850 border border-slate-800 p-5 sm:p-6 space-y-6">
              <!-- Movie & Cinema Info Row -->
              <div class="flex flex-col sm:flex-row gap-5 items-start">
                <img
                  v-if="booking.showtime?.movie?.posterUrl"
                  :src="booking.showtime.movie.posterUrl"
                  :alt="booking.showtime.movie.title"
                  class="w-24 sm:w-28 aspect-[2/3] rounded-xl object-cover border border-slate-700 shadow-md shrink-0"
                />
                <div class="space-y-2 flex-1">
                  <div class="flex items-center gap-2">
                    <h3 class="text-xl sm:text-2xl font-bold text-white tracking-tight">
                      {{ booking.showtime?.movie?.title || 'Vé xem phim' }}
                    </h3>
                    <Badge v-if="booking.showtime?.format" variant="primary" size="sm">
                      {{ booking.showtime.format }}
                    </Badge>
                  </div>

                  <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm text-slate-300 pt-2">
                    <div>
                      <span class="text-xs text-slate-400 block font-medium">Cụm rạp:</span>
                      <span class="font-semibold text-slate-100 text-sm sm:text-base">{{ booking.showtime?.cinema?.name || 'Rạp CineBook' }}</span>
                      <span v-if="booking.showtime?.cinema?.city" class="text-slate-400 text-xs"> ({{ booking.showtime.cinema.city }})</span>
                    </div>
                    <div>
                      <span class="text-xs text-slate-400 block font-medium">Phòng chiếu:</span>
                      <span class="font-semibold text-slate-100 text-sm sm:text-base">{{ booking.showtime?.auditorium?.name || 'Phòng chiếu' }}</span>
                    </div>
                    <div>
                      <span class="text-xs text-slate-400 block font-medium">Suất chiếu:</span>
                      <span class="font-bold text-indigo-400 text-sm sm:text-base">
                        {{ booking.showtime?.startTime ? formatTime(booking.showtime.startTime) : '---' }}
                      </span>
                      <span class="text-slate-300 text-xs ml-1">
                        {{ booking.showtime?.startTime ? formatDate(booking.showtime.startTime) : '' }}
                      </span>
                    </div>
                    <div>
                      <span class="text-xs text-slate-400 block font-medium">Ghế đã chọn:</span>
                      <span class="font-bold text-emerald-400 text-sm sm:text-base">{{ seatCodesText }}</span>
                      <span class="text-slate-400 text-xs ml-1">({{ booking.seats?.length || 0 }} ghế)</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Status specific details block -->
              <div v-if="booking.bookingStatus === 'PENDING_PAYMENT'" class="p-4 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 text-sm space-y-1.5 leading-relaxed">
                <div class="flex items-center gap-2 font-bold text-base">
                  <span>⏳</span>
                  <span>Đơn đặt vé đang trong trạng thái chờ thanh toán</span>
                </div>
                <p v-if="isHoldActive()" class="text-amber-200/90 text-sm">
                  Ghế được giữ đến <strong class="text-amber-100">{{ formatTime(booking.holdExpiresAt) }}</strong>. Vui lòng thanh toán trước khi hết hạn giữ chỗ.
                </p>
                <p v-else class="text-rose-400 font-semibold text-sm">
                  Hạn giữ chỗ đã kết thúc. Các ghế đã được tự động giải phóng.
                </p>
              </div>

              <div v-else-if="booking.bookingStatus === 'CANCELLED'" class="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-sm space-y-1.5 leading-relaxed">
                <div class="font-bold text-base">Đơn đặt vé đã bị hủy</div>
                <p v-if="booking.cancelledReason" class="text-sm">Lý do: {{ booking.cancelledReason }}</p>
                <p v-if="booking.cancelledAt" class="text-rose-400/80 text-xs">Thời gian hủy: {{ formatDate(booking.cancelledAt) }} {{ formatTime(booking.cancelledAt) }}</p>
              </div>

              <!-- Price Breakdown Table -->
              <div class="pt-3 border-t border-slate-700/80 text-sm space-y-2.5">
                <div class="flex justify-between text-slate-300">
                  <span>Tạm tính ({{ booking.seats?.length || 0 }} vé)</span>
                  <span class="font-medium">{{ formatCurrency(booking.grossAmount || booking.totalAmount) }}</span>
                </div>
                <div v-if="booking.discountAmount && booking.discountAmount > 0" class="flex justify-between text-rose-400 font-semibold">
                  <span>Giảm giá khuyến mãi {{ booking.promotion?.code ? `(${booking.promotion.code})` : '' }}</span>
                  <span>-{{ formatCurrency(booking.discountAmount) }}</span>
                </div>
                <div class="flex justify-between text-base font-black text-white pt-2.5 border-t border-slate-800">
                  <span>Tổng tiền thanh toán</span>
                  <span class="text-emerald-400 text-lg font-black">{{ formatCurrency(booking.totalAmount) }}</span>
                </div>
              </div>
            </div>
          </template>

          <!-- Modal Actions Footer -->
          <div class="flex items-center justify-between gap-3 pt-2 border-t border-slate-800 print:hidden">
            <Button variant="secondary" size="md" @click="emit('close')">
              {{ t('common.close') }}
            </Button>

            <div class="flex items-center gap-2">
              <!-- Print button for PAID -->
              <Button
                v-if="booking.bookingStatus === 'PAID' && tickets.length > 0"
                variant="primary"
                size="md"
                class="shadow-lg shadow-indigo-600/30"
                @click="printTicket"
              >
                <template #prefix>
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
                  </svg>
                </template>
                {{ t('ticket.printBtn') }}
              </Button>

              <!-- Actions for PENDING_PAYMENT -->
              <template v-else-if="booking.bookingStatus === 'PENDING_PAYMENT' && isHoldActive()">
                <Button
                  variant="ghost"
                  size="md"
                  :loading="actionLoading"
                  class="text-rose-400 hover:text-rose-300"
                  @click="emit('cancel-booking', booking.id)"
                >
                  {{ t('myBookings.cancelBookingBtn') }}
                </Button>
                <Button
                  variant="primary"
                  size="md"
                  :loading="actionLoading"
                  class="shadow-lg shadow-amber-600/30 bg-amber-600 hover:bg-amber-500 text-white"
                  @click="emit('continue-payment', booking.id)"
                >
                  {{ t('myBookings.payNowBtn') }}
                </Button>
              </template>
            </div>
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

