<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import reportService from '@/services/report.service'
import type {
  DashboardResponse,
  RevenueTrendResponse,
  MovieReportResponse,
  CinemaReportResponse,
  ShowtimeOccupancyResponse,
  UserStatisticsResponse,
} from '@/types/report.types'
import { formatCurrency, formatNumber, formatPercent } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import LineChart from '@/components/charts/LineChart.vue'
import BarChart from '@/components/charts/BarChart.vue'
import HorizontalBarChart from '@/components/charts/HorizontalBarChart.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import { useToast } from '@/composables/useToast'

const { t } = useI18n()
const toast = useToast()

const isLoading = ref(true)
const errorMessage = ref('')

// Filters
const selectedPeriod = ref<'7d' | '30d' | 'all'>('30d')
const groupBy = ref<'DAY' | 'MONTH' | 'YEAR'>('DAY')
const movieSortBy = ref<'REVENUE' | 'TICKETS'>('REVENUE')

// Data states
const dashboardData = ref<DashboardResponse | null>(null)
const revenueData = ref<RevenueTrendResponse[]>([])
const moviesData = ref<MovieReportResponse[]>([])
const cinemasData = ref<CinemaReportResponse[]>([])
const userStats = ref<UserStatisticsResponse | null>(null)
const occupancyData = ref<ShowtimeOccupancyResponse[]>([])
const occupancyPage = ref(0)
const occupancyTotalPages = ref(0)

function calculateDateRange() {
  const now = new Date()
  let fromDate: Date | null = null

  if (selectedPeriod.value === '7d') {
    fromDate = new Date()
    fromDate.setDate(now.getDate() - 7)
  } else if (selectedPeriod.value === '30d') {
    fromDate = new Date()
    fromDate.setDate(now.getDate() - 30)
  }

  const from = fromDate ? fromDate.toISOString().split('T')[0] : undefined
  const to = now.toISOString().split('T')[0]
  return { from, to }
}

async function loadAllReports() {
  isLoading.value = true
  errorMessage.value = ''

  const { from, to } = calculateDateRange()

  try {
    const [dash, rev, mov, cin, uStats, occ] = await Promise.all([
      reportService.getDashboardSummary(from, to).catch(() => null),
      reportService.getRevenueTrend(from, to, groupBy.value).catch(() => []),
      reportService.getMovieReport(from, to, movieSortBy.value, 10).catch(() => []),
      reportService.getCinemaReport(from, to, 'REVENUE', 10).catch(() => []),
      reportService.getUserStatistics(from, to).catch(() => null),
      reportService.getShowtimeOccupancy({ from, to, page: occupancyPage.value, size: 10 }).catch(() => ({ content: [], totalPages: 0 })),
    ])

    dashboardData.value = dash
    revenueData.value = rev || []
    moviesData.value = mov || []
    cinemasData.value = cin || []
    userStats.value = uStats
    occupancyData.value = occ?.content || []
    occupancyTotalPages.value = occ?.totalPages || 0
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
  } finally {
    isLoading.value = false
  }
}

// Chart computed properties
const revenueChartLabels = ref<string[]>([])
const revenueChartSeries = ref<any[]>([])

watch(revenueData, (data) => {
  revenueChartLabels.value = data.map((d) => d.period)
  revenueChartSeries.value = [
    {
      name: t('adminReports.seriesRevenue'),
      data: data.map((d) => d.netRevenue),
      color: '#10b981',
      yAxisIndex: 0,
    },
    {
      name: t('adminReports.seriesTickets'),
      data: data.map((d) => d.ticketCount || 0),
      color: '#6366f1',
      yAxisIndex: 1,
    },
  ]
}, { immediate: true })

const movieBarItems = ref<any[]>([])
watch([moviesData, movieSortBy], () => {
  movieBarItems.value = moviesData.value.map((m) => ({
    label: m.movieTitle,
    value: movieSortBy.value === 'REVENUE' ? m.totalRevenue : m.ticketsSold,
    subValue: movieSortBy.value === 'REVENUE'
      ? `${formatNumber(m.ticketsSold)} ${t('adminReports.unitTickets')}`
      : formatCurrency(m.totalRevenue),
    color: '#6366f1',
  }))
}, { immediate: true })

const cinemaChartLabels = ref<string[]>([])
const cinemaChartValues = ref<number[]>([])
watch(cinemasData, (data) => {
  cinemaChartLabels.value = data.map((c) => c.cinemaName)
  cinemaChartValues.value = data.map((c) => c.totalRevenue)
}, { immediate: true })

const bookingStatusSegments = ref<any[]>([])
watch(dashboardData, (dash) => {
  if (!dash?.bookings) {
    bookingStatusSegments.value = []
    return
  }
  bookingStatusSegments.value = [
    { label: t('status.PAID'), value: dash.bookings.paidBookings || 0, color: '#10b981' },
    { label: t('status.REFUNDED'), value: dash.bookings.refundedBookings || 0, color: '#f59e0b' },
    { label: t('status.CANCELLED'), value: dash.bookings.cancelledBookings || 0, color: '#ef4444' },
    { label: t('status.EXPIRED'), value: dash.bookings.expiredBookings || 0, color: '#64748b' },
  ].filter((s) => s.value > 0)
}, { immediate: true })

