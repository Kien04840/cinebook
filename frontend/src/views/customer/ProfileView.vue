<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useI18n } from '@/composables/useI18n'
import userService from '@/services/user.service'
import authService from '@/services/auth.service'
import type { UserProfileResponse } from '@/types/auth.types'
import { formatDate } from '@/utils/formatters'
import Card from '@/components/common/Card.vue'
import Badge from '@/components/common/Badge.vue'
import Button from '@/components/common/Button.vue'
import Input from '@/components/common/Input.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'

const authStore = useAuthStore()
const toast = useToast()
const { t } = useI18n()

const activeTab = ref<'profile' | 'password'>('profile')
const isLoading = ref(true)
const isUpdating = ref(false)
const isChangingPassword = ref(false)

const profileData = ref<UserProfileResponse | null>(null)

const profileForm = reactive({
  fullName: '',
  phone: '',
  avatarUrl: '',
})

const profileErrors = reactive({
  fullName: '',
  phone: '',
  avatarUrl: '',
})

const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const passwordErrors = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const showPasswords = ref(false)

const effectiveAvatarUrl = computed(() => {
  return profileForm.avatarUrl.trim() || profileData.value?.avatarUrl || ''
})

async function loadProfile() {
  isLoading.value = true
  try {
    const data = await userService.getProfile()
    profileData.value = data
    profileForm.fullName = data.fullName || ''
    profileForm.phone = data.phone || ''
    profileForm.avatarUrl = data.avatarUrl || ''
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  } finally {
    isLoading.value = false
  }
}

function validateProfile(): boolean {
  let valid = true
  profileErrors.fullName = ''
  profileErrors.phone = ''
  profileErrors.avatarUrl = ''

  if (!profileForm.fullName.trim()) {
    profileErrors.fullName = t('auth.errFullNameRequired')
    valid = false
  } else if (profileForm.fullName.trim().length > 100) {
    profileErrors.fullName = t('auth.errFullNameTooLong')
    valid = false
  }

  if (profileForm.phone.trim()) {
    const phoneRegex = /^0\d{9}$/
    if (!phoneRegex.test(profileForm.phone.trim())) {
      profileErrors.phone = t('auth.errPhoneInvalid')
      valid = false
    }
  }

  return valid
}

async function handleUpdateProfile() {
  if (!validateProfile()) return

  isUpdating.value = true
  try {
    const updated = await authStore.updateProfile({
      fullName: profileForm.fullName.trim(),
      phone: profileForm.phone.trim() || undefined,
      avatarUrl: profileForm.avatarUrl.trim() || undefined,
    })

    profileData.value = updated
    toast.success(t('profile.updateSuccess'))
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  } finally {
    isUpdating.value = false
  }
}

function validatePassword(): boolean {
  let valid = true
  passwordErrors.currentPassword = ''
  passwordErrors.newPassword = ''
  passwordErrors.confirmPassword = ''

  if (!passwordForm.currentPassword) {
    passwordErrors.currentPassword = t('auth.errPasswordRequired')
    valid = false
  }

  if (!passwordForm.newPassword) {
    passwordErrors.newPassword = t('auth.errPasswordRequired')
    valid = false
  } else if (passwordForm.newPassword.length < 6) {
    passwordErrors.newPassword = t('auth.errPasswordMinLength')
    valid = false
  }

  if (!passwordForm.confirmPassword) {
    passwordErrors.confirmPassword = t('auth.errConfirmPasswordRequired')
    valid = false
  } else if (passwordForm.confirmPassword !== passwordForm.newPassword) {
    passwordErrors.confirmPassword = t('auth.errPasswordMismatch')
    valid = false
  }

  return valid
}

async function handleChangePassword() {
  if (!validatePassword()) return

  isChangingPassword.value = true
  try {
    await userService.changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
    })

    passwordForm.currentPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''

    toast.success(t('profile.passwordSuccess'))
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  } finally {
    isChangingPassword.value = false
  }
}

const isResendingVerification = ref(false)
const resendCountdown = ref(0)
let resendInterval: number | null = null

function startResendCountdown(seconds = 60) {
  resendCountdown.value = seconds
  if (resendInterval) clearInterval(resendInterval)
  resendInterval = window.setInterval(() => {
    resendCountdown.value--
    if (resendCountdown.value <= 0) {
      if (resendInterval) clearInterval(resendInterval)
      resendInterval = null
    }
  }, 1000)
}

