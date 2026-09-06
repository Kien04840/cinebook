<script setup lang="ts">
import { ref } from 'vue'
import bookingService from '@/services/booking.service'
import ticketService from '@/services/ticket.service'
import type { BookingVerifyResponse } from '@/types/booking.types'
import type { TicketVerifyResponse } from '@/types/ticket.types'
import { formatCurrency, formatDateTime } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Input from '@/components/common/Input.vue'
import Badge from '@/components/common/Badge.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import { useToast } from '@/composables/useToast'

const { t } = useI18n()
const toast = useToast()

const qrCodeInput = ref('')
const isChecking = ref(false)
const isCheckingIn = ref(false)
const errorMessage = ref('')
const checkInSuccessMessage = ref('')

// State for Booking check-in (Primary)
const bookingVerifyResult = ref<BookingVerifyResponse | null>(null)

// State for legacy Ticket check-in (Fallback)
const singleTicketVerifyResult = ref<TicketVerifyResponse | null>(null)

async function handleCheckTicket() {
  const code = qrCodeInput.value.trim()
  if (!code) return

  isChecking.value = true
  errorMessage.value = ''
  checkInSuccessMessage.value = ''
  bookingVerifyResult.value = null
  singleTicketVerifyResult.value = null

  try {
    // 1. Try booking-level check-in verification first (Primary)
    const bResult = await bookingService.verifyBookingCheckIn(code)
    bookingVerifyResult.value = bResult
    if (bResult.checkInEligible) {
      toast.info(`Tìm thấy đơn hàng ${bResult.bookingCode} (${bResult.validTickets} ghế hợp lệ)!`)
    } else {
      toast.warning(bResult.ineligibleReason || t('adminTickets.ticketCancelled'))
    }
  } catch (err: any) {
    // 2. Fallback to legacy single ticket verification if booking verification fails
    try {
      const tResult = await ticketService.verifyTicket(code)
      singleTicketVerifyResult.value = tResult
      if (tResult.checkInEligible) {
        toast.info(t('adminTickets.ticketValid'))
      } else {
        toast.warning(tResult.ineligibleReason || t('adminTickets.ticketCancelled'))
      }
    } catch (legacyErr: any) {
      errorMessage.value = err.response?.data?.message || legacyErr.response?.data?.message || t('common.errorTitle')
      toast.error(errorMessage.value)
    }
  } finally {
    isChecking.value = false
  }
}

async function handleConfirmBookingCheckIn() {
  if (!bookingVerifyResult.value || !bookingVerifyResult.value.checkInEligible) return

  isCheckingIn.value = true
  errorMessage.value = ''
  checkInSuccessMessage.value = ''

  try {
    const result = await bookingService.checkInBooking(bookingVerifyResult.value.checkInCode)
    checkInSuccessMessage.value = result.message
    // Update local state
    bookingVerifyResult.value.checkInEligible = false
    bookingVerifyResult.value.validTickets = 0
    bookingVerifyResult.value.usedTickets = result.totalTickets
    if (result.tickets && result.tickets.length > 0) {
      bookingVerifyResult.value.tickets = result.tickets
    } else {
      bookingVerifyResult.value.tickets.forEach(t => {
        if (t.ticketStatus === 'VALID') t.ticketStatus = 'USED'
      })
    }
    toast.success(result.message || t('common.successTitle'))
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
    toast.error(errorMessage.value)
  } finally {
    isCheckingIn.value = false
  }
}

async function handleConfirmSingleTicketCheckIn() {
  if (!singleTicketVerifyResult.value || !singleTicketVerifyResult.value.checkInEligible) return

  isCheckingIn.value = true
  errorMessage.value = ''

  try {
    const result = await ticketService.checkInTicket(singleTicketVerifyResult.value.ticketId)
    singleTicketVerifyResult.value.ticketStatus = result.ticketStatus
    singleTicketVerifyResult.value.checkInEligible = false
    checkInSuccessMessage.value = result.message || t('common.successTitle')
    toast.success(result.message || t('common.successTitle'))
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
    toast.error(errorMessage.value)
  } finally {
    isCheckingIn.value = false
  }
}

function resetScanner() {
  qrCodeInput.value = ''
  bookingVerifyResult.value = null
  singleTicketVerifyResult.value = null
  errorMessage.value = ''
  checkInSuccessMessage.value = ''
}

function getTicketStatusBadge(status: string) {
  switch (status) {
    case 'VALID':
      return { variant: 'success' as const, label: 'Chưa soát' }
    case 'USED':
      return { variant: 'warning' as const, label: 'Đã soát vé' }
    case 'CANCELLED':
      return { variant: 'danger' as const, label: 'Đã hủy' }
    default:
      return { variant: 'neutral' as const, label: status }
  }
}
</script>

