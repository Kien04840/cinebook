<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useI18n } from '@/composables/useI18n'
import Input from '@/components/common/Input.vue'
import Button from '@/components/common/Button.vue'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const toast = useToast()
const { t } = useI18n()

const form = reactive({
  email: '',
  password: '',
})

const errors = reactive({
  email: '',
  password: '',
})

const showPassword = ref(false)
const errorMessage = ref('')

function validateForm(): boolean {
  let isValid = true
  errors.email = ''
  errors.password = ''
  errorMessage.value = ''

  if (!form.email.trim()) {
    errors.email = t('auth.errEmailRequired')
    isValid = false
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    errors.email = t('auth.errEmailInvalid')
    isValid = false
  }

  if (!form.password) {
    errors.password = t('auth.errPasswordRequired')
    isValid = false
  } else if (form.password.length < 6) {
    errors.password = t('auth.errPasswordMinLength')
    isValid = false
  }

  return isValid
}

async function handleLogin() {
  if (!validateForm()) return

  errorMessage.value = ''
  try {
    const data = await authStore.login({
      email: form.email.trim(),
      password: form.password,
    })

    toast.success(
      t('auth.successLoginMsg', { name: data.user.fullName || data.user.email }),
      t('auth.successLogin')
    )

    const redirectPath = (route.query.redirect as string) || (authStore.isAdmin ? '/admin/dashboard' : '/')
    router.push(redirectPath)
  } catch (err: any) {
    const backendMessage = err.response?.data?.message
    if (err.response?.status === 401 || err.response?.status === 400) {
      errorMessage.value = backendMessage || t('auth.errLoginFailed')
    } else if (err.response?.status === 403) {
      errorMessage.value = backendMessage || t('auth.errAccountLocked')
    } else {
      errorMessage.value = backendMessage || t('auth.errNetwork')
    }
  }
}

function fillCredentials(type: 'admin' | 'customer') {
  if (type === 'admin') {
    form.email = 'admin@cinebook.com'
    form.password = 'Password123@'
  } else {
    form.email = 'customer@cinebook.com'
    form.password = 'Password123@'
  }
  errors.email = ''
  errors.password = ''
  errorMessage.value = ''
}
</script>

<template>
  <div class="space-y-6">
    <div class="text-center space-y-1">
      <h2 class="text-2xl font-bold text-white tracking-tight">{{ t('auth.loginTitle') }}</h2>
      <p class="text-xs text-slate-400">{{ t('auth.loginSubtitle') }}</p>
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

    <form class="space-y-4" @submit.prevent="handleLogin">
      <Input
        v-model="form.email"
        :label="t('auth.emailLabel')"
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

      <div class="space-y-1">
        <Input
          v-model="form.password"
          :label="t('auth.passwordLabel')"
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
      </div>

      <div class="flex items-center justify-between text-xs pt-1">
        <label class="flex items-center gap-2 cursor-pointer text-slate-400 hover:text-slate-300">
          <input
            type="checkbox"
            class="rounded bg-slate-800 border-slate-700 text-indigo-600 focus:ring-indigo-500 w-3.5 h-3.5"
          />
          <span>{{ t('auth.rememberMe') }}</span>
        </label>
        <router-link to="/forgot-password" class="text-indigo-400 hover:text-indigo-300 hover:underline">
          {{ t('auth.forgotPassword') }}
        </router-link>
      </div>

      <div class="pt-2">
        <Button
          type="submit"
          variant="primary"
          size="md"
          block
          :loading="authStore.isLoading"
        >
          {{ t('auth.loginBtn') }}
        </Button>
      </div>
    </form>

    <!-- Quick Demo Logins for Developer/Tester Convenience -->
    <div class="pt-4 border-t border-slate-800/80 space-y-2">
      <p class="text-[11px] font-semibold uppercase tracking-wider text-slate-400 text-center">
        {{ t('auth.demoLogins') }}
      </p>
      <div class="grid grid-cols-2 gap-2">
        <button
          type="button"
          class="px-2.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-750 border border-slate-700 text-[11px] text-slate-300 font-medium transition-colors"
          @click="fillCredentials('admin')"
        >
          👤 Admin Demo
        </button>
        <button
          type="button"
          class="px-2.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-750 border border-slate-700 text-[11px] text-slate-300 font-medium transition-colors"
          @click="fillCredentials('customer')"
        >
          🎟️ Customer Demo
        </button>
      </div>
    </div>

    <div class="text-center text-xs text-slate-400">
      {{ t('auth.noAccount') }}
      <router-link to="/register" class="text-indigo-400 font-semibold hover:text-indigo-300 hover:underline ml-1">
        {{ t('auth.registerNow') }}
      </router-link>
    </div>
  </div>
</template>
