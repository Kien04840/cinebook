<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useI18n } from '@/composables/useI18n'
import Input from '@/components/common/Input.vue'
import Button from '@/components/common/Button.vue'

const authStore = useAuthStore()
const router = useRouter()
const toast = useToast()
const { t } = useI18n()

const form = reactive({
  fullName: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: '',
})

const errors = reactive({
  fullName: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: '',
})

const showPassword = ref(false)
const errorMessage = ref('')

function validateForm(): boolean {
  let isValid = true
  errors.fullName = ''
  errors.email = ''
  errors.phone = ''
  errors.password = ''
  errors.confirmPassword = ''
  errorMessage.value = ''

  // Full Name: REQUIRED
  if (!form.fullName.trim()) {
    errors.fullName = t('auth.errFullNameRequired')
    isValid = false
  } else if (form.fullName.trim().length > 100) {
    errors.fullName = t('auth.errFullNameTooLong')
    isValid = false
  }

  // Email: REQUIRED
  if (!form.email.trim()) {
    errors.email = t('auth.errEmailRequired')
    isValid = false
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    errors.email = t('auth.errEmailInvalid')
    isValid = false
  }

  // Phone: OPTIONAL (validate format only when non-empty)
  if (form.phone.trim() && !/^0[0-9]{9}$/.test(form.phone.trim())) {
    errors.phone = t('auth.errPhoneInvalid')
    isValid = false
  }

  // Password: REQUIRED
  if (!form.password) {
    errors.password = t('auth.errPasswordRequired')
    isValid = false
  } else if (form.password.length < 6) {
    errors.password = t('auth.errPasswordMinLength')
    isValid = false
  }

  // Confirm Password: REQUIRED
  if (!form.confirmPassword) {
    errors.confirmPassword = t('auth.errConfirmPasswordRequired')
    isValid = false
  } else if (form.password !== form.confirmPassword) {
    errors.confirmPassword = t('auth.errPasswordMismatch')
    isValid = false
  }

  return isValid
}

async function handleRegister() {
  if (!validateForm()) return

  errorMessage.value = ''
  try {
    const data = await authStore.register({
      fullName: form.fullName.trim(),
      email: form.email.trim(),
      password: form.password,
      phone: form.phone.trim() || undefined,
    })

    toast.success(
      t('auth.successRegisterMsg', { name: data.user.fullName || data.user.email }),
      t('auth.successRegister')
    )
    router.push('/')
  } catch (err: any) {
    const backendMessage = err.response?.data?.message
    const details = err.response?.data?.details

    if (details && Array.isArray(details)) {
      details.forEach((d: { field: string; message: string }) => {
        if (d.field === 'email') errors.email = d.message
        if (d.field === 'fullName') errors.fullName = d.message
        if (d.field === 'phone') errors.phone = d.message
        if (d.field === 'password') errors.password = d.message
      })
    }

    if (err.response?.status === 409) {
      if (backendMessage && backendMessage.toLowerCase().includes('phone')) {
        errors.phone = t('auth.errPhoneConflict')
        errorMessage.value = t('auth.errPhoneConflict')
      } else {
        errors.email = t('auth.errEmailConflict')
        errorMessage.value = t('auth.errEmailConflict')
      }
    } else {
      errorMessage.value = backendMessage || t('auth.errRegisterFailed')
    }
  }
}
</script>

<template>
  <div class="space-y-6">
    <div class="text-center space-y-1">
      <h2 class="text-2xl font-bold text-white tracking-tight">{{ t('auth.registerTitle') }}</h2>
      <p class="text-xs text-slate-400">{{ t('auth.registerSubtitle') }}</p>
    </div>

    <!-- Error Alert Banner -->
    <div
      v-if="errorMessage"
      class="p-3.5 rounded-xl bg-rose-950/60 border border-rose-800 text-xs text-rose-200 flex items-start gap-2.5"
      role="alert"
    >
      <svg class="w-4 h-4 text-rose-400 shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>{{ errorMessage }}</span>
    </div>

    <form class="space-y-3.5" @submit.prevent="handleRegister">
      <!-- Full Name (REQUIRED *) -->
      <Input
        v-model="form.fullName"
        :label="`${t('auth.fullNameLabel')} *`"
        :placeholder="t('auth.fullNameLabel')"
        :error="errors.fullName"
        required
      >
        <template #prefix>
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
          </svg>
        </template>
      </Input>

      <!-- Email (REQUIRED *) -->
      <Input
        v-model="form.email"
        :label="`${t('auth.emailLabel')} *`"
        type="email"
        placeholder="name@example.com"
        :error="errors.email"
        required
      >
        <template #prefix>
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 12a4 4 0 10-8 0 4 4 0 008 0zm0 0v1.5a2.5 2.5 0 005 0V12a9 9 0 10-9 9m4.5-1.206a8.959 8.959 0 01-4.5 1.207" />
          </svg>
        </template>
      </Input>

      <!-- Phone Number (OPTIONAL) -->
      <Input
        v-model="form.phone"
        :label="t('auth.phoneLabel')"
        type="tel"
        :placeholder="t('auth.phonePlaceholder')"
        :error="errors.phone"
      >
        <template #prefix>
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" />
          </svg>
        </template>
      </Input>

      <!-- Password (REQUIRED *) -->
      <Input
        v-model="form.password"
        :label="`${t('auth.passwordLabel')} *`"
        :type="showPassword ? 'text' : 'password'"
        placeholder="••••••••"
        :error="errors.password"
        required
      >
        <template #prefix>
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
          </svg>
        </template>

        <template #suffix>
          <button
            type="button"
            class="pointer-events-auto p-1 text-slate-400 hover:text-slate-200 transition-colors focus:outline-none"
            :aria-label="showPassword ? t('auth.hidePassword') : t('auth.showPassword')"
            @click="showPassword = !showPassword"
          >
            <svg v-if="!showPassword" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
            </svg>
            <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l18 18" />
            </svg>
          </button>
        </template>
      </Input>

      <!-- Confirm Password (REQUIRED *) -->
      <Input
        v-model="form.confirmPassword"
        :label="`${t('auth.confirmPasswordLabel')} *`"
        :type="showPassword ? 'text' : 'password'"
        placeholder="••••••••"
        :error="errors.confirmPassword"
        required
      >
        <template #prefix>
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
          </svg>
        </template>
      </Input>

      <div class="pt-2">
        <Button
          type="submit"
          variant="primary"
          size="md"
          block
          :loading="authStore.isLoading"
        >
          {{ t('auth.registerBtn') }}
        </Button>
      </div>
    </form>

    <div class="text-center text-xs text-slate-400">
      {{ t('auth.haveAccount') }}
      <router-link to="/login" class="text-indigo-400 font-semibold hover:text-indigo-300 hover:underline ml-1">
        {{ t('auth.loginNow') }}
      </router-link>
    </div>
  </div>
</template>