async function exportReport(format: 'XLSX' | 'CSV') {
  try {
    const { from, to } = calculateDateRange()
    await reportService.exportReport('REVENUE', format, from, to)
    toast.success(`${t('adminReports.exportExcel')} (${format}) ${t('common.successTitle')}`)
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  }
}

watch([selectedPeriod, groupBy, movieSortBy], () => {
  loadAllReports()
})

onMounted(() => {
  loadAllReports()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-white tracking-tight">{{ t('adminReports.title') }}</h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ t('adminReports.subtitle') }}
        </p>
      </div>

      <div class="flex items-center gap-2 flex-wrap">
        <Button variant="secondary" size="sm" @click="exportReport('CSV')">
          <template #prefix>
            <svg class="w-4 h-4 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
          </template>
          {{ t('adminReports.exportCsv') }}
        </Button>
        <Button variant="primary" size="sm" :loading="isLoading" @click="loadAllReports">
          <template #prefix>
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </template>
          {{ t('common.refresh') }}
        </Button>
      </div>
    </div>

    <!-- Error Alert -->
    <ErrorAlert v-if="errorMessage" :message="errorMessage" />

    <!-- Filters Bar -->
    <Card padding="sm">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <!-- Period -->
        <div class="flex items-center gap-2">
          <span class="text-xs text-slate-400 font-medium">{{ t('adminReports.filterPeriod') }}:</span>
          <div class="flex rounded-lg bg-slate-900 p-1 border border-slate-800">
            <button
              :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', selectedPeriod === '7d' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-slate-200']"
              @click="selectedPeriod = '7d'"
            >
              {{ t('adminReports.period7Days') }}
            </button>
            <button
              :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', selectedPeriod === '30d' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-slate-200']"
              @click="selectedPeriod = '30d'"
            >
              {{ t('adminReports.period30Days') }}
            </button>
            <button
              :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', selectedPeriod === 'all' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-slate-200']"
              @click="selectedPeriod = 'all'"
            >
              {{ t('adminReports.periodAll') }}
            </button>
          </div>
        </div>

        <!-- Group By -->
        <div class="flex items-center gap-2">
          <span class="text-xs text-slate-400 font-medium">{{ t('adminReports.groupBy') }}:</span>
          <div class="flex rounded-lg bg-slate-900 p-1 border border-slate-800">
            <button
              :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', groupBy === 'DAY' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-slate-200']"
              @click="groupBy = 'DAY'"
            >
              {{ t('adminReports.groupByDay') }}
            </button>
            <button
              :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', groupBy === 'MONTH' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-slate-200']"
              @click="groupBy = 'MONTH'"
            >
              {{ t('adminReports.groupByMonth') }}
            </button>
            <button
              :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', groupBy === 'YEAR' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-slate-200']"
              @click="groupBy = 'YEAR'"
            >
              {{ t('adminReports.groupByYear') }}
            </button>
          </div>
        </div>
      </div>
    </Card>

    <!-- Top KPI Summary Cards -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <Card padding="md" class="border-emerald-500/30 bg-gradient-to-br from-slate-900 to-emerald-950/20">
        <p class="text-xs font-semibold text-emerald-400 uppercase tracking-wider">{{ t('adminReports.kpiNetRevenue') }}</p>
        <p class="text-2xl font-black text-white mt-1 font-mono">
          {{ formatCurrency(dashboardData?.financial?.netRevenue || 0) }}
        </p>
        <div class="flex items-center justify-between text-[11px] text-slate-400 mt-2 pt-2 border-t border-slate-800">
          <span>{{ t('adminReports.kpiGrossRevenue') }}:</span>
          <span class="font-mono text-slate-200">{{ formatCurrency(dashboardData?.financial?.grossRevenue || 0) }}</span>
        </div>
      </Card>

      <Card padding="md" class="border-indigo-500/30 bg-gradient-to-br from-slate-900 to-indigo-950/20">
        <p class="text-xs font-semibold text-indigo-400 uppercase tracking-wider">{{ t('adminReports.kpiNetTickets') }}</p>
        <p class="text-2xl font-black text-white mt-1 font-mono">
          {{ formatNumber(dashboardData?.tickets?.netTicketsSold || 0) }}
        </p>
        <div class="flex items-center justify-between text-[11px] text-slate-400 mt-2 pt-2 border-t border-slate-800">
          <span>{{ t('adminReports.kpiRefundedTickets') }}:</span>
          <span class="font-mono text-rose-400">{{ formatNumber(dashboardData?.tickets?.refundedTickets || 0) }}</span>
        </div>
      </Card>

      <Card padding="md" class="border-sky-500/30 bg-gradient-to-br from-slate-900 to-sky-950/20">
        <p class="text-xs font-semibold text-sky-400 uppercase tracking-wider">{{ t('adminReports.kpiOccupancyRate') }}</p>
        <p class="text-2xl font-black text-white mt-1 font-mono">
          {{ formatPercent(dashboardData?.operations?.averageOccupancyRate || 0) }}
        </p>
        <div class="flex items-center justify-between text-[11px] text-slate-400 mt-2 pt-2 border-t border-slate-800">
          <span>{{ t('adminDashboard.kpiActiveShowtimes') }}:</span>
          <span class="font-mono text-slate-200">{{ formatNumber(dashboardData?.operations?.totalShowtimes || 0) }}</span>
        </div>
      </Card>

      <Card padding="md" class="border-purple-500/30 bg-gradient-to-br from-slate-900 to-purple-950/20">
        <p class="text-xs font-semibold text-purple-400 uppercase tracking-wider">{{ t('adminReports.kpiTotalUsers') }}</p>
        <p class="text-2xl font-black text-white mt-1 font-mono">
          {{ formatNumber(userStats?.totalUsers || 0) }}
        </p>
        <div class="flex items-center justify-between text-[11px] text-slate-400 mt-2 pt-2 border-t border-slate-800">
          <span>{{ t('adminReports.kpiActiveUsers') }}:</span>
          <span class="font-mono text-emerald-400">{{ formatNumber(userStats?.activeUsers || 0) }}</span>
        </div>
      </Card>
    </div>

    <!-- Main Visualizations Grid -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- Revenue Trends Line Chart (2 Cols) -->
      <Card padding="md" class="lg:col-span-2 space-y-4">
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-800 pb-3">
          <div>
            <h3 class="text-sm font-bold text-white uppercase tracking-wider">
              {{ t('adminReports.revenueChartTitle') }}
            </h3>
            <p class="text-[11px] text-slate-400 mt-0.5">
              {{ t('adminReports.unitVndTickets') }}
            </p>
          </div>
        </div>

        <LineChart
          :labels="revenueChartLabels"
          :series="revenueChartSeries"
          :height="300"
          :y-axis-format="formatCurrency"
          :y-axis-right-format="formatNumber"
          :empty-text="t('adminReports.emptyChartData')"
        />
      </Card>

      <!-- Booking Status Donut Chart (1 Col) -->
      <Card padding="md" class="space-y-4">
        <div class="border-b border-slate-800 pb-3">
          <h3 class="text-sm font-bold text-white uppercase tracking-wider">
            {{ t('adminReports.bookingDistributionTitle') }}
          </h3>
          <p class="text-[11px] text-slate-400 mt-0.5">
            {{ t('myBookings.allStatuses') }}
          </p>
        </div>

        <DonutChart
          :segments="bookingStatusSegments"
          :center-value="formatNumber(dashboardData?.bookings?.totalBookings || 0)"
          :center-label="t('adminReports.tabBookingStatus')"
          :empty-text="t('adminReports.emptyChartData')"
        />
      </Card>
    </div>

    <!-- Secondary Visualizations Grid -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- Top Movies Horizontal Bar Chart -->
      <Card padding="md" class="space-y-4">
        <div class="flex items-center justify-between border-b border-slate-800 pb-3">
          <div>
            <h3 class="text-sm font-bold text-white uppercase tracking-wider">
              {{ t('adminReports.moviePerformanceTitle') }}
            </h3>
            <p class="text-[11px] text-slate-400 mt-0.5">
              {{ movieSortBy === 'REVENUE' ? t('adminReports.sortByRevenue') : t('adminReports.sortByTickets') }}
            </p>
          </div>

          <div class="flex rounded-lg bg-slate-900 p-1 border border-slate-800 text-xs">
            <button
              :class="['px-2.5 py-1 rounded font-medium transition-colors', movieSortBy === 'REVENUE' ? 'bg-indigo-600 text-white' : 'text-slate-400']"
              @click="movieSortBy = 'REVENUE'"
            >
              {{ t('adminReports.sortByRevenue') }}
            </button>
            <button
              :class="['px-2.5 py-1 rounded font-medium transition-colors', movieSortBy === 'TICKETS' ? 'bg-indigo-600 text-white' : 'text-slate-400']"
              @click="movieSortBy = 'TICKETS'"
            >
              {{ t('adminReports.sortByTickets') }}
            </button>
          </div>
        </div>

        <HorizontalBarChart
          :items="movieBarItems"
          :value-format="movieSortBy === 'REVENUE' ? formatCurrency : formatNumber"
          :empty-text="t('adminReports.emptyChartData')"
        />
      </Card>

      <!-- Cinema Revenue Bar Chart -->
      <Card padding="md" class="space-y-4">
        <div class="border-b border-slate-800 pb-3">
          <h3 class="text-sm font-bold text-white uppercase tracking-wider">
            {{ t('adminReports.cinemaPerformanceTitle') }}
          </h3>
          <p class="text-[11px] text-slate-400 mt-0.5">
            {{ t('adminReports.unitVnd') }}
          </p>
        </div>

        <BarChart
          :labels="cinemaChartLabels"
          :values="cinemaChartValues"
          :height="280"
          :value-format="formatCurrency"
          :empty-text="t('adminReports.emptyChartData')"
        />
      </Card>
    </div>
  </div>
</template>
