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
  ReportType,
  ReportFormat,
  DateFilterPreset,
} from '@/types/report.types'
import { formatCurrency, formatNumber, formatPercent, formatDateTime } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import LineChart from '@/components/charts/LineChart.vue'
import BarChart from '@/components/charts/BarChart.vue'
import HorizontalBarChart from '@/components/charts/HorizontalBarChart.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import Pagination from '@/components/common/Pagination.vue'
import { useToast } from '@/composables/useToast'

const { t } = useI18n()
const toast = useToast()

const isLoading = ref(true)
const isExportingXlsx = ref(false)
const isExportingCsv = ref(false)
const errorMessage = ref('')
const dateValidationError = ref('')

// Filters
const selectedReportType = ref<ReportType>('REVENUE')
const selectedPeriod = ref<DateFilterPreset>('7d')
const customFromDate = ref<string>('')
const customToDate = ref<string>('')
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

function formatDateToYMD(d: Date): string {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function setPeriod(preset: DateFilterPreset) {
  selectedPeriod.value = preset
  dateValidationError.value = ''
  const now = new Date()

  if (preset === 'TODAY') {
    const todayStr = formatDateToYMD(now)
    customFromDate.value = todayStr
    customToDate.value = todayStr
  } else if (preset === 'YESTERDAY') {
    const y = new Date(now)
    y.setDate(now.getDate() - 1)
    const yStr = formatDateToYMD(y)
    customFromDate.value = yStr
    customToDate.value = yStr
  } else if (preset === '7d') {
    const d = new Date(now)
    d.setDate(now.getDate() - 6)
    customFromDate.value = formatDateToYMD(d)
    customToDate.value = formatDateToYMD(now)
  } else if (preset === '30d') {
    const d = new Date(now)
    d.setDate(now.getDate() - 29)
    customFromDate.value = formatDateToYMD(d)
    customToDate.value = formatDateToYMD(now)
  } else if (preset === 'THIS_MONTH') {
    const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1)
    customFromDate.value = formatDateToYMD(startOfMonth)
    customToDate.value = formatDateToYMD(now)
  } else if (preset === 'custom') {
    if (!customFromDate.value || !customToDate.value) {
      const d = new Date(now)
      d.setDate(now.getDate() - 6)
      customFromDate.value = formatDateToYMD(d)
      customToDate.value = formatDateToYMD(now)
    }
  }
}

function onCustomDateChange() {
  dateValidationError.value = ''
  if (customFromDate.value && customToDate.value) {
    if (customFromDate.value > customToDate.value) {
      dateValidationError.value = t('adminReports.dateRangeError')
      toast.warning(t('adminReports.dateRangeError'))
      return
    }
  }
  loadAllReports()
}

function calculateDateRange() {
  return {
    from: customFromDate.value || undefined,
    to: customToDate.value || undefined,
  }
}

