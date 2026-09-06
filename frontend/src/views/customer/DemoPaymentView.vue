<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import paymentService from '@/services/payment.service'
import { formatCurrency } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import { useToast } from '@/composables/useToast'
import Button from '@/components/common/Button.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import Spinner from '@/components/common/Spinner.vue'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const { t } = useI18n()

const isSubmitting = ref<boolean>(false)
const selectedCode = ref<string>('')
const errorMessage = ref<string>('')

// Parse query params from mock payment URL
const paymentCode = computed(() => (route.query.vnp_TxnRef as string) || '')
const bookingCode = computed(() => (route.query.bookingCode as string) || '')
const orderInfo = computed(() => (route.query.vnp_OrderInfo as string) || '')
const rawAmount = computed(() => {
  const val = Number(route.query.vnp_Amount)
  return isNaN(val) ? 0 : val / 100
})
const bankCode = computed(() => (route.query.vnp_BankCode as string) || 'NCB')
const rawExpireDate = computed(() => (route.query.vnp_ExpireDate as string) || '')

// Seat hold duration is strictly 5 minutes (300s) according to CineBook business rules
const remainingSeconds = ref<number>(300)
const isHoldExpired = computed(() => remainingSeconds.value <= 0)
let timerInterval: any = null

function parseVnPayExpireDate(dateStr: string): number | null {
  if (!dateStr || dateStr.length !== 14) return null
  const y = parseInt(dateStr.substring(0, 4), 10)
  const m = parseInt(dateStr.substring(4, 6), 10) - 1
  const d = parseInt(dateStr.substring(6, 8), 10)
  const h = parseInt(dateStr.substring(8, 10), 10)
  const min = parseInt(dateStr.substring(10, 12), 10)
  const s = parseInt(dateStr.substring(12, 14), 10)
  return new Date(y, m, d, h, min, s).getTime()
}