<template>
  <div class="max-w-4xl mx-auto space-y-6">
    <!-- Header -->
    <div>
      <h1 class="text-2xl font-bold text-white tracking-tight">{{ t('adminTickets.title') }}</h1>
      <p class="text-xs sm:text-sm text-slate-400 mt-1">
        {{ t('adminTickets.subtitle') }}
      </p>
    </div>

    <!-- Error Alert -->
    <ErrorAlert v-if="errorMessage" :message="errorMessage" />

    <!-- Success Message Banner -->
    <div
      v-if="checkInSuccessMessage"
      class="p-4 rounded-2xl bg-emerald-950/80 border border-emerald-500/80 text-emerald-200 flex items-center gap-3 shadow-lg animate-fade-in"
    >
      <span class="text-xl">✅</span>
      <p class="text-sm font-medium">{{ checkInSuccessMessage }}</p>
    </div>

    <!-- Scanner Input Card -->
    <Card padding="md" class="space-y-4">
      <form class="flex flex-col sm:flex-row gap-3" @submit.prevent="handleCheckTicket">
        <div class="flex-1">
          <Input
            v-model="qrCodeInput"
            :placeholder="t('adminTickets.qrInputPlaceholder')"
            :disabled="isChecking || isCheckingIn"
            autofocus
            clearable
          >
            <template #prefix>
              <svg class="w-5 h-5 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v1m6 11h2m-6 0h-2v4m0-11v3m0 0h.01M12 12h4.01M16 20h4M4 12h4m12 0h.01M5 8h2a1 1 0 001-1V5a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1zm12 0h2a1 1 0 001-1V5a1 1 0 00-1-1h-2a1 1 0 00-1 1v2a1 1 0 001 1zM5 20h2a1 1 0 001-1v-2a1 1 0 00-1-1H5a1 1 0 00-1 1v2a1 1 0 001 1z" />
              </svg>
            </template>
          </Input>
        </div>

        <div class="flex gap-2">
          <Button
            type="submit"
            variant="primary"
            size="md"
            :loading="isChecking"
            :disabled="!qrCodeInput.trim() || isCheckingIn"
          >
            {{ t('adminTickets.checkBtn') }}
          </Button>
          <Button
            type="button"
            variant="secondary"
            size="md"
            @click="resetScanner"
          >
            {{ t('common.refresh') }}
          </Button>
        </div>
      </form>
    </Card>

    <!-- SECTION A: Booking Verification Result (Primary Flow: One QR for multiple seats) -->
    <Card v-if="bookingVerifyResult" padding="md" class="space-y-6">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-800 pb-4">
        <div>
          <span class="text-xs text-slate-400 uppercase tracking-wider font-semibold">Đơn Đặt Vé & Soát Vé</span>
          <h3 class="text-lg font-bold text-white mt-0.5">{{ bookingVerifyResult.movieTitle || '—' }}</h3>
          <p class="text-xs text-slate-400 font-mono mt-0.5">
            Mã đơn: <span class="text-indigo-400 font-bold">{{ bookingVerifyResult.bookingCode }}</span>
            <span v-if="bookingVerifyResult.customerName" class="text-slate-500 font-sans ml-2">• Khách: {{ bookingVerifyResult.customerName }}</span>
          </p>
        </div>

        <Badge :variant="bookingVerifyResult.checkInEligible ? 'success' : 'danger'" size="md">
          {{ bookingVerifyResult.checkInEligible ? 'Hợp lệ - Sẵn sàng soát vé' : (bookingVerifyResult.ineligibleReason || 'Không hợp lệ') }}
        </Badge>
      </div>

      <!-- Showtime Info Grid -->
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs">
        <div class="bg-slate-850 p-3.5 rounded-xl border border-slate-800">
          <span class="text-slate-400 font-medium">{{ t('booking.cinemaInfo') }}</span>
          <p class="font-bold text-slate-200 mt-1 text-sm">{{ bookingVerifyResult.cinemaName }}</p>
          <p class="text-slate-400 text-[11px]">{{ bookingVerifyResult.auditoriumName }}</p>
        </div>

        <div class="bg-slate-850 p-3.5 rounded-xl border border-slate-800">
          <span class="text-slate-400 font-medium">{{ t('booking.showtimeInfo') }}</span>
          <p class="font-bold text-slate-200 mt-1 font-mono text-sm">
            {{ formatDateTime(bookingVerifyResult.startTime) }}
          </p>
        </div>

        <div class="bg-slate-850 p-3.5 rounded-xl border border-slate-800">
          <span class="text-slate-400 font-medium">Tình trạng vé</span>
          <div class="flex items-center gap-3 mt-1.5 font-bold">
            <span class="text-slate-300">Tổng: {{ bookingVerifyResult.totalTickets }}</span>
            <span class="text-emerald-400">Hợp lệ: {{ bookingVerifyResult.validTickets }}</span>
            <span class="text-amber-400">Đã soát: {{ bookingVerifyResult.usedTickets }}</span>
          </div>
        </div>
      </div>

      <!-- Ticket Items List (All seats in this booking) -->
      <div class="space-y-3">
        <h4 class="text-xs font-bold text-slate-300 uppercase tracking-wider">
          Danh sách ghế trong đơn hàng ({{ bookingVerifyResult.tickets?.length || 0 }} vé)
        </h4>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          <div
            v-for="item in bookingVerifyResult.tickets"
            :key="item.ticketId"
            class="p-3.5 rounded-xl bg-slate-850 border border-slate-800 flex items-center justify-between"
          >
            <div>
              <div class="flex items-center gap-2">
                <span class="text-base font-black text-indigo-400 font-mono">{{ item.seatCode }}</span>
                <span v-if="item.seatTypeName" class="text-xs text-slate-400">({{ item.seatTypeName }})</span>
              </div>
              <p class="text-[11px] text-slate-400 mt-0.5">
                Hàng {{ item.rowLabel }} • Số {{ item.seatNumber }} • {{ formatCurrency(item.ticketPrice) }}
              </p>
            </div>

            <Badge :variant="getTicketStatusBadge(item.ticketStatus).variant" size="sm">
              {{ getTicketStatusBadge(item.ticketStatus).label }}
            </Badge>
          </div>
        </div>
      </div>

      <!-- Action Button -->
      <div v-if="bookingVerifyResult.checkInEligible" class="flex justify-end pt-2 border-t border-slate-800">
        <Button
          variant="primary"
          size="lg"
          :loading="isCheckingIn"
          @click="handleConfirmBookingCheckIn"
        >
          <template #prefix>
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
            </svg>
          </template>
          Xác Nhận Soát Vé ({{ bookingVerifyResult.validTickets }} ghế)
        </Button>
      </div>
    </Card>

    <!-- SECTION B: Legacy Single Ticket Result (Fallback) -->
    <Card v-if="singleTicketVerifyResult" padding="md" class="space-y-6">
      <div class="flex items-center justify-between border-b border-slate-800 pb-4">
        <div>
          <span class="text-xs text-slate-400 uppercase tracking-wider">{{ t('adminTickets.ticketInfoTitle') }}</span>
          <h3 class="text-lg font-bold text-white mt-0.5">{{ singleTicketVerifyResult.movieTitle || '—' }}</h3>
        </div>

        <Badge :variant="singleTicketVerifyResult.checkInEligible ? 'success' : 'danger'" size="md">
          {{ singleTicketVerifyResult.checkInEligible ? t('adminTickets.ticketValid') : (singleTicketVerifyResult.ineligibleReason || t('adminTickets.ticketCancelled')) }}
        </Badge>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 text-xs">
        <div class="bg-slate-850 p-3 rounded-lg border border-slate-800">
          <span class="text-slate-400">{{ t('booking.cinemaInfo') }}</span>
          <p class="font-bold text-slate-200 mt-1">{{ singleTicketVerifyResult.cinemaName }}</p>
          <p class="text-slate-400 text-[11px]">{{ singleTicketVerifyResult.auditoriumName }}</p>
        </div>

        <div class="bg-slate-850 p-3 rounded-lg border border-slate-800">
          <span class="text-slate-400">{{ t('booking.showtimeInfo') }}</span>
          <p class="font-bold text-slate-200 mt-1 font-mono">
            {{ formatDateTime(singleTicketVerifyResult.startTime) }}
          </p>
        </div>

        <div class="bg-slate-850 p-3 rounded-lg border border-slate-800">
          <span class="text-slate-400">{{ t('booking.stepSelectSeats') }}</span>
          <p class="font-bold text-indigo-400 mt-1 text-base font-mono">
            {{ singleTicketVerifyResult.seatCode }}
          </p>
          <p class="text-slate-400 text-[11px]">{{ singleTicketVerifyResult.seatTypeName }}</p>
        </div>

        <div class="bg-slate-850 p-3 rounded-lg border border-slate-800">
          <span class="text-slate-400">{{ t('booking.totalPrice') }}</span>
          <p class="font-bold text-emerald-400 mt-1 text-base font-mono">
            {{ formatCurrency(singleTicketVerifyResult.ticketPrice || 0) }}
          </p>
        </div>
      </div>

      <!-- Action -->
      <div v-if="singleTicketVerifyResult.checkInEligible" class="flex justify-end pt-2">
        <Button
          variant="primary"
          size="lg"
          :loading="isCheckingIn"
          @click="handleConfirmSingleTicketCheckIn"
        >
          <template #prefix>
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
            </svg>
          </template>
          {{ t('adminTickets.checkinBtn') }}
        </Button>
      </div>
    </Card>
  </div>
</template>