async function loadAllReports() {
  if (selectedPeriod.value === 'custom') {
    if (customFromDate.value && customToDate.value && customFromDate.value > customToDate.value) {
      dateValidationError.value = t('adminReports.dateRangeError')
      return
    }
  }

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

async function loadOccupancyPage(page: number) {
  if (page < 0 || (occupancyTotalPages.value > 0 && page >= occupancyTotalPages.value)) return
  occupancyPage.value = page
  const { from, to } = calculateDateRange()
  try {
    const occ = await reportService.getShowtimeOccupancy({ from, to, page, size: 10 })
    occupancyData.value = occ?.content || []
    occupancyTotalPages.value = occ?.totalPages || 0
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
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

async function handleExport(format: ReportFormat) {
  if (isExportingXlsx.value || isExportingCsv.value) return

  if (format === 'XLSX') isExportingXlsx.value = true
  else isExportingCsv.value = true

  try {
    const { from, to } = calculateDateRange()
    const { data, filename } = await reportService.exportReport(
      selectedReportType.value,
      format,
      from,
      to,
      {
        groupBy: groupBy.value,
        sortBy: selectedReportType.value === 'MOVIES' ? movieSortBy.value : 'REVENUE',
        limit: 100,
      }
    )

    reportService.downloadBlob(data, filename)
    toast.success(t('adminReports.exportSuccess'))
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('adminReports.exportError'))
  } finally {
    if (format === 'XLSX') isExportingXlsx.value = false
    else isExportingCsv.value = false
  }
}

watch([selectedPeriod, groupBy, movieSortBy], () => {
  if (selectedPeriod.value !== 'custom') {
    loadAllReports()
  }
})

onMounted(() => {
  setPeriod('7d')
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
        <!-- Export Excel XLSX Button -->
        <Button
          variant="secondary"
          size="sm"
          :loading="isExportingXlsx"
          :disabled="isExportingXlsx || isExportingCsv"
          @click="handleExport('XLSX')"
        >
          <template #prefix>
            <svg class="w-4 h-4 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
          </template>
          {{ t('adminReports.exportExcel') }}
        </Button>

        <!-- Export CSV Button -->
        <Button
          variant="secondary"
          size="sm"
          :loading="isExportingCsv"
          :disabled="isExportingXlsx || isExportingCsv"
          @click="handleExport('CSV')"
        >
          <template #prefix>
            <svg class="w-4 h-4 text-sky-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
          </template>
          {{ t('adminReports.exportCsv') }}
        </Button>

        <!-- Refresh Button -->
        <Button
          variant="primary"
          size="sm"
          :loading="isLoading"
          :disabled="isLoading || isExportingXlsx || isExportingCsv"
          @click="loadAllReports"
        >
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

    <!-- Top KPI Summary Cards -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4" :aria-busy="isLoading">
      <Card padding="md" class="border-emerald-500/30 bg-gradient-to-br from-slate-900 to-emerald-950/20">
        <p class="text-xs font-semibold text-emerald-400 uppercase tracking-wider">{{ t('adminReports.kpiNetRevenue') }}</p>
        <div v-if="isLoading" class="mt-1 space-y-2">
          <div class="h-8 w-36 bg-slate-800 rounded animate-pulse"></div>
          <div class="flex items-center justify-between text-[11px] text-slate-400 pt-2 border-t border-slate-800">
            <span>{{ t('adminReports.kpiGrossRevenue') }}:</span>
            <div class="h-3.5 w-24 bg-slate-800 rounded animate-pulse"></div>
          </div>
        </div>
        <template v-else>
          <p class="text-2xl font-black text-white mt-1 font-mono">
            {{ formatCurrency(dashboardData?.financial?.netRevenue || 0) }}
          </p>
          <div class="flex items-center justify-between text-[11px] text-slate-400 mt-2 pt-2 border-t border-slate-800">
            <span>{{ t('adminReports.kpiGrossRevenue') }}:</span>
            <span class="font-mono text-slate-200">{{ formatCurrency(dashboardData?.financial?.grossRevenue || 0) }}</span>
          </div>
        </template>
      </Card>

      <Card padding="md" class="border-indigo-500/30 bg-gradient-to-br from-slate-900 to-indigo-950/20">
        <p class="text-xs font-semibold text-indigo-400 uppercase tracking-wider">{{ t('adminReports.kpiNetTickets') }}</p>
        <div v-if="isLoading" class="mt-1 space-y-2">
          <div class="h-8 w-24 bg-slate-800 rounded animate-pulse"></div>
          <div class="flex items-center justify-between text-[11px] text-slate-400 pt-2 border-t border-slate-800">
            <span>{{ t('adminReports.kpiRefundedTickets') }}:</span>
            <div class="h-3.5 w-16 bg-slate-800 rounded animate-pulse"></div>
          </div>
        </div>
        <template v-else>
          <p class="text-2xl font-black text-white mt-1 font-mono">
            {{ formatNumber(dashboardData?.tickets?.netTicketsSold || 0) }}
          </p>
          <div class="flex items-center justify-between text-[11px] text-slate-400 mt-2 pt-2 border-t border-slate-800">
            <span>{{ t('adminReports.kpiRefundedTickets') }}:</span>
            <span class="font-mono text-rose-400">{{ formatNumber(dashboardData?.tickets?.refundedTickets || 0) }}</span>
          </div>
        </template>
      </Card>

      <Card padding="md" class="border-sky-500/30 bg-gradient-to-br from-slate-900 to-sky-950/20">
        <p class="text-xs font-semibold text-sky-400 uppercase tracking-wider">{{ t('adminReports.kpiOccupancyRate') }}</p>
        <div v-if="isLoading" class="mt-1 space-y-2">
          <div class="h-8 w-24 bg-slate-800 rounded animate-pulse"></div>
          <div class="flex items-center justify-between text-[11px] text-slate-400 pt-2 border-t border-slate-800">
            <span>{{ t('adminDashboard.kpiActiveShowtimes') }}:</span>
            <div class="h-3.5 w-16 bg-slate-800 rounded animate-pulse"></div>
          </div>
        </div>
        <template v-else>
          <p class="text-2xl font-black text-white mt-1 font-mono">
            {{ formatPercent(dashboardData?.operations?.averageOccupancyRate || 0) }}
          </p>
          <div class="flex items-center justify-between text-[11px] text-slate-400 mt-2 pt-2 border-t border-slate-800">
            <span>{{ t('adminDashboard.kpiActiveShowtimes') }}:</span>
            <span class="font-mono text-slate-200">{{ formatNumber(dashboardData?.operations?.totalShowtimes || 0) }}</span>
          </div>
        </template>
      </Card>

      <Card padding="md" class="border-purple-500/30 bg-gradient-to-br from-slate-900 to-purple-950/20">
        <p class="text-xs font-semibold text-purple-400 uppercase tracking-wider">{{ t('adminReports.kpiTotalUsers') }}</p>
        <div v-if="isLoading" class="mt-1 space-y-2">
          <div class="h-8 w-24 bg-slate-800 rounded animate-pulse"></div>
          <div class="flex items-center justify-between text-[11px] text-slate-400 pt-2 border-t border-slate-800">
            <span>{{ t('adminReports.kpiActiveUsers') }}:</span>
            <div class="h-3.5 w-16 bg-slate-800 rounded animate-pulse"></div>
          </div>
        </div>
        <template v-else>
          <p class="text-2xl font-black text-white mt-1 font-mono">
            {{ formatNumber(userStats?.totalUsers || 0) }}
          </p>
          <div class="flex items-center justify-between text-[11px] text-slate-400 mt-2 pt-2 border-t border-slate-800">
            <span>{{ t('adminReports.kpiActiveUsers') }}:</span>
            <span class="font-mono text-emerald-400">{{ formatNumber(userStats?.activeUsers || 0) }}</span>
          </div>
        </template>
      </Card>
    </div>

    <!-- Filters Bar -->
    <Card padding="sm">
      <div class="flex flex-col gap-4">
        <!-- Row 1: Report Type Selector -->
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-800/80 pb-3">
          <div class="flex items-center gap-2 flex-wrap">
            <span class="text-xs text-slate-400 font-medium">{{ t('adminReports.filterReportType') }}:</span>
            <div class="flex rounded-lg bg-slate-900 p-1 border border-slate-800 flex-wrap gap-1">
              <button
                :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', selectedReportType === 'REVENUE' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200']"
                @click="selectedReportType = 'REVENUE'"
              >
                {{ t('adminReports.tabRevenueTrend') }}
              </button>
              <button
                :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', selectedReportType === 'MOVIES' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200']"
                @click="selectedReportType = 'MOVIES'"
              >
                {{ t('adminReports.tabMoviePerformance') }}
              </button>
              <button
                :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', selectedReportType === 'CINEMAS' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200']"
                @click="selectedReportType = 'CINEMAS'"
              >
                {{ t('adminReports.tabCinemaPerformance') }}
              </button>
              <button
                :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', selectedReportType === 'OCCUPANCY' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200']"
                @click="selectedReportType = 'OCCUPANCY'"
              >
                {{ t('adminReports.tabOccupancy') }}
              </button>
            </div>
          </div>

          <!-- Group By (Only for REVENUE) -->
          <div v-if="selectedReportType === 'REVENUE'" class="flex items-center gap-2">
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

          <!-- Sort By (Only for MOVIES) -->
          <div v-if="selectedReportType === 'MOVIES'" class="flex items-center gap-2">
            <span class="text-xs text-slate-400 font-medium">{{ t('adminReports.sortByRevenue') }}:</span>
            <div class="flex rounded-lg bg-slate-900 p-1 border border-slate-800">
              <button
                :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', movieSortBy === 'REVENUE' ? 'bg-indigo-600 text-white' : 'text-slate-400']"
                @click="movieSortBy = 'REVENUE'"
              >
                {{ t('adminReports.sortByRevenue') }}
              </button>
              <button
                :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', movieSortBy === 'TICKETS' ? 'bg-indigo-600 text-white' : 'text-slate-400']"
                @click="movieSortBy = 'TICKETS'"
              >
                {{ t('adminReports.sortByTickets') }}
              </button>
            </div>
          </div>
        </div>

        <!-- Row 2: Date Filters & Custom Inputs -->
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 flex-wrap">
          <!-- Preset Buttons -->
          <div class="flex items-center gap-2 flex-wrap">
            <span class="text-xs text-slate-400 font-medium">{{ t('adminReports.filterPeriod') }}:</span>
            <div class="flex rounded-lg bg-slate-900 p-1 border border-slate-800 flex-wrap gap-1">
              <button
                :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', selectedPeriod === 'TODAY' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200']"
                @click="setPeriod('TODAY')"
              >
                {{ t('adminReports.periodToday') }}
              </button>
              <button
                :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', selectedPeriod === 'YESTERDAY' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200']"
                @click="setPeriod('YESTERDAY')"
              >
                {{ t('adminReports.periodYesterday') }}
              </button>
              <button
                :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', selectedPeriod === '7d' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200']"
                @click="setPeriod('7d')"
              >
                {{ t('adminReports.period7Days') }}
              </button>
              <button
                :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', selectedPeriod === '30d' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200']"
                @click="setPeriod('30d')"
              >
                {{ t('adminReports.period30Days') }}
              </button>
              <button
                :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', selectedPeriod === 'THIS_MONTH' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200']"
                @click="setPeriod('THIS_MONTH')"
              >
                {{ t('adminReports.periodThisMonth') }}
              </button>
              <button
                :class="['px-3 py-1 text-xs rounded-md font-medium transition-colors', selectedPeriod === 'custom' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200']"
                @click="setPeriod('custom')"
              >
                {{ t('adminReports.periodCustom') }}
              </button>
            </div>
          </div>

          <!-- Date Inputs (Shown when custom is selected) -->
          <div v-if="selectedPeriod === 'custom'" class="flex flex-col sm:flex-row sm:items-center gap-2 text-xs flex-wrap">
            <div class="flex items-center gap-2">
              <span class="text-slate-400 font-medium">{{ t('adminReports.fromDate') }}:</span>
              <input
                v-model="customFromDate"
                type="date"
                class="px-2.5 py-1 rounded-md bg-slate-900 border text-white focus:outline-none focus:border-indigo-500 font-mono text-xs"
                :class="dateValidationError ? 'border-rose-500 focus:border-rose-500' : 'border-slate-800'"
                @change="onCustomDateChange"
              />
              <span class="text-slate-400 font-medium">{{ t('adminReports.toDate') }}:</span>
              <input
                v-model="customToDate"
                type="date"
                class="px-2.5 py-1 rounded-md bg-slate-900 border text-white focus:outline-none focus:border-indigo-500 font-mono text-xs"
                :class="dateValidationError ? 'border-rose-500 focus:border-rose-500' : 'border-slate-800'"
                @change="onCustomDateChange"
              />
            </div>
            <span v-if="dateValidationError" class="text-xs text-rose-400 font-medium">
              {{ dateValidationError }}
            </span>
          </div>

          <!-- Applied range badge when preset is active -->
          <div v-else class="text-xs text-slate-400 font-mono bg-slate-900/60 px-2.5 py-1 rounded border border-slate-800/80">
            {{ customFromDate }} &rarr; {{ customToDate }}
          </div>
        </div>
      </div>
    </Card>

    <!-- SECTION A: REVENUE REPORT VIEW -->
    <div v-if="selectedReportType === 'REVENUE'" class="grid grid-cols-1 lg:grid-cols-3 gap-6">
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

        <div v-if="isLoading" class="h-[300px] flex flex-col justify-between p-4 animate-pulse">
          <div class="flex justify-end gap-4">
            <div class="h-3 w-28 bg-slate-800 rounded"></div>
            <div class="h-3 w-24 bg-slate-800 rounded"></div>
          </div>
          <div class="h-[220px] w-full bg-slate-800/30 rounded-lg flex items-end justify-between p-4 gap-3">
            <div v-for="i in 10" :key="i" class="w-full bg-slate-800 rounded-t" :style="{ height: `${20 + (i * 17) % 65}%` }"></div>
          </div>
        </div>
        <LineChart
          v-else
          :labels="revenueChartLabels"
          :series="revenueChartSeries"
          :height="300"
          :y-axis-format="formatCurrency"
          :y-axis-right-format="formatNumber"
          :empty-text="t('adminReports.emptyDataPeriod')"
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

        <div v-if="isLoading" class="h-[260px] flex flex-col sm:flex-row items-center justify-center gap-6 animate-pulse">
          <div class="w-36 h-36 rounded-full border-[18px] border-slate-800 border-t-indigo-600/40 animate-spin"></div>
          <div class="space-y-2.5 w-full max-w-[160px]">
            <div class="h-4 bg-slate-800 rounded"></div>
            <div class="h-4 bg-slate-800 rounded w-4/5"></div>
            <div class="h-4 bg-slate-800 rounded w-3/5"></div>
          </div>
        </div>
        <DonutChart
          v-else
          :segments="bookingStatusSegments"
          :center-value="formatNumber(dashboardData?.bookings?.totalBookings || 0)"
          :center-label="t('adminReports.tabBookingStatus')"
          :empty-text="t('adminReports.emptyDataPeriod')"
        />
      </Card>
    </div>

    <!-- SECTION B: MOVIE PERFORMANCE VIEW -->
    <div v-else-if="selectedReportType === 'MOVIES'" class="space-y-6">
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
        </div>

        <div v-if="isLoading" class="space-y-4 p-2 animate-pulse">
          <div v-for="i in 5" :key="i" class="space-y-2">
            <div class="flex justify-between">
              <div class="h-3.5 w-40 bg-slate-800 rounded"></div>
              <div class="h-3.5 w-20 bg-slate-800 rounded"></div>
            </div>
            <div class="h-2.5 bg-slate-800 rounded-full w-full"></div>
          </div>
        </div>
        <HorizontalBarChart
          v-else
          :items="movieBarItems"
          :value-format="movieSortBy === 'REVENUE' ? formatCurrency : formatNumber"
          :empty-text="t('adminReports.emptyDataPeriod')"
        />
      </Card>

      <!-- Detailed Movies Table -->
      <Card padding="none">
        <div class="overflow-x-auto">
          <table class="w-full text-left text-xs">
            <thead class="bg-slate-900/60 text-slate-400 border-b border-slate-800 font-medium">
              <tr>
                <th class="px-4 py-3">{{ t('adminReports.colRank') }}</th>
                <th class="px-4 py-3">{{ t('adminReports.colMovieTitle') }}</th>
                <th class="px-4 py-3 text-right">{{ t('adminReports.colTicketsSold') }}</th>
                <th class="px-4 py-3 text-right">{{ t('adminReports.colTotalRevenue') }}</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800/60 text-slate-300">
              <template v-if="isLoading">
                <tr v-for="i in 5" :key="`movie-skel-${i}`" class="animate-pulse">
                  <td class="px-4 py-3"><div class="h-4 w-6 bg-slate-800 rounded"></div></td>
                  <td class="px-4 py-3"><div class="h-4 w-48 bg-slate-800 rounded"></div></td>
                  <td class="px-4 py-3"><div class="h-4 w-16 bg-slate-800 rounded ml-auto"></div></td>
                  <td class="px-4 py-3"><div class="h-4 w-24 bg-slate-800 rounded ml-auto"></div></td>
                </tr>
              </template>
              <template v-else>
                <tr v-for="(m, idx) in moviesData" :key="m.movieId" class="hover:bg-slate-900/30 transition-colors">
                  <td class="px-4 py-3 font-mono font-bold text-indigo-400">#{{ idx + 1 }}</td>
                  <td class="px-4 py-3 font-medium text-white">{{ m.movieTitle }}</td>
                  <td class="px-4 py-3 text-right font-mono">{{ formatNumber(m.ticketsSold) }}</td>
                  <td class="px-4 py-3 text-right font-mono font-semibold text-emerald-400">{{ formatCurrency(m.totalRevenue) }}</td>
                </tr>
                <tr v-if="moviesData.length === 0">
                  <td colspan="4" class="px-4 py-8 text-center text-slate-500">
                    <div class="flex flex-col items-center justify-center gap-1.5">
                      <span class="text-xl">🎬</span>
                      <span>{{ t('adminReports.emptyDataPeriod') }}</span>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </Card>
    </div>

    <!-- SECTION C: CINEMA PERFORMANCE VIEW -->
    <div v-else-if="selectedReportType === 'CINEMAS'" class="space-y-6">
      <Card padding="md" class="space-y-4">
        <div class="border-b border-slate-800 pb-3">
          <h3 class="text-sm font-bold text-white uppercase tracking-wider">
            {{ t('adminReports.cinemaPerformanceTitle') }}
          </h3>
          <p class="text-[11px] text-slate-400 mt-0.5">
            {{ t('adminReports.unitVnd') }}
          </p>
        </div>

        <div v-if="isLoading" class="h-[280px] flex items-end justify-between p-4 gap-4 bg-slate-800/20 rounded-lg animate-pulse">
          <div v-for="i in 6" :key="i" class="w-12 bg-slate-800 rounded-t" :style="{ height: `${25 + (i * 14) % 65}%` }"></div>
        </div>
        <BarChart
          v-else
          :labels="cinemaChartLabels"
          :values="cinemaChartValues"
          :height="280"
          :value-format="formatCurrency"
          :empty-text="t('adminReports.emptyDataPeriod')"
        />
      </Card>

      <!-- Detailed Cinemas Table -->
      <Card padding="none">
        <div class="overflow-x-auto">
          <table class="w-full text-left text-xs">
            <thead class="bg-slate-900/60 text-slate-400 border-b border-slate-800 font-medium">
              <tr>
                <th class="px-4 py-3">{{ t('adminReports.colRank') }}</th>
                <th class="px-4 py-3">{{ t('adminReports.colCinemaName') }}</th>
                <th class="px-4 py-3">Thành phố</th>
                <th class="px-4 py-3 text-right">{{ t('adminReports.colShowtimesCount') }}</th>
                <th class="px-4 py-3 text-right">{{ t('adminReports.colTicketsSold') }}</th>
                <th class="px-4 py-3 text-right">{{ t('adminReports.colTotalRevenue') }}</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800/60 text-slate-300">
              <template v-if="isLoading">
                <tr v-for="i in 5" :key="`cinema-skel-${i}`" class="animate-pulse">
                  <td class="px-4 py-3"><div class="h-4 w-6 bg-slate-800 rounded"></div></td>
                  <td class="px-4 py-3"><div class="h-4 w-36 bg-slate-800 rounded"></div></td>
                  <td class="px-4 py-3"><div class="h-4 w-20 bg-slate-800 rounded"></div></td>
                  <td class="px-4 py-3"><div class="h-4 w-12 bg-slate-800 rounded ml-auto"></div></td>
                  <td class="px-4 py-3"><div class="h-4 w-16 bg-slate-800 rounded ml-auto"></div></td>
                  <td class="px-4 py-3"><div class="h-4 w-24 bg-slate-800 rounded ml-auto"></div></td>
                </tr>
              </template>
              <template v-else>
                <tr v-for="(c, idx) in cinemasData" :key="c.cinemaId" class="hover:bg-slate-900/30 transition-colors">
                  <td class="px-4 py-3 font-mono font-bold text-indigo-400">#{{ idx + 1 }}</td>
                  <td class="px-4 py-3 font-medium text-white">{{ c.cinemaName }}</td>
                  <td class="px-4 py-3 text-slate-400">{{ c.city }}</td>
                  <td class="px-4 py-3 text-right font-mono">{{ formatNumber(c.totalShowtimes) }}</td>
                  <td class="px-4 py-3 text-right font-mono">{{ formatNumber(c.ticketsSold) }}</td>
                  <td class="px-4 py-3 text-right font-mono font-semibold text-emerald-400">{{ formatCurrency(c.totalRevenue) }}</td>
                </tr>
                <tr v-if="cinemasData.length === 0">
                  <td colspan="6" class="px-4 py-8 text-center text-slate-500">
                    <div class="flex flex-col items-center justify-center gap-1.5">
                      <span class="text-xl">🏛️</span>
                      <span>{{ t('adminReports.emptyDataPeriod') }}</span>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </Card>
    </div>

    <!-- SECTION D: OCCUPANCY REPORT VIEW -->
    <div v-else-if="selectedReportType === 'OCCUPANCY'" class="space-y-6">
      <Card padding="none">
        <div class="p-4 border-b border-slate-800">
          <h3 class="text-sm font-bold text-white uppercase tracking-wider">
            {{ t('adminReports.occupancyTitle') }}
          </h3>
          <p class="text-[11px] text-slate-400 mt-0.5">
            Thống kê chi tiết tỷ lệ lấp đầy từng suất chiếu trong khoảng thời gian đã chọn
          </p>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full text-left text-xs">
            <thead class="bg-slate-900/60 text-slate-400 border-b border-slate-800 font-medium">
              <tr>
                <th class="px-4 py-3">{{ t('adminReports.colStartTime') }}</th>
                <th class="px-4 py-3">{{ t('adminReports.colMovieTitle') }}</th>
                <th class="px-4 py-3">{{ t('adminReports.colCinemaName') }}</th>
                <th class="px-4 py-3">{{ t('adminReports.colAuditorium') }}</th>
                <th class="px-4 py-3">{{ t('adminReports.colFormat') }}</th>
                <th class="px-4 py-3 text-right">{{ t('adminReports.colTotalCapacity') }}</th>
                <th class="px-4 py-3 text-right">{{ t('adminReports.colOccupiedSeats') }}</th>
                <th class="px-4 py-3 text-right">{{ t('adminReports.colOccupancyRate') }}</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800/60 text-slate-300">
              <template v-if="isLoading">
                <tr v-for="i in 5" :key="`occ-skel-${i}`" class="animate-pulse">
                  <td class="px-4 py-3"><div class="h-4 w-28 bg-slate-800 rounded"></div></td>
                  <td class="px-4 py-3"><div class="h-4 w-36 bg-slate-800 rounded"></div></td>
                  <td class="px-4 py-3"><div class="h-4 w-24 bg-slate-800 rounded"></div></td>
                  <td class="px-4 py-3"><div class="h-4 w-16 bg-slate-800 rounded"></div></td>
                  <td class="px-4 py-3"><div class="h-4 w-10 bg-slate-800 rounded"></div></td>
                  <td class="px-4 py-3"><div class="h-4 w-12 bg-slate-800 rounded ml-auto"></div></td>
                  <td class="px-4 py-3"><div class="h-4 w-12 bg-slate-800 rounded ml-auto"></div></td>
                  <td class="px-4 py-3"><div class="h-4 w-14 bg-slate-800 rounded ml-auto"></div></td>
                </tr>
              </template>
              <template v-else>
                <tr v-for="st in occupancyData" :key="st.showtimeId" class="hover:bg-slate-900/30 transition-colors">
                  <td class="px-4 py-3 font-mono text-slate-300 whitespace-nowrap">{{ formatDateTime(st.startTime) }}</td>
                  <td class="px-4 py-3 font-medium text-white">{{ st.movieTitle }}</td>
                  <td class="px-4 py-3 text-slate-400">{{ st.cinemaName }}</td>
                  <td class="px-4 py-3 text-slate-400">{{ st.auditoriumName }}</td>
                  <td class="px-4 py-3">
                    <span class="px-2 py-0.5 text-[10px] rounded bg-slate-800 text-slate-300 font-mono">{{ st.format }}</span>
                  </td>
                  <td class="px-4 py-3 text-right font-mono">{{ formatNumber(st.totalSeats) }}</td>
                  <td class="px-4 py-3 text-right font-mono text-indigo-400">{{ formatNumber(st.occupiedSeats) }}</td>
                  <td class="px-4 py-3 text-right font-mono font-bold" :class="st.occupancyRate >= 70 ? 'text-emerald-400' : st.occupancyRate >= 40 ? 'text-amber-400' : 'text-slate-400'">
                    {{ formatPercent(st.occupancyRate) }}
                  </td>
                </tr>
                <tr v-if="occupancyData.length === 0">
                  <td colspan="8" class="px-4 py-8 text-center text-slate-500">
                    <div class="flex flex-col items-center justify-center gap-1.5">
                      <span class="text-xl">💺</span>
                      <span>{{ t('adminReports.emptyDataPeriod') }}</span>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>

        <div v-if="occupancyTotalPages > 1" class="p-3 border-t border-slate-800 flex justify-center">
          <Pagination
            :current-page="occupancyPage"
            :total-pages="occupancyTotalPages"
            @page-change="loadOccupancyPage"
          />
        </div>
      </Card>
    </div>
  </div>
</template>

