import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User, LoginPayload, RegisterPayload, UpdateProfilePayload } from '@/types/auth.types'
import authService from '@/services/auth.service'
import userService from '@/services/user.service'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const accessToken = ref<string | null>(null)
  const refreshToken = ref<string | null>(null)
  const isLoading = ref<boolean>(false)
  const isInitialized = ref<boolean>(false)

  // Initialize state from localStorage
  if (typeof window !== 'undefined') {
    accessToken.value = localStorage.getItem('cinebook_access_token')
    refreshToken.value = localStorage.getItem('cinebook_refresh_token')

    const storedUser = localStorage.getItem('cinebook_user')
    if (storedUser) {
      try {
        user.value = JSON.parse(storedUser)
      } catch {
        localStorage.removeItem('cinebook_user')
      }
    }
  }

  // Getters
  const isAuthenticated = computed(() => !!accessToken.value && !!user.value)
  const isAdmin = computed(() => {
    if (!user.value?.roles) return false
    return user.value.roles.some((r) => r === 'ADMIN' || r === 'ROLE_ADMIN')
  })
  const isCustomer = computed(() => {
    if (!user.value?.roles) return false
    return user.value.roles.some((r) => r === 'CUSTOMER' || r === 'ROLE_CUSTOMER')
  })
  const userFullName = computed(() => user.value?.fullName || user.value?.email || 'Người dùng')
  const userInitials = computed(() => {
    const name = user.value?.fullName || user.value?.email || 'U'
    return name.charAt(0).toUpperCase()
  })

  // Actions
  async function login(payload: LoginPayload) {
    isLoading.value = true
    try {
      const data = await authService.login(payload)
      setAuthData(data.accessToken, data.refreshToken, data.user)
      return data
    } finally {
      isLoading.value = false
    }
  }

  async function register(payload: RegisterPayload) {
    isLoading.value = true
    try {
      const data = await authService.register(payload)
      setAuthData(data.accessToken, data.refreshToken, data.user)
      return data
    } finally {
      isLoading.value = false
    }
  }

  async function restoreSession(): Promise<boolean> {
    if (isInitialized.value) return isAuthenticated.value
    isInitialized.value = true

    if (!accessToken.value) {
      return false
    }

    try {
      const profile = await userService.getProfile()
      user.value = {
        ...user.value,
        id: profile.id,
        email: profile.email,
        fullName: profile.fullName,
        phone: profile.phone,
        avatarUrl: profile.avatarUrl,
        status: profile.status,
        emailVerified: profile.emailVerified,
        roles: profile.roles,
        createdAt: profile.createdAt,
        updatedAt: profile.updatedAt,
      }
      localStorage.setItem('cinebook_user', JSON.stringify(user.value))
      return true
    } catch (err) {
      // If profile fetch fails (e.g. invalid/expired token), logout
      logout()
      return false
    }
  }

  async function updateProfile(payload: UpdateProfilePayload) {
    isLoading.value = true
    try {
      const updated = await userService.updateProfile(payload)
      if (user.value) {
        user.value.fullName = updated.fullName
        user.value.phone = updated.phone
        user.value.avatarUrl = updated.avatarUrl
        localStorage.setItem('cinebook_user', JSON.stringify(user.value))
      }
      return updated
    } finally {
      isLoading.value = false
    }
  }

  async function logout() {
    const currentRefresh = refreshToken.value
    user.value = null
    accessToken.value = null
    refreshToken.value = null
    localStorage.removeItem('cinebook_access_token')
    localStorage.removeItem('cinebook_refresh_token')
    localStorage.removeItem('cinebook_user')

    if (currentRefresh) {
      await authService.logout(currentRefresh)
    }
  }

  function setAuthData(newAccessToken: string, newRefreshToken: string, newUser: User) {
    accessToken.value = newAccessToken
    refreshToken.value = newRefreshToken
    user.value = newUser

    localStorage.setItem('cinebook_access_token', newAccessToken)
    localStorage.setItem('cinebook_refresh_token', newRefreshToken)
    localStorage.setItem('cinebook_user', JSON.stringify(newUser))
  }

  // Listen for auth expired event from axios interceptor
  if (typeof window !== 'undefined') {
    window.addEventListener('auth:expired', () => {
      logout()
    })
  }

  return {
    user,
    accessToken,
    refreshToken,
    isLoading,
    isInitialized,
    isAuthenticated,
    isAdmin,
    isCustomer,
    userFullName,
    userInitials,
    login,
    register,
    restoreSession,
    updateProfile,
    logout,
    setAuthData,
  }
})
