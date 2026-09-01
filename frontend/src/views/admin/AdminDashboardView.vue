<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import reportService from '@/services/report.service'
import type { DashboardResponse, UserStatisticsResponse } from '@/types/report.types'
import { formatCurrency, formatNumber } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const { t } = useI18n()
const router = useRouter()

const isLoading = ref(true)
const errorMessage = ref('')
const dashboardData = ref<DashboardResponse | null>(null)
const userStats = ref<UserStatisticsResponse | null>(null)

async function loadDashboard() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const [dash, uStats] = await Promise.all([
      reportService.getDashboardSummary().catch(() => null),
      reportService.getUserStatistics().catch(() => null),
    ])

    dashboardData.value = dash
    userStats.value = uStats
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
  } finally {
    isLoading.value = false
  }
}

onMounted(() => {
  loadDashboard()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-white tracking-tight">{{ t('adminDashboard.title') }}</h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ t('adminDashboard.subtitle') }}
        </p>
      </div>

      <div class="flex items-center gap-2">
        <Button variant="primary" size="sm" @click="router.push('/admin/reports')">
          <template #prefix>
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
          </template>
          {{ t('adminDashboard.actionReports') }}
        </Button>
      </div>
    </div>

    <!-- Error Alert -->
    <ErrorAlert v-if="errorMessage" :message="errorMessage" />

    <!-- KPI Cards -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4">
      <Card padding="sm" class="border-emerald-500/30">
        <p class="text-[11px] font-semibold text-emerald-400 uppercase tracking-wider">{{ t('adminReports.kpiNetRevenue') }}</p>
        <p class="text-lg font-black text-white mt-1 font-mono">
          {{ formatCurrency(dashboardData?.financial?.netRevenue || 0) }}
        </p>
      </Card>

      <Card padding="sm" class="border-indigo-500/30">
        <p class="text-[11px] font-semibold text-indigo-400 uppercase tracking-wider">{{ t('adminReports.kpiNetTickets') }}</p>
        <p class="text-lg font-black text-white mt-1 font-mono">
          {{ formatNumber(dashboardData?.tickets?.netTicketsSold || 0) }}
        </p>
      </Card>

      <Card padding="sm" class="border-sky-500/30">
        <p class="text-[11px] font-semibold text-sky-400 uppercase tracking-wider">{{ t('adminReports.kpiGrossRevenue') }}</p>
        <p class="text-lg font-black text-white mt-1 font-mono">
          {{ formatCurrency(dashboardData?.financial?.grossRevenue || 0) }}
        </p>
      </Card>

      <Card padding="sm" class="border-amber-500/30">
        <p class="text-[11px] font-semibold text-amber-400 uppercase tracking-wider">{{ t('adminReports.tabBookingStatus') }}</p>
        <p class="text-lg font-black text-white mt-1 font-mono">
          {{ formatNumber(dashboardData?.bookings?.totalBookings || 0) }}
        </p>
      </Card>

      <Card padding="sm" class="border-rose-500/30">
        <p class="text-[11px] font-semibold text-rose-400 uppercase tracking-wider">{{ t('adminDashboard.kpiActiveShowtimes') }}</p>
        <p class="text-lg font-black text-white mt-1 font-mono">
          {{ formatNumber(dashboardData?.operations?.totalShowtimes || 0) }}
        </p>
      </Card>

      <Card padding="sm" class="border-purple-500/30">
        <p class="text-[11px] font-semibold text-purple-400 uppercase tracking-wider">{{ t('adminDashboard.kpiTotalMembers') }}</p>
        <p class="text-lg font-black text-white mt-1 font-mono">
          {{ formatNumber(userStats?.totalUsers || 0) }}
        </p>
      </Card>
    </div>

    <!-- Quick Management Action Grid -->
    <div class="space-y-3">
      <h2 class="text-sm font-bold text-slate-300 uppercase tracking-wider">
        {{ t('adminDashboard.quickActionsTitle') }}
      </h2>

      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
        <button
          class="p-4 rounded-xl bg-slate-800/80 border border-slate-700 hover:border-indigo-500 hover:bg-slate-800 transition-all flex flex-col items-center text-center gap-2.5 group"
          @click="router.push('/admin/bookings')"
        >
          <div class="w-10 h-10 rounded-lg bg-indigo-500/20 text-indigo-400 flex items-center justify-center group-hover:scale-110 transition-transform">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z" />
            </svg>
          </div>
          <span class="text-xs font-semibold text-slate-200 group-hover:text-white">{{ t('adminDashboard.actionManageBookings') }}</span>
        </button>

        <button
          class="p-4 rounded-xl bg-slate-800/80 border border-slate-700 hover:border-indigo-500 hover:bg-slate-800 transition-all flex flex-col items-center text-center gap-2.5 group"
          @click="router.push('/admin/movies')"
        >
          <div class="w-10 h-10 rounded-lg bg-sky-500/20 text-sky-400 flex items-center justify-center group-hover:scale-110 transition-transform">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 4v16M17 4v16M3 8h4m10 0h4M3 12h18M3 16h4m10 0h4M4 20h16a1 1 0 001-1V5a1 1 0 00-1-1H4a1 1 0 00-1 1v14a1 1 0 001 1z" />
            </svg>
          </div>
          <span class="text-xs font-semibold text-slate-200 group-hover:text-white">{{ t('adminDashboard.actionManageMovies') }}</span>
        </button>

        <button
          class="p-4 rounded-xl bg-slate-800/80 border border-slate-700 hover:border-indigo-500 hover:bg-slate-800 transition-all flex flex-col items-center text-center gap-2.5 group"
          @click="router.push('/admin/showtimes')"
        >
          <div class="w-10 h-10 rounded-lg bg-emerald-500/20 text-emerald-400 flex items-center justify-center group-hover:scale-110 transition-transform">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
          </div>
          <span class="text-xs font-semibold text-slate-200 group-hover:text-white">{{ t('adminDashboard.actionManageShowtimes') }}</span>
        </button>

        <button
          class="p-4 rounded-xl bg-slate-800/80 border border-slate-700 hover:border-indigo-500 hover:bg-slate-800 transition-all flex flex-col items-center text-center gap-2.5 group"
          @click="router.push('/admin/cinemas')"
        >
          <div class="w-10 h-10 rounded-lg bg-amber-500/20 text-amber-400 flex items-center justify-center group-hover:scale-110 transition-transform">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
            </svg>
          </div>
          <span class="text-xs font-semibold text-slate-200 group-hover:text-white">{{ t('adminDashboard.actionManageCinemas') }}</span>
        </button>

        <button
          class="p-4 rounded-xl bg-slate-800/80 border border-slate-700 hover:border-indigo-500 hover:bg-slate-800 transition-all flex flex-col items-center text-center gap-2.5 group"
          @click="router.push('/admin/users')"
        >
          <div class="w-10 h-10 rounded-lg bg-purple-500/20 text-purple-400 flex items-center justify-center group-hover:scale-110 transition-transform">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" />
            </svg>
          </div>
          <span class="text-xs font-semibold text-slate-200 group-hover:text-white">{{ t('adminDashboard.actionManageUsers') }}</span>
        </button>
      </div>
    </div>
  </div>
</template>
