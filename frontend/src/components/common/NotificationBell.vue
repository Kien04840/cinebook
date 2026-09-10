<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useI18n } from '@/composables/useI18n'
import { notificationService } from '@/services/notification.service'
import type { NotificationResponse, NotificationType } from '@/types/notification.types'

const router = useRouter()
const authStore = useAuthStore()
const { t, locale } = useI18n()

const isOpen = ref(false)
const isLoading = ref(false)
const isMarkingAll = ref(false)
const errorMessage = ref<string | null>(null)
const unreadCount = ref(0)
const notifications = ref<NotificationResponse[]>([])
const dropdownRef = ref<HTMLElement | null>(null)

let pollingTimer: ReturnType<typeof setInterval> | null = null
const POLLING_INTERVAL_MS = 30000 // 30 seconds

const hasUnread = computed(() => unreadCount.value > 0)
const displayBadge = computed(() => {
  if (unreadCount.value > 99) return '99+'
  return unreadCount.value.toString()
})

async function fetchUnreadCount() {
  if (!authStore.isAuthenticated) return
  try {
    const res = await notificationService.getUnreadCount()
    unreadCount.value = res.unreadCount
  } catch (err) {
    // Non-intrusive polling error
    console.debug('Failed to fetch unread notification count', err)
  }
}

async function fetchNotifications() {
  if (!authStore.isAuthenticated) return
  isLoading.value = true
  errorMessage.value = null
  try {
    const res = await notificationService.getNotifications(0, 10)
    notifications.value = res.content || []
  } catch (err) {
    console.error('Failed to load notifications', err)
    errorMessage.value = t('notifications.loadError')
  } finally {
    isLoading.value = false
  }
}

function toggleDropdown() {
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    fetchNotifications()
  }
}

function closeDropdown() {
  isOpen.value = false
}

function handleClickOutside(event: MouseEvent) {
  if (dropdownRef.value && !dropdownRef.value.contains(event.target as Node)) {
    closeDropdown()
  }
}

async function handleNotificationClick(item: NotificationResponse) {
  if (!item.isRead) {
    try {
      await notificationService.markAsRead(item.id)
      item.isRead = true
      item.readAt = new Date().toISOString()
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch (err) {
      console.error('Failed to mark notification as read', err)
    }
  }

  closeDropdown()

  if (item.bookingId) {
    router.push('/my-bookings')
  }
}

async function handleMarkAllAsRead() {
  if (isMarkingAll.value || unreadCount.value === 0) return
  isMarkingAll.value = true
  try {
    await notificationService.markAllAsRead()
    notifications.value.forEach((n) => {
      n.isRead = true
      n.readAt = new Date().toISOString()
    })
    unreadCount.value = 0
  } catch (err) {
    console.error('Failed to mark all notifications as read', err)
  } finally {
    isMarkingAll.value = false
  }
}

function formatRelativeTime(dateStr: string): string {
  if (!dateStr) return ''
  const then = new Date(dateStr).getTime()
  const now = Date.now()
  const diffMinutes = Math.floor((now - then) / 60000)

  if (diffMinutes < 1) {
    return t('notifications.timeJustNow')
  }
  if (diffMinutes < 60) {
    return t('notifications.timeMinutesAgo', { count: diffMinutes })
  }
  const diffHours = Math.floor(diffMinutes / 60)
  if (diffHours < 24) {
    return t('notifications.timeHoursAgo', { count: diffHours })
  }
  const diffDays = Math.floor(diffHours / 24)
  return t('notifications.timeDaysAgo', { count: diffDays })
}

function getNotificationBadgeClass(type: NotificationType) {
  switch (type) {
    case 'PAYMENT_SUCCESS':
      return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
    case 'BOOKING_CANCELLED':
      return 'bg-amber-500/10 text-amber-400 border-amber-500/30'
    case 'REFUND_COMPLETED':
      return 'bg-cyan-500/10 text-cyan-400 border-cyan-500/30'
    default:
      return 'bg-indigo-500/10 text-indigo-400 border-indigo-500/30'
  }
}

// Start / stop polling based on auth state
watch(
  () => authStore.isAuthenticated,
  (isAuth) => {
    if (isAuth) {
      fetchUnreadCount()
      if (!pollingTimer) {
        pollingTimer = setInterval(fetchUnreadCount, POLLING_INTERVAL_MS)
      }
    } else {
      if (pollingTimer) {
        clearInterval(pollingTimer)
        pollingTimer = null
      }
      unreadCount.value = 0
      notifications.value = []
      isOpen.value = false
    }
  },
  { immediate: true }
)

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  if (authStore.isAuthenticated) {
    fetchUnreadCount()
    if (!pollingTimer) {
      pollingTimer = setInterval(fetchUnreadCount, POLLING_INTERVAL_MS)
    }
  }
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
})
</script>

