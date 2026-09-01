<script setup lang="ts">
import { ref } from 'vue'
import ticketService from '@/services/ticket.service'
import type { TicketVerifyResponse, TicketCheckInResponse } from '@/types/ticket.types'
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
const verifyResult = ref<TicketVerifyResponse | null>(null)
const checkInResult = ref<TicketCheckInResponse | null>(null)

async function handleCheckTicket() {
  if (!qrCodeInput.value.trim()) return

  isChecking.value = true
  errorMessage.value = ''
  verifyResult.value = null
  checkInResult.value = null

  try {
    const result = await ticketService.verifyTicket(qrCodeInput.value.trim())
    verifyResult.value = result
    if (result.checkInEligible) {
      toast.info(t('adminTickets.ticketValid'))
    } else {
      toast.warning(result.ineligibleReason || t('adminTickets.ticketCancelled'))
    }
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
    toast.error(errorMessage.value)
  } finally {
    isChecking.value = false
  }
}

async function handleConfirmCheckIn() {
  if (!verifyResult.value || !verifyResult.value.checkInEligible) return

  isCheckingIn.value = true
  errorMessage.value = ''

  try {
    const result = await ticketService.checkInTicket(verifyResult.value.ticketId)
    checkInResult.value = result
    verifyResult.value.ticketStatus = result.ticketStatus
    verifyResult.value.checkInEligible = false
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
  verifyResult.value = null
  checkInResult.value = null
  errorMessage.value = ''
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

    <!-- Ticket Result Display -->
    <Card v-if="verifyResult" padding="md" class="space-y-6">
      <div class="flex items-center justify-between border-b border-slate-800 pb-4">
        <div>
          <span class="text-xs text-slate-400 uppercase tracking-wider">{{ t('adminTickets.ticketInfoTitle') }}</span>
          <h3 class="text-lg font-bold text-white mt-0.5">{{ verifyResult.movieTitle || '—' }}</h3>
        </div>

        <Badge :variant="verifyResult.checkInEligible ? 'success' : 'danger'" size="md">
          {{ verifyResult.checkInEligible ? t('adminTickets.ticketValid') : (verifyResult.ineligibleReason || t('adminTickets.ticketCancelled')) }}
        </Badge>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 text-xs">
        <div class="bg-slate-850 p-3 rounded-lg border border-slate-800">
          <span class="text-slate-400">{{ t('booking.cinemaInfo') }}</span>
          <p class="font-bold text-slate-200 mt-1">{{ verifyResult.cinemaName }}</p>
          <p class="text-slate-400 text-[11px]">{{ verifyResult.auditoriumName }}</p>
        </div>

        <div class="bg-slate-850 p-3 rounded-lg border border-slate-800">
          <span class="text-slate-400">{{ t('booking.showtimeInfo') }}</span>
          <p class="font-bold text-slate-200 mt-1 font-mono">
            {{ formatDateTime(verifyResult.startTime) }}
          </p>
        </div>

        <div class="bg-slate-850 p-3 rounded-lg border border-slate-800">
          <span class="text-slate-400">{{ t('booking.stepSelectSeats') }}</span>
          <p class="font-bold text-indigo-400 mt-1 text-base font-mono">
            {{ verifyResult.seatCode }}
          </p>
          <p class="text-slate-400 text-[11px]">{{ verifyResult.seatTypeName }}</p>
        </div>

        <div class="bg-slate-850 p-3 rounded-lg border border-slate-800">
          <span class="text-slate-400">{{ t('booking.totalPrice') }}</span>
          <p class="font-bold text-emerald-400 mt-1 text-base font-mono">
            {{ formatCurrency(verifyResult.ticketPrice || 0) }}
          </p>
        </div>
      </div>

      <!-- Action -->
      <div v-if="verifyResult.checkInEligible" class="flex justify-end pt-2">
        <Button
          variant="primary"
          size="lg"
          :loading="isCheckingIn"
          @click="handleConfirmCheckIn"
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
