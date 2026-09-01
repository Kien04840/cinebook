<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'
import { useI18n } from '@/composables/useI18n'
import Button from '@/components/common/Button.vue'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const toast = useToast()
const { t, locale, setLocale } = useI18n()

const isMobileMenuOpen = ref(false)
const isProfileDropdownOpen = ref(false)
const avatarLoadError = ref(false)

const navLinks = computed(() => [
  { name: t('nav.home'), path: '/' },
  { name: t('nav.movies'), path: '/movies' },
  { name: t('nav.showtimes'), path: '/showtimes' },
  { name: t('nav.cinemas'), path: '/cinemas' },
  { name: t('nav.promotions'), path: '/promotions' },
])

function isActive(path: string) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

function handleAvatarError() {
  avatarLoadError.value = true
}

async function handleLogout() {
  await authStore.logout()
  isProfileDropdownOpen.value = false
  isMobileMenuOpen.value = false
  toast.info(locale.value === 'vi' ? 'Bạn đã đăng xuất khỏi hệ thống' : 'You have been logged out')
  router.push('/login')
}
</script>

<template>
  <div class="min-h-screen flex flex-col bg-slate-900 text-slate-100 antialiased selection:bg-indigo-500 selection:text-white">
    <!-- Header -->
    <header class="sticky top-0 z-40 bg-slate-900/95 backdrop-blur-md border-b border-slate-800 transition-colors">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex items-center justify-between h-16 sm:h-20">
          <!-- Logo & Nav -->
          <div class="flex items-center gap-6 lg:gap-8">
            <router-link to="/" class="flex items-center gap-2.5 group">
              <div class="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-600 to-indigo-400 flex items-center justify-center text-white shadow-md shadow-indigo-500/20 group-hover:scale-105 transition-transform">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 4v16M17 4v16M3 8h4m10 0h4M3 12h18M3 16h4m10 0h4M4 20h16a1 1 0 001-1V5a1 1 0 00-1-1H4a1 1 0 00-1 1v14a1 1 0 001 1z" />
                </svg>
              </div>
              <span class="text-xl font-bold tracking-tight text-white group-hover:text-indigo-300 transition-colors">
                CINE<span class="text-indigo-500">BOOK</span>
              </span>
            </router-link>

            <!-- Desktop Nav Links -->
            <nav class="hidden md:flex items-center gap-1">
              <router-link
                v-for="link in navLinks"
                :key="link.path"
                :to="link.path"
                :class="[
                  'px-3 py-2 rounded-lg text-sm font-medium transition-colors duration-150',
                  isActive(link.path)
                    ? 'text-indigo-400 bg-indigo-950/40 border border-indigo-800/40'
                    : 'text-slate-300 hover:text-white hover:bg-slate-800/60',
                ]"
              >
                {{ link.name }}
              </router-link>
            </nav>
          </div>

          <!-- User Section / Actions / Language Switcher -->
          <div class="hidden sm:flex items-center gap-3">
            <!-- Language Switcher Pill -->
            <div class="flex items-center bg-slate-800 p-0.5 rounded-lg border border-slate-700 text-xs font-semibold">
              <button
                type="button"
                :class="[
                  'px-2 py-1 rounded transition-colors',
                  locale === 'vi' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200'
                ]"
                @click="setLocale('vi')"
              >
                VI
              </button>
              <button
                type="button"
                :class="[
                  'px-2 py-1 rounded transition-colors',
                  locale === 'en' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200'
                ]"
                @click="setLocale('en')"
              >
                EN
              </button>
            </div>

            <template v-if="authStore.isAuthenticated">
              <!-- Admin Link if Admin -->
              <router-link
                v-if="authStore.isAdmin"
                to="/admin/dashboard"
                class="px-3 py-1.5 rounded-lg text-xs font-semibold bg-indigo-950 text-indigo-300 border border-indigo-700 hover:bg-indigo-900 transition-colors"
              >
                {{ t('nav.adminDashboard') }}
              </router-link>

              <!-- Profile Dropdown -->
              <div class="relative">
                <button
                  type="button"
                  class="flex items-center gap-2.5 p-1.5 rounded-xl hover:bg-slate-800 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  @click="isProfileDropdownOpen = !isProfileDropdownOpen"
                >
                  <!-- Real Avatar image or Initials Fallback -->
                  <div class="w-8 h-8 rounded-lg overflow-hidden bg-gradient-to-tr from-indigo-600 to-indigo-400 flex items-center justify-center text-white font-bold text-xs shadow-inner">
                    <img
                      v-if="authStore.user?.avatarUrl && !avatarLoadError"
                      :src="authStore.user.avatarUrl"
                      :alt="authStore.userFullName"
                      class="w-full h-full object-cover"
                      @error="handleAvatarError"
                    />
                    <span v-else class="uppercase">
                      {{ authStore.userInitials }}
                    </span>
                  </div>

                  <span class="text-sm font-medium text-slate-200 max-w-[120px] truncate">
                    {{ authStore.userFullName }}
                  </span>
                  <svg class="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                  </svg>
                </button>

                <!-- Dropdown Menu -->
                <div
                  v-if="isProfileDropdownOpen"
                  class="absolute right-0 mt-2 w-56 rounded-xl bg-slate-800 border border-slate-700 shadow-2xl py-1.5 z-50 text-sm"
                  @click="isProfileDropdownOpen = false"
                >
                  <div class="px-4 py-2 border-b border-slate-700/80">
                    <p class="text-xs text-slate-400">{{ t('nav.loggedInAs') }}</p>
                    <p class="font-medium text-white truncate">{{ authStore.user?.email }}</p>
                  </div>

                  <router-link
                    to="/profile"
                    class="flex items-center gap-2 px-4 py-2 text-slate-200 hover:bg-slate-700/80 transition-colors"
                  >
                    <svg class="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                    </svg>
                    {{ t('nav.myAccount') }}
                  </router-link>

                  <router-link
                    to="/my-bookings"
                    class="flex items-center gap-2 px-4 py-2 text-slate-200 hover:bg-slate-700/80 transition-colors"
                  >
                    <svg class="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z" />
                    </svg>
                    {{ t('nav.myBookings') }}
                  </router-link>

                  <div class="border-t border-slate-700/80 my-1"></div>

                  <button
                    type="button"
                    class="w-full flex items-center gap-2 px-4 py-2 text-rose-400 hover:bg-rose-950/40 transition-colors text-left"
                    @click="handleLogout"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                    </svg>
                    {{ t('nav.logout') }}
                  </button>
                </div>
              </div>
            </template>

            <template v-else>
              <router-link to="/login">
                <Button variant="ghost" size="sm">{{ t('nav.login') }}</Button>
              </router-link>
              <router-link to="/register">
                <Button variant="primary" size="sm">{{ t('nav.register') }}</Button>
              </router-link>
            </template>
          </div>

          <!-- Mobile Hamburger & Language -->
          <div class="flex items-center gap-2 sm:hidden">
            <div class="flex items-center bg-slate-800 p-0.5 rounded border border-slate-700 text-[11px] font-semibold">
              <button
                type="button"
                :class="['px-1.5 py-0.5 rounded', locale === 'vi' ? 'bg-indigo-600 text-white' : 'text-slate-400']"
                @click="setLocale('vi')"
              >
                VI
              </button>
              <button
                type="button"
                :class="['px-1.5 py-0.5 rounded', locale === 'en' ? 'bg-indigo-600 text-white' : 'text-slate-400']"
                @click="setLocale('en')"
              >
                EN
              </button>
            </div>

            <button
              type="button"
              class="p-2 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 focus:outline-none"
              aria-label="Toggle menu"
              @click="isMobileMenuOpen = !isMobileMenuOpen"
            >
              <svg v-if="!isMobileMenuOpen" class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
              </svg>
              <svg v-else class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      </div>

      <!-- Mobile Dropdown Menu -->
      <div v-if="isMobileMenuOpen" class="sm:hidden border-b border-slate-800 bg-slate-900/98 px-4 pt-2 pb-4 space-y-3">
        <nav class="space-y-1">
          <router-link
            v-for="link in navLinks"
            :key="link.path"
            :to="link.path"
            :class="[
              'block px-3 py-2 rounded-lg text-base font-medium transition-colors',
              isActive(link.path)
                ? 'text-indigo-400 bg-indigo-950/40 border border-indigo-800/40'
                : 'text-slate-300 hover:text-white hover:bg-slate-800',
            ]"
            @click="isMobileMenuOpen = false"
          >
            {{ link.name }}
          </router-link>
        </nav>

        <div class="pt-3 border-t border-slate-800">
          <template v-if="authStore.isAuthenticated">
            <div class="flex items-center gap-3 px-3 py-2">
              <div class="w-8 h-8 rounded-lg overflow-hidden bg-indigo-600 flex items-center justify-center text-white font-bold text-xs">
                <img
                  v-if="authStore.user?.avatarUrl && !avatarLoadError"
                  :src="authStore.user.avatarUrl"
                  :alt="authStore.userFullName"
                  class="w-full h-full object-cover"
                  @error="handleAvatarError"
                />
                <span v-else class="uppercase">
                  {{ authStore.userInitials }}
                </span>
              </div>
              <div class="flex-1 truncate">
                <p class="text-sm font-medium text-white">{{ authStore.userFullName }}</p>
                <p class="text-xs text-slate-400 truncate">{{ authStore.user?.email }}</p>
              </div>
            </div>

            <div class="mt-2 space-y-1">
              <router-link
                v-if="authStore.isAdmin"
                to="/admin/dashboard"
                class="block px-3 py-2 rounded-lg text-sm text-indigo-400 font-semibold hover:bg-slate-800"
                @click="isMobileMenuOpen = false"
              >
                {{ t('nav.adminDashboard') }}
              </router-link>
              <router-link
                to="/profile"
                class="block px-3 py-2 rounded-lg text-sm text-slate-300 hover:bg-slate-800"
                @click="isMobileMenuOpen = false"
              >
                {{ t('nav.myAccount') }}
              </router-link>
              <router-link
                to="/my-bookings"
                class="block px-3 py-2 rounded-lg text-sm text-slate-300 hover:bg-slate-800"
                @click="isMobileMenuOpen = false"
              >
                {{ t('nav.myBookings') }}
              </router-link>
              <button
                type="button"
                class="w-full text-left px-3 py-2 rounded-lg text-sm text-rose-400 hover:bg-rose-950/30"
                @click="handleLogout"
              >
                {{ t('nav.logout') }}
              </button>
            </div>
          </template>

          <template v-else>
            <div class="grid grid-cols-2 gap-2 pt-1">
              <router-link to="/login" class="w-full" @click="isMobileMenuOpen = false">
                <Button variant="secondary" size="md" block>{{ t('nav.login') }}</Button>
              </router-link>
              <router-link to="/register" class="w-full" @click="isMobileMenuOpen = false">
                <Button variant="primary" size="md" block>{{ t('nav.register') }}</Button>
              </router-link>
            </div>
          </template>
        </div>
      </div>
    </header>

    <!-- Main Content Shell -->
    <main class="flex-1 w-full relative min-h-[calc(100vh-18rem)]">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <!-- Footer -->
    <footer class="bg-slate-950 border-t border-slate-800 text-slate-400 text-sm mt-auto">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-8">
          <!-- Col 1: About -->
          <div class="space-y-3">
            <div class="flex items-center gap-2">
              <div class="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center text-white font-bold text-sm">
                CB
              </div>
              <span class="text-lg font-bold text-white tracking-tight">{{ t('footer.aboutTitle') }}</span>
            </div>
            <p class="text-xs text-slate-400 leading-relaxed">
              {{ t('footer.aboutText') }}
            </p>
          </div>

          <!-- Col 2: Terms & FAQs -->
          <div class="space-y-3">
            <h4 class="text-xs font-semibold uppercase tracking-wider text-slate-200">{{ t('footer.termsTitle') }}</h4>
            <ul class="space-y-2 text-xs">
              <li><router-link to="/terms" class="hover:text-indigo-400 transition-colors">{{ t('footer.termsOfUse') }}</router-link></li>
              <li><router-link to="/privacy" class="hover:text-indigo-400 transition-colors">{{ t('footer.privacyPolicy') }}</router-link></li>
              <li><router-link to="/refund" class="hover:text-indigo-400 transition-colors">{{ t('footer.refundPolicy') }}</router-link></li>
              <li><router-link to="/faq" class="hover:text-indigo-400 transition-colors">{{ t('footer.faq') }}</router-link></li>
            </ul>
          </div>

          <!-- Col 3: Customer Care -->
          <div class="space-y-3">
            <h4 class="text-xs font-semibold uppercase tracking-wider text-slate-200">{{ t('footer.supportTitle') }}</h4>
            <ul class="space-y-2 text-xs text-slate-400">
              <li><strong class="text-slate-300">{{ t('footer.hotline') }}:</strong> 1900 6868 (1,000đ/phút)</li>
              <li>{{ t('footer.workingHours') }}</li>
              <li><strong class="text-slate-300">{{ t('footer.email') }}:</strong> support@cinebook.com</li>
            </ul>
          </div>

          <!-- Col 4: Payment Partners -->
          <div class="space-y-3">
            <h4 class="text-xs font-semibold uppercase tracking-wider text-slate-200">{{ t('footer.paymentTitle') }}</h4>
            <p class="text-xs text-slate-400">
              {{ t('footer.paymentText') }}
            </p>
            <div class="flex items-center gap-2 pt-1">
              <div class="px-2.5 py-1.5 rounded-lg bg-slate-800 border border-slate-700 text-xs font-black tracking-wider text-blue-400">
                VN<span class="text-red-500">PAY</span>
              </div>
              <div class="px-2.5 py-1.5 rounded-lg bg-slate-800 border border-slate-700 text-xs font-bold text-slate-300">
                ATM / VISA
              </div>
            </div>
          </div>
        </div>

        <div class="mt-12 pt-6 border-t border-slate-800/80 text-center text-xs text-slate-500">
          {{ t('footer.copyright', { year: new Date().getFullYear() }) }}
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
