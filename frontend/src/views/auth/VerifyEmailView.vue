<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useI18n } from '@/composables/useI18n'
import authService from '@/services/auth.service'
import Button from '@/components/common/Button.vue'
import Input from '@/components/common/Input.vue'

type VerifyState = 'loading' | 'success' | 'error' | 'no_token'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toast = useToast()
const { t } = useI18n()

const state = ref<VerifyState>('loading')
const errorMessage = ref('')
const isResending = ref(false)
const resendCountdown = ref(0)
let countdownInterval: number | null = null

const resendForm = reactive({
  email: authStore.user?.email || '',
})
const resendError = ref('')

const token = computed(() => {
  const qToken = route.query.token
  return typeof qToken === 'string' ? qToken.trim() : ''
})

async function executeVerification() {
  if (!token.value) {
    state.value = 'no_token'
    return
  }

  state.value = 'loading'
  errorMessage.value = ''

  try {
    const res = await authService.verifyEmail(token.value)
    state.value = 'success'
    if (authStore.user) {
      authStore.user.emailVerified = true
    }
    toast.success(res.message || t('auth.verifySuccessToast'))
  } catch (err: any) {
    state.value = 'error'
    errorMessage.value = err.response?.data?.message || t('auth.verifyFailed')
  }
}

function startCountdown(seconds = 60) {
  resendCountdown.value = seconds
  if (countdownInterval) clearInterval(countdownInterval)
  countdownInterval = window.setInterval(() => {
    resendCountdown.value--
    if (resendCountdown.value <= 0) {
      if (countdownInterval) clearInterval(countdownInterval)
      countdownInterval = null
    }
  }, 1000)
}

async function handleResend() {
  resendError.value = ''
  if (!resendForm.email.trim()) {
    resendError.value = t('auth.errEmailRequired')
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(resendForm.email.trim())) {
    resendError.value = t('auth.errEmailInvalid')
    return
  }

  isResending.value = true
  try {
    const res = await authService.resendVerification(resendForm.email.trim())
    toast.success(res.message || t('auth.resendSuccessToast'))
    startCountdown(60)
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  } finally {
    isResending.value = false
  }
}

onMounted(() => {
  executeVerification()
})
</script>

<template>
  <div class="space-y-6">
    <!-- State 1: Verifying Token -->
    <div v-if="state === 'loading'" class="text-center py-8 space-y-4">
      <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400">
        <svg class="w-8 h-8 animate-spin" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
      </div>
      <h2 class="text-xl font-bold text-white">{{ t('auth.verifyingTitle') }}</h2>
      <p class="text-xs sm:text-sm text-slate-400 max-w-sm mx-auto">{{ t('auth.verifyingDesc') }}</p>
    </div>

    <!-- State 2: Verification Success -->
    <div v-else-if="state === 'success'" class="text-center py-8 space-y-5">
      <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400">
        <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
        </svg>
      </div>
      <div class="space-y-1.5">
        <h2 class="text-2xl font-bold text-white tracking-tight">{{ t('auth.verifySuccessTitle') }}</h2>
        <p class="text-xs sm:text-sm text-slate-400 max-w-sm mx-auto">
          {{ t('auth.verifySuccessDesc') }}
        </p>
      </div>

      <div class="pt-4 flex flex-col sm:flex-row justify-center gap-3">
        <Button
          v-if="!authStore.isAuthenticated"
          variant="primary"
          @click="router.push('/login')"
        >
          {{ t('auth.loginNow') }}
        </Button>
        <Button
          v-else
          variant="primary"
          @click="router.push('/profile')"
        >
          {{ t('auth.goToProfile') }}
        </Button>
        <Button
          variant="secondary"
          @click="router.push('/')"
        >
          {{ t('auth.goToHome') }}
        </Button>
      </div>
    </div>

    <!-- State 3: Verification Error (Expired / Invalid) -->
    <div v-else-if="state === 'error'" class="space-y-6">
      <div class="text-center space-y-3">
        <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-rose-500/10 border border-rose-500/20 text-rose-400 mx-auto">
          <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
        </div>
        <h2 class="text-xl font-bold text-white">{{ t('auth.verifyFailedTitle') }}</h2>
        <p class="text-xs sm:text-sm text-rose-300/90 max-w-sm mx-auto">
          {{ errorMessage }}
        </p>
      </div>

      <!-- Resend verification form box -->
      <div class="p-5 rounded-2xl bg-slate-900/60 border border-slate-800 space-y-4">
        <div class="space-y-1">
          <h3 class="text-sm font-semibold text-slate-200">{{ t('auth.resendSectionTitle') }}</h3>
          <p class="text-xs text-slate-400">{{ t('auth.resendSectionDesc') }}</p>
        </div>

        <form class="space-y-3" @submit.prevent="handleResend">
          <Input
            v-model="resendForm.email"
            type="email"
            :label="t('auth.emailLabel')"
            placeholder="name@example.com"
            :error="resendError"
            required
          />

          <Button
            type="submit"
            variant="primary"
            class="w-full"
            :loading="isResending"
            :disabled="resendCountdown > 0"
          >
            {{ resendCountdown > 0 ? `${t('auth.resendIn')} (${resendCountdown}s)` : t('auth.resendEmailBtn') }}
          </Button>
        </form>
      </div>

      <div class="text-center">
        <router-link to="/" class="text-xs text-slate-400 hover:text-slate-200 transition-colors">
          &larr; {{ t('auth.backToHome') }}
        </router-link>
      </div>
    </div>

    <!-- State 4: No Token in URL -->
    <div v-else class="space-y-6">
      <div class="text-center space-y-2">
        <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-amber-500/10 border border-amber-500/20 text-amber-400 mx-auto">
          <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
          </svg>
        </div>
        <h2 class="text-xl font-bold text-white">{{ t('auth.verifyEmailPromptTitle') }}</h2>
        <p class="text-xs sm:text-sm text-slate-400 max-w-sm mx-auto">
          {{ t('auth.verifyEmailPromptDesc') }}
        </p>
      </div>

      <!-- Resend Form -->
      <div class="p-5 rounded-2xl bg-slate-900/60 border border-slate-800 space-y-4">
        <div class="space-y-1">
          <h3 class="text-sm font-semibold text-slate-200">{{ t('auth.resendSectionTitle') }}</h3>
          <p class="text-xs text-slate-400">{{ t('auth.resendSectionDesc') }}</p>
        </div>

        <form class="space-y-3" @submit.prevent="handleResend">
          <Input
            v-model="resendForm.email"
            type="email"
            :label="t('auth.emailLabel')"
            placeholder="name@example.com"
            :error="resendError"
            required
          />

          <Button
            type="submit"
            variant="primary"
            class="w-full"
            :loading="isResending"
            :disabled="resendCountdown > 0"
          >
            {{ resendCountdown > 0 ? `${t('auth.resendIn')} (${resendCountdown}s)` : t('auth.resendEmailBtn') }}
          </Button>
        </form>
      </div>

      <div class="text-center">
        <router-link to="/" class="text-xs text-slate-400 hover:text-slate-200 transition-colors">
          &larr; {{ t('auth.backToHome') }}
        </router-link>
      </div>
    </div>
  </div>
</template>