function updateCountdown() {
  const expireTimestamp = parseVnPayExpireDate(rawExpireDate.value)
  if (expireTimestamp) {
    const diff = Math.max(0, Math.floor((expireTimestamp - Date.now()) / 1000))
    // Seat hold cannot exceed 5 minutes (300 seconds)
    remainingSeconds.value = Math.min(diff, 300)
  } else {
    if (remainingSeconds.value > 0) {
      remainingSeconds.value -= 1
    }
  }

  if (remainingSeconds.value <= 0 && timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
}

const formattedCountdown = computed(() => {
  const m = Math.floor(remainingSeconds.value / 60)
  const s = remainingSeconds.value % 60
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
})

async function handleComplete(responseCode: '00' | '07' | '24') {
  if (isHoldExpired.value) {
    errorMessage.value = t('demoPayment.holdExpiredNotice') || 'Đơn đặt vé đã hết thời gian giữ chỗ (5 phút). Vui lòng đặt lại vé.'
    toast.error(t('common.errorTitle'), errorMessage.value)
    return
  }

  if (!paymentCode.value) {
    errorMessage.value = t('demoPayment.missingParams')
    return
  }

  isSubmitting.value = true
  selectedCode.value = responseCode
  errorMessage.value = ''

  try {
    const res = await paymentService.completeDemoPayment({
      paymentCode: paymentCode.value,
      responseCode,
    })

    if (responseCode === '00') {
      toast.success(t('common.successTitle'), res.message || 'Thanh toán thành công!')
    } else if (responseCode === '24') {
      toast.info(t('common.noticeTitle'), res.message || 'Giao dịch đã hủy.')
    } else {
      toast.error(t('common.errorTitle'), res.message || 'Giao dịch thất bại.')
    }

    // Redirect to the signed result URL provided by backend
    if (res.redirectUrl) {
      if (res.redirectUrl.startsWith('/')) {
        await router.push(res.redirectUrl)
      } else {
        window.location.href = res.redirectUrl
      }
    }
  } catch (err: any) {
    errorMessage.value =
      err.response?.data?.message || 'Không thể xử lý mô phỏng thanh toán. Vui lòng thử lại.'
    toast.error(t('common.errorTitle'), errorMessage.value)
    isSubmitting.value = false
    selectedCode.value = ''
  }
}

onMounted(() => {
  if (!paymentCode.value) {
    errorMessage.value = t('demoPayment.missingParams')
  }

  updateCountdown()
  timerInterval = setInterval(updateCountdown, 1000)
})

onUnmounted(() => {
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
})
</script>

<template>
  <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-10 sm:py-14 space-y-8">
    <!-- 1. Top Demo Alert Banner -->
    <div class="p-4 sm:p-5 rounded-2xl bg-amber-500/10 border border-amber-500/30 text-amber-300 flex items-start gap-4">
      <div class="p-2 rounded-xl bg-amber-500/20 text-amber-400 shrink-0">
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
      </div>
      <div class="space-y-1 text-sm">
        <div class="font-bold flex items-center gap-2">
          <span>{{ t('demoPayment.badge') }}</span>
          <span class="text-xs px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/30">Sandbox Mock</span>
        </div>
        <p class="text-amber-200/80 leading-relaxed">
          {{ t('demoPayment.warningNotice') }}
        </p>
      </div>
    </div>

    <!-- Hold Expired Alert Banner -->
    <div v-if="isHoldExpired" class="p-5 rounded-2xl bg-rose-500/10 border border-rose-500/40 text-rose-300 space-y-3">
      <div class="flex items-center gap-3 font-bold text-base">
        <svg class="w-5 h-5 text-rose-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span>{{ t('demoPayment.holdExpiredNotice') }}</span>
      </div>
      <p class="text-xs text-rose-200/80 leading-relaxed">
        Theo quy định của CineBook, ghế chỉ được giữ trong vòng 5 phút. Vui lòng quay lại danh sách suất chiếu để chọn lại ghế mới.
      </p>
      <div class="pt-2 flex items-center gap-3">
        <router-link to="/movies">
          <Button variant="secondary" size="sm">{{ t('demoPayment.reselectSeatsBtn') }}</Button>
        </router-link>
        <router-link to="/my-bookings">
          <Button variant="secondary" size="sm">{{ t('demoPayment.backToBookings') }}</Button>
        </router-link>
      </div>
    </div>

    <!-- Error Alert if present -->
    <div v-if="errorMessage && !isHoldExpired" class="space-y-4">
      <ErrorAlert :message="errorMessage" />
      <div class="flex justify-center">
        <router-link to="/my-bookings">
          <Button variant="secondary" size="md">{{ t('demoPayment.backToBookings') }}</Button>
        </router-link>
      </div>
    </div>

    <!-- Main Payment Container -->
    <div v-else class="space-y-8">
      <!-- Header -->
      <div class="text-center space-y-2">
        <h1 class="text-2xl sm:text-3xl font-black text-white tracking-tight">
          {{ t('demoPayment.title') }}
        </h1>
        <p class="text-slate-400 text-sm max-w-lg mx-auto">
          {{ t('demoPayment.subtitle') }}
        </p>
      </div>

      <!-- Order Details Card -->
      <div class="p-6 rounded-3xl bg-slate-900/90 border border-slate-800 shadow-xl space-y-6">
        <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pb-5 border-b border-slate-800/80">
          <div class="space-y-1">
            <span class="text-xs font-semibold text-slate-400 uppercase tracking-wider">{{ t('demoPayment.orderInfo') }}</span>
            <div class="text-lg font-bold text-white flex items-center gap-2">
              <span>{{ bookingCode || paymentCode }}</span>
            </div>
          </div>
          <div
            class="flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-semibold transition-colors"
            :class="isHoldExpired ? 'bg-rose-500/20 border border-rose-500/40 text-rose-300' : 'bg-rose-500/10 border border-rose-500/30 text-rose-400'"
          >
            <svg class="w-4 h-4" :class="{ 'animate-spin-slow': !isHoldExpired }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>{{ isHoldExpired ? 'Đã hết hạn giữ chỗ' : `${t('demoPayment.holdTimeRemaining')}: ${formattedCountdown}` }}</span>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
          <div class="p-4 rounded-2xl bg-slate-800/50 border border-slate-700/50 space-y-1">
            <span class="text-xs text-slate-400">{{ t('demoPayment.paymentCode') }}</span>
            <p class="font-mono font-medium text-slate-200 break-all">{{ paymentCode }}</p>
          </div>
          <div class="p-4 rounded-2xl bg-slate-800/50 border border-slate-700/50 space-y-1">
            <span class="text-xs text-slate-400">{{ t('demoPayment.amount') }}</span>
            <p class="text-xl font-black text-emerald-400">{{ formatCurrency(rawAmount) }}</p>
          </div>
          <div class="p-4 rounded-2xl bg-slate-800/50 border border-slate-700/50 space-y-1">
            <span class="text-xs text-slate-400">{{ t('demoPayment.simulatedBank') }}</span>
            <p class="font-medium text-slate-200">{{ bankCode }} — {{ t('demoPayment.bankName') }}</p>
          </div>
          <div class="p-4 rounded-2xl bg-slate-800/50 border border-slate-700/50 space-y-1">
            <span class="text-xs text-slate-400">{{ t('demoPayment.description') }}</span>
            <p class="font-medium text-slate-300 truncate">{{ orderInfo || 'Thanh toan ve xem phim' }}</p>
          </div>
        </div>
      </div>

      <!-- Simulation Action Cards -->
      <div class="space-y-4">
        <h2 class="text-sm font-bold uppercase tracking-wider text-slate-400 px-1">
          {{ t('demoPayment.selectAction') }}
        </h2>

        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <!-- 1. SUCCESS (Code 00) -->
          <div class="p-5 rounded-2xl bg-emerald-950/40 border border-emerald-500/40 hover:border-emerald-500 transition-all space-y-4 flex flex-col justify-between group">
            <div class="space-y-2">
              <div class="flex items-center gap-2.5">
                <div class="w-8 h-8 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold text-sm">
                  ✓
                </div>
                <h3 class="font-bold text-emerald-300 text-base">
                  {{ t('demoPayment.btnSuccess') }}
                </h3>
              </div>
              <p class="text-xs text-emerald-200/70 leading-relaxed">
                {{ t('demoPayment.btnSuccessDesc') }}
              </p>
            </div>
            <Button
              variant="primary"
              size="md"
              class="w-full !bg-emerald-600 hover:!bg-emerald-500 !border-emerald-500 !text-white shadow-lg shadow-emerald-950/50"
              :disabled="isSubmitting || isHoldExpired"
              @click="handleComplete('00')"
            >
              <Spinner v-if="isSubmitting && selectedCode === '00'" size="sm" class="mr-2" />
              <span>{{ isSubmitting && selectedCode === '00' ? t('demoPayment.processing') : t('demoPayment.btnSuccess') }}</span>
            </Button>
          </div>

          <!-- 2. CANCEL (Code 24) -->
          <div class="p-5 rounded-2xl bg-amber-950/40 border border-amber-500/40 hover:border-amber-500 transition-all space-y-4 flex flex-col justify-between group">
            <div class="space-y-2">
              <div class="flex items-center gap-2.5">
                <div class="w-8 h-8 rounded-full bg-amber-500/20 text-amber-400 flex items-center justify-center font-bold text-sm">
                  ✕
                </div>
                <h3 class="font-bold text-amber-300 text-base">
                  {{ t('demoPayment.btnCancel') }}
                </h3>
              </div>
              <p class="text-xs text-amber-200/70 leading-relaxed">
                {{ t('demoPayment.btnCancelDesc') }}
              </p>
            </div>
            <Button
              variant="secondary"
              size="md"
              class="w-full !border-amber-500/50 !text-amber-300 hover:!bg-amber-500/20 shadow-lg shadow-amber-950/50"
              :disabled="isSubmitting || isHoldExpired"
              @click="handleComplete('24')"
            >
              <Spinner v-if="isSubmitting && selectedCode === '24'" size="sm" class="mr-2" />
              <span>{{ isSubmitting && selectedCode === '24' ? t('demoPayment.processing') : t('demoPayment.btnCancel') }}</span>
            </Button>
          </div>

          <!-- 3. FAILURE (Code 07) -->
          <div class="p-5 rounded-2xl bg-rose-950/40 border border-rose-500/40 hover:border-rose-500 transition-all space-y-4 flex flex-col justify-between group">
            <div class="space-y-2">
              <div class="flex items-center gap-2.5">
                <div class="w-8 h-8 rounded-full bg-rose-500/20 text-rose-400 flex items-center justify-center font-bold text-sm">
                  !
                </div>
                <h3 class="font-bold text-rose-300 text-base">
                  {{ t('demoPayment.btnFailure') }}
                </h3>
              </div>
              <p class="text-xs text-rose-200/70 leading-relaxed">
                {{ t('demoPayment.btnFailureDesc') }}
              </p>
            </div>
            <Button
              variant="danger"
              size="md"
              class="w-full !bg-rose-600 hover:!bg-rose-500 !text-white shadow-lg shadow-rose-950/50"
              :disabled="isSubmitting || isHoldExpired"
              @click="handleComplete('07')"
            >
              <Spinner v-if="isSubmitting && selectedCode === '07'" size="sm" class="mr-2" />
              <span>{{ isSubmitting && selectedCode === '07' ? t('demoPayment.processing') : t('demoPayment.btnFailure') }}</span>
            </Button>
          </div>
        </div>
      </div>

      <!-- Navigation Links -->
      <div class="flex items-center justify-between pt-4 border-t border-slate-800 text-sm">
        <router-link to="/my-bookings" class="text-slate-400 hover:text-white transition-colors flex items-center gap-2">
          <span>← {{ t('demoPayment.backToBookings') }}</span>
        </router-link>
        <div class="text-xs text-slate-500">
          CineBook Monolith Demo System • Gate: MOCK
        </div>
      </div>
    </div>
  </div>
</template>