async function handleResendVerification() {
  if (!profileData.value?.email) return
  isResendingVerification.value = true
  try {
    const res = await authService.resendVerification(profileData.value.email)
    toast.success(res.message || t('auth.resendSuccessToast'))
    startResendCountdown(60)
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  } finally {
    isResendingVerification.value = false
  }
}

onMounted(() => {
  loadProfile()
})

onUnmounted(() => {
  if (resendInterval) clearInterval(resendInterval)
})
</script>

<template>
  <div class="max-w-4xl mx-auto space-y-6">
    <!-- Header -->
    <div>
      <h1 class="text-2xl font-bold text-white tracking-tight">{{ t('profile.title') }}</h1>
      <p class="text-xs sm:text-sm text-slate-400 mt-1">
        {{ t('profile.subtitle') }}
      </p>
    </div>

    <!-- Unverified Email Banner -->
    <transition name="fade-fast">
      <div
        v-if="profileData && !profileData.emailVerified && !isLoading"
        class="p-4 rounded-xl bg-amber-500/10 border border-amber-500/30 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 shadow-lg shadow-amber-950/10"
      >
        <div class="flex items-start gap-3">
          <div class="w-8 h-8 rounded-lg bg-amber-500/20 text-amber-400 flex items-center justify-center shrink-0 mt-0.5">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>
          <div>
            <h3 class="text-sm font-semibold text-amber-200">{{ t('profile.unverifiedEmailTitle') }}</h3>
            <p class="text-xs text-amber-300/80 mt-0.5">{{ t('profile.unverifiedEmailDesc') }}</p>
          </div>
        </div>
        <Button
          variant="secondary"
          size="sm"
          class="shrink-0 text-xs border-amber-500/40 text-amber-300 hover:bg-amber-500/20"
          :loading="isResendingVerification"
          :disabled="resendCountdown > 0"
          @click="handleResendVerification"
        >
          {{ resendCountdown > 0 ? `${t('auth.resendIn')} (${resendCountdown}s)` : t('auth.resendEmailBtn') }}
        </Button>
      </div>
    </transition>

    <!-- Content / Loading with Fade Transition -->
    <transition name="fade-fast" mode="out-in">
      <!-- Loading State -->
      <div v-if="isLoading" key="loading" class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card padding="md" class="space-y-4">
          <div class="w-20 h-20 rounded-full bg-slate-800 animate-shimmer mx-auto"></div>
          <div class="h-4 w-32 bg-slate-800 animate-shimmer mx-auto rounded"></div>
          <div class="h-3 w-40 bg-slate-800 animate-shimmer mx-auto rounded"></div>
        </Card>
        <Card padding="md" class="lg:col-span-2 space-y-4">
          <div class="h-6 w-48 bg-slate-800 animate-shimmer rounded"></div>
          <div class="h-10 w-full bg-slate-800 animate-shimmer rounded"></div>
          <div class="h-10 w-full bg-slate-800 animate-shimmer rounded"></div>
        </Card>
      </div>

      <div v-else key="content" class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Left Card: User Overview -->
        <Card padding="md" class="space-y-6 text-center h-fit">
          <div class="flex flex-col items-center">
            <UserAvatar
              :src="effectiveAvatarUrl"
              :name="profileData?.fullName"
              size="xl"
              bordered
            />

            <h2 class="text-lg font-bold text-white mt-3">{{ profileData?.fullName }}</h2>
            <p class="text-xs text-slate-400 font-mono mt-0.5">{{ profileData?.email }}</p>

            <div class="flex flex-wrap justify-center gap-1.5 mt-3">
              <span
                v-for="role in profileData?.roles"
                :key="role"
                :class="[
                  'px-2.5 py-0.5 rounded text-[11px] font-semibold uppercase tracking-wider',
                  role === 'ADMIN'
                    ? 'bg-rose-500/20 text-rose-300 border border-rose-500/30'
                    : 'bg-indigo-500/20 text-indigo-300 border border-indigo-500/30',
                ]"
              >
                {{ role }}
              </span>
            </div>
          </div>

          <div class="border-t border-slate-800 pt-4 space-y-2 text-xs text-left">
            <div class="flex justify-between text-slate-400">
              <span>{{ t('profile.accountStatus') }}:</span>
              <Badge :variant="profileData?.status === 'ACTIVE' ? 'success' : 'danger'">
                {{ profileData?.status === 'ACTIVE' ? t('status.ACTIVE') : profileData?.status }}
              </Badge>
            </div>
            <div class="flex justify-between text-slate-400">
              <span>{{ t('profile.emailStatus') }}:</span>
              <Badge :variant="profileData?.emailVerified ? 'success' : 'warning'">
                {{ profileData?.emailVerified ? t('profile.emailVerified') : t('profile.emailUnverified') }}
              </Badge>
            </div>
            <div class="flex justify-between text-slate-400">
              <span>{{ t('profile.memberSince') }}:</span>
              <span class="text-slate-200 font-mono">{{ formatDate(profileData?.createdAt) }}</span>
            </div>
          </div>
        </Card>

        <!-- Right Card: Form Tabs -->
        <Card padding="md" class="lg:col-span-2 space-y-6">
          <!-- Tab Switcher -->
          <div class="flex border-b border-slate-800 pb-2 gap-4">
            <button
              :class="[
                'text-sm font-semibold pb-2 border-b-2 transition-colors touch-manipulation active:scale-95',
                activeTab === 'profile'
                  ? 'border-indigo-500 text-indigo-400'
                  : 'border-transparent text-slate-400 hover:text-slate-200',
              ]"
              @click="activeTab = 'profile'"
            >
              {{ t('profile.tabProfile') }}
            </button>
            <button
              :class="[
                'text-sm font-semibold pb-2 border-b-2 transition-colors touch-manipulation active:scale-95',
                activeTab === 'password'
                  ? 'border-indigo-500 text-indigo-400'
                  : 'border-transparent text-slate-400 hover:text-slate-200',
              ]"
              @click="activeTab = 'password'"
            >
              {{ t('profile.tabPassword') }}
            </button>
          </div>

          <!-- Tab Content with Transition -->
          <transition name="fade-fast" mode="out-in">
            <!-- Profile Form -->
            <form v-if="activeTab === 'profile'" key="tab-profile" class="space-y-4" @submit.prevent="handleUpdateProfile">
              <Input
                v-model="profileForm.fullName"
                :label="t('auth.fullNameLabel')"
                :error="profileErrors.fullName"
                required
              />

          <Input
            v-model="profileForm.phone"
            :label="t('auth.phoneLabel')"
            :placeholder="t('auth.phonePlaceholder')"
            :error="profileErrors.phone"
          />

          <Input
            v-model="profileForm.avatarUrl"
            :label="t('profile.avatarUrlLabel')"
            :placeholder="t('profile.avatarUrlPlaceholder')"
            :error="profileErrors.avatarUrl"
          />

          <div class="flex justify-end pt-2">
            <Button
              type="submit"
              variant="primary"
              size="md"
              :loading="isUpdating"
            >
              {{ t('profile.saveProfileBtn') }}
            </Button>
          </div>
        </form>

        <!-- Password Form -->
        <form v-else key="tab-password" class="space-y-4" @submit.prevent="handleChangePassword">
          <Input
            v-model="passwordForm.currentPassword"
            :type="showPasswords ? 'text' : 'password'"
            :label="t('profile.currentPasswordLabel')"
            :error="passwordErrors.currentPassword"
            required
          />

          <Input
            v-model="passwordForm.newPassword"
            :type="showPasswords ? 'text' : 'password'"
            :label="t('profile.newPasswordLabel')"
            :error="passwordErrors.newPassword"
            required
          />

          <Input
            v-model="passwordForm.confirmPassword"
            :type="showPasswords ? 'text' : 'password'"
            :label="t('profile.confirmNewPasswordLabel')"
            :error="passwordErrors.confirmPassword"
            required
          />

          <div class="flex items-center justify-between pt-2">
            <label class="flex items-center gap-2 text-xs text-slate-400 cursor-pointer select-none">
              <input
                v-model="showPasswords"
                type="checkbox"
                class="rounded bg-slate-900 border-slate-700 text-indigo-600 focus:ring-indigo-500"
              />
              <span>{{ showPasswords ? t('auth.hidePassword') : t('auth.showPassword') }}</span>
            </label>

            <Button
              type="submit"
              variant="primary"
              size="md"
              :loading="isChangingPassword"
            >
              {{ t('profile.changePasswordBtn') }}
            </Button>
          </div>
        </form>
          </transition>
        </Card>
      </div>
    </transition>
  </div>
</template>