<template>
  <div v-if="authStore.isAuthenticated" ref="dropdownRef" class="relative">
    <!-- Bell Trigger Button -->
    <button
      type="button"
      class="relative p-2 rounded-xl text-slate-300 hover:text-white hover:bg-slate-800 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500"
      :aria-label="t('notifications.title')"
      :title="t('notifications.title')"
      @click="toggleDropdown"
    >
      <!-- Bell Icon -->
      <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="2"
          d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
        />
      </svg>

      <!-- Unread Count Badge -->
      <span
        v-if="hasUnread"
        class="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 rounded-full bg-rose-500 text-white text-[10px] font-bold flex items-center justify-center shadow-md animate-pulse ring-2 ring-slate-900"
      >
        {{ displayBadge }}
      </span>
    </button>

    <!-- Notification Dropdown Panel -->
    <transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="transform scale-95 opacity-0"
      enter-to-class="transform scale-100 opacity-100"
      leave-active-class="transition duration-100 ease-in"
      leave-from-class="transform scale-100 opacity-100"
      leave-to-class="transform scale-95 opacity-0"
    >
      <div
        v-if="isOpen"
        class="absolute right-0 mt-2 w-80 sm:w-96 rounded-2xl bg-slate-900/98 backdrop-blur-xl border border-slate-800 shadow-2xl shadow-black/60 z-50 overflow-hidden text-slate-200"
      >
        <!-- Header -->
        <div class="px-4 py-3 border-b border-slate-800 flex items-center justify-between bg-slate-900/60">
          <div class="flex items-center gap-2">
            <h3 class="text-sm font-bold text-white tracking-wide">
              {{ t('notifications.title') }}
            </h3>
            <span
              v-if="hasUnread"
              class="px-2 py-0.5 rounded-full text-[11px] font-semibold bg-indigo-950 text-indigo-300 border border-indigo-800/60"
            >
              {{ unreadCount }} {{ t('notifications.unread') }}
            </span>
          </div>

          <button
            v-if="hasUnread"
            type="button"
            class="text-xs text-indigo-400 hover:text-indigo-300 transition-colors font-medium disabled:opacity-50"
            :disabled="isMarkingAll"
            @click="handleMarkAllAsRead"
          >
            {{ isMarkingAll ? '...' : t('notifications.markAllRead') }}
          </button>
        </div>

        <!-- Content Area -->
        <div class="max-h-[380px] overflow-y-auto divide-y divide-slate-800/60">
          <!-- Loading State -->
          <div v-if="isLoading" class="py-8 text-center text-slate-400 text-xs flex flex-col items-center gap-2">
            <svg class="animate-spin h-5 w-5 text-indigo-500" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
            </svg>
            <span>{{ t('notifications.loading') }}</span>
          </div>

          <!-- Error State -->
          <div v-else-if="errorMessage" class="py-6 px-4 text-center">
            <p class="text-xs text-rose-400 mb-2">{{ errorMessage }}</p>
            <button
              type="button"
              class="text-xs text-indigo-400 hover:underline"
              @click="fetchNotifications"
            >
              Thử lại
            </button>
          </div>

          <!-- Empty State -->
          <div v-else-if="notifications.length === 0" class="py-10 px-6 text-center">
            <div class="w-12 h-12 rounded-full bg-slate-800/80 flex items-center justify-center mx-auto mb-3 text-slate-500">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
              </svg>
            </div>
            <p class="text-sm font-semibold text-slate-300 mb-1">{{ t('notifications.empty') }}</p>
            <p class="text-xs text-slate-500 max-w-[240px] mx-auto leading-relaxed">{{ t('notifications.emptyDesc') }}</p>
          </div>

          <!-- Notification Items List -->
          <div
            v-for="item in notifications"
            v-else
            :key="item.id"
            class="p-3.5 hover:bg-slate-800/60 transition-colors cursor-pointer flex items-start gap-3 text-left relative group"
            :class="{ 'bg-indigo-950/20': !item.isRead }"
            @click="handleNotificationClick(item)"
          >
            <!-- Type Icon Indicator -->
            <div
              class="w-9 h-9 rounded-xl border flex items-center justify-center shrink-0 mt-0.5 shadow-sm"
              :class="getNotificationBadgeClass(item.type)"
            >
              <!-- Payment Success Icon -->
              <svg v-if="item.type === 'PAYMENT_SUCCESS'" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7" />
              </svg>
              <!-- Booking Cancelled Icon -->
              <svg v-else-if="item.type === 'BOOKING_CANCELLED'" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M6 18L18 6M6 6l12 12" />
              </svg>
              <!-- Refund Completed Icon -->
              <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h10a8 8 0 018 8v2M3 10l6 6m-6-6l6-6" />
              </svg>
            </div>

            <!-- Content -->
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between gap-2 mb-0.5">
                <p
                  class="text-xs font-semibold truncate"
                  :class="item.isRead ? 'text-slate-300' : 'text-white font-bold'"
                >
                  {{ item.title }}
                </p>
                <span class="text-[10px] text-slate-500 whitespace-nowrap shrink-0">
                  {{ formatRelativeTime(item.createdAt) }}
                </span>
              </div>

              <p class="text-xs text-slate-400 line-clamp-2 leading-relaxed">
                {{ item.message }}
              </p>

              <!-- Optional Booking reference tag -->
              <div v-if="item.bookingCode" class="mt-1 flex items-center gap-1.5 text-[10px] text-indigo-400 font-medium">
                <span>#{{ item.bookingCode }}</span>
                <span class="text-slate-600">•</span>
                <span class="text-slate-400 group-hover:text-indigo-300 transition-colors">{{ t('notifications.viewBooking') }} &rarr;</span>
              </div>
            </div>

            <!-- Unread Status Dot -->
            <span
              v-if="!item.isRead"
              class="w-2 h-2 rounded-full bg-indigo-500 mt-1.5 shrink-0 shadow-sm shadow-indigo-500/50"
              :title="t('notifications.markAsRead')"
            ></span>
          </div>
        </div>

        <!-- Footer -->
        <div class="p-2.5 border-t border-slate-800 bg-slate-900/80 text-center">
          <router-link
            to="/my-bookings"
            class="text-xs text-indigo-400 hover:text-indigo-300 transition-colors font-semibold block py-1"
            @click="closeDropdown"
          >
            {{ locale === 'vi' ? 'Xem danh sách vé & đơn hàng' : 'View bookings & tickets' }} &rarr;
          </router-link>
        </div>
      </div>
    </transition>
  </div>
</template>

