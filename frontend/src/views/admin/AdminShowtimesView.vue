<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import type { ShowtimeSummaryResponse } from '@/types/showtime.types'
import type { MovieSummaryResponse } from '@/types/movie.types'
import type { CinemaSummaryResponse, AuditoriumResponse } from '@/types/cinema.types'
import showtimeService from '@/services/showtime.service'
import movieService from '@/services/movie.service'
import cinemaService from '@/services/cinema.service'
import { formatCurrency, formatDateTime, formatTime } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Badge from '@/components/common/Badge.vue'
import Pagination from '@/components/common/Pagination.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import Modal from '@/components/common/Modal.vue'
import Input from '@/components/common/Input.vue'
import { useToast } from '@/composables/useToast'
import ShowtimeGenerateModal from '@/components/showtime/ShowtimeGenerateModal.vue'
import ShowtimeCopyModal from '@/components/showtime/ShowtimeCopyModal.vue'
import ShowtimeCalendarView from '@/components/showtime/ShowtimeCalendarView.vue'
import ShowtimeEditModal from '@/components/showtime/ShowtimeEditModal.vue'

const { t } = useI18n()
const toast = useToast()

// View Mode: 'list' | 'calendar'
const activeView = ref<'list' | 'calendar'>('list')

const showtimes = ref<ShowtimeSummaryResponse[]>([])
const isLoading = ref(true)
const errorMessage = ref('')

// Dropdown options
const moviesList = ref<MovieSummaryResponse[]>([])
const cinemasList = ref<CinemaSummaryResponse[]>([])
const filterAuditoriumsList = ref<AuditoriumResponse[]>([])
const createAuditoriumsList = ref<AuditoriumResponse[]>([])

// Filters
const selectedCinemaId = ref('')
const selectedAuditoriumId = ref('')
const selectedMovieId = ref('')
const selectedDate = ref('')
const selectedStatus = ref<string>('')
const selectedFormat = ref<string>('')

// Pagination
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const pageSize = ref(10)

// Modal States
const isCreateModalOpen = ref(false)
const isGenerateModalOpen = ref(false)
const isCopyModalOpen = ref(false)
const isEditModalOpen = ref(false)
const editingShowtime = ref<ShowtimeSummaryResponse | null>(null)
const calendarRef = ref<InstanceType<typeof ShowtimeCalendarView> | null>(null)

// Manual Create Showtime Form
const createForm = ref({
  movieId: '',
  cinemaId: '',
  auditoriumId: '',
  startTime: '',
  basePrice: 90000,
  format: 'TWO_D' as 'TWO_D' | 'THREE_D' | 'IMAX',
  language: 'Tiếng Việt',
  subtitle: '',
})
const isCreating = ref(false)

const selectedCreateMovie = computed(() =>
  moviesList.value.find((m) => m.id === createForm.value.movieId)
)

const calculatedCreateEndTime = computed(() => {
  if (!createForm.value.startTime || !selectedCreateMovie.value?.durationMinutes) return null
  try {
    const d = new Date(createForm.value.startTime)
    d.setMinutes(d.getMinutes() + selectedCreateMovie.value.durationMinutes)
    return formatTime(d.toISOString())
  } catch {
    return null
  }
})

async function fetchFilterOptions() {
  try {
    const [moviesRes, cinemasRes] = await Promise.all([
      movieService.getPublicMovies({ status: 'NOW_SHOWING', size: 100 }),
      cinemaService.getPublicCinemas({ status: 'ACTIVE', size: 100 }),
    ])
    moviesList.value = moviesRes.content || []
    cinemasList.value = cinemasRes.content || []
  } catch (err: any) {
    console.error('Failed to load filter options', err)
  }
}

async function fetchShowtimes() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const res = await showtimeService.getAdminShowtimes({
      cinemaId: selectedCinemaId.value || undefined,
      auditoriumId: selectedAuditoriumId.value || undefined,
      movieId: selectedMovieId.value || undefined,
      date: selectedDate.value || undefined,
      status: selectedStatus.value || undefined,
      format: selectedFormat.value || undefined,
      page: currentPage.value,
      size: pageSize.value,
    })

    showtimes.value = res.content || []
    totalPages.value = res.totalPages || 0
    totalElements.value = res.totalElements || 0
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
  } finally {
    isLoading.value = false
  }
}

async function handleFilterCinemaChange(cinemaId: string) {
  selectedCinemaId.value = cinemaId
  selectedAuditoriumId.value = ''
  filterAuditoriumsList.value = []

  if (cinemaId) {
    try {
      const auds = await cinemaService.getAuditoriumsByCinema(cinemaId)
      filterAuditoriumsList.value = auds || []
    } catch (err) {
      filterAuditoriumsList.value = []
    }
  }
}

async function handleCinemaChangeInCreate(cinemaId: string) {
  createForm.value.cinemaId = cinemaId
  createForm.value.auditoriumId = ''
  createAuditoriumsList.value = []
  if (!cinemaId) return

  try {
    const auds = await cinemaService.getAuditoriumsByCinema(cinemaId)
    createAuditoriumsList.value = auds || []
    if (auds.length > 0) {
      createForm.value.auditoriumId = auds[0].id
    }
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  }
}

function openCreateModal() {
  createForm.value = {
    movieId: moviesList.value[0]?.id || '',
    cinemaId: cinemasList.value[0]?.id || '',
    auditoriumId: '',
    startTime: '',
    basePrice: 90000,
    format: 'TWO_D',
    language: 'Tiếng Việt',
    subtitle: '',
  }
  if (cinemasList.value[0]?.id) {
    handleCinemaChangeInCreate(cinemasList.value[0].id)
  }
  isCreateModalOpen.value = true
}

async function handleCreateShowtime() {
  if (!createForm.value.movieId || !createForm.value.auditoriumId || !createForm.value.startTime) {
    toast.error(t('common.errorTitle'))
    return
  }

  isCreating.value = true
  try {
    const startTimeFormatted =
      createForm.value.startTime.length === 16
        ? `${createForm.value.startTime}:00`
        : createForm.value.startTime

    await showtimeService.createShowtime({
      movieId: createForm.value.movieId,
      auditoriumId: createForm.value.auditoriumId,
      startTime: startTimeFormatted,
      basePrice: Number(createForm.value.basePrice),
      format: createForm.value.format,
      language: createForm.value.language,
      subtitle: createForm.value.subtitle ? createForm.value.subtitle : undefined,
    })
    toast.success(t('common.successTitle'))
    isCreateModalOpen.value = false
    await refreshData()
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  } finally {
    isCreating.value = false
  }
}

function openEditModal(st: ShowtimeSummaryResponse) {
  editingShowtime.value = st
  isEditModalOpen.value = true
}

function handleSelectShowtimeFromCalendar(st: ShowtimeSummaryResponse) {
  openEditModal(st)
}

function handleViewCalendarFromGenerate(_date?: string, cinemaId?: string) {
  if (cinemaId) {
    selectedCinemaId.value = cinemaId
  }
  activeView.value = 'calendar'
  refreshData()
}

async function refreshData() {
  await fetchShowtimes()
  if (calendarRef.value) {
    calendarRef.value.refresh()
  }
}

function onPageChange(page: number) {
  currentPage.value = page
  fetchShowtimes()
}

watch(
  [selectedCinemaId, selectedAuditoriumId, selectedMovieId, selectedDate, selectedStatus, selectedFormat],
  () => {
    currentPage.value = 0
    fetchShowtimes()
  }
)

onMounted(async () => {
  await fetchFilterOptions()
  await fetchShowtimes()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header with Title, Actions & View Mode Toggle -->
    <div class="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-white tracking-tight">{{ t('adminShowtimes.title') }}</h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ t('adminShowtimes.subtitle') }}
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <!-- View Mode Switcher -->
        <div class="flex items-center bg-slate-900 border border-slate-700 rounded-lg p-1 mr-2">
          <button
            type="button"
            class="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition-colors"
            :class="
              activeView === 'list'
                ? 'bg-indigo-600 text-white shadow'
                : 'text-slate-400 hover:text-slate-200'
            "
            @click="activeView = 'list'"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 10h16M4 14h16M4 18h16" />
            </svg>
            {{ t('adminShowtimes.listView') }}
          </button>
          <button
            type="button"
            class="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-semibold transition-colors"
            :class="
              activeView === 'calendar'
                ? 'bg-indigo-600 text-white shadow'
                : 'text-slate-400 hover:text-slate-200'
            "
            @click="activeView = 'calendar'"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            {{ t('adminShowtimes.calendarView') }}
          </button>
        </div>

        <!-- Copy Schedule Button -->
        <Button variant="secondary" size="md" @click="isCopyModalOpen = true">
          <template #prefix>
            <svg class="w-4 h-4 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7v8a2 2 0 002 2h6M8 7V5a2 2 0 012-2h4.586a1 1 0 01.707.293l4.414 4.414a1 1 0 01.293.707V15a2 2 0 01-2 2h-2M8 7H6a2 2 0 00-2 2v10a2 2 0 002 2h8a2 2 0 002-2v-2" />
            </svg>
          </template>
          {{ t('adminShowtimes.copyScheduleBtn') }}
        </Button>

        <!-- Auto Generate Button -->
        <Button variant="secondary" size="md" @click="isGenerateModalOpen = true">
          <template #prefix>
            <svg class="w-4 h-4 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
          </template>
          {{ t('adminShowtimes.autoGenerateBtn') }}
        </Button>

        <!-- Manual Create Button -->
        <Button variant="primary" size="md" @click="openCreateModal">
          <template #prefix>
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
          </template>
          {{ t('adminShowtimes.createBtn') }}
        </Button>
      </div>
    </div>

    <!-- Error Alert -->
    <ErrorAlert v-if="errorMessage" :message="errorMessage" />

    <!-- VIEW MODE: CALENDAR -->
    <div v-if="activeView === 'calendar'">
      <ShowtimeCalendarView
        ref="calendarRef"
        :cinemas-list="cinemasList"
        :initial-cinema-id="selectedCinemaId"
        @select-showtime="handleSelectShowtimeFromCalendar"
      />
    </div>

    <!-- VIEW MODE: LIST -->
    <div v-else class="space-y-4">
      <!-- Filter Bar -->
      <Card padding="sm">
        <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
          <!-- Cinema Filter -->
          <div>
            <select
              :value="selectedCinemaId"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-2 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
              @change="(e: any) => handleFilterCinemaChange(e.target.value)"
            >
              <option value="">{{ t('adminShowtimes.allCinemas') }}</option>
              <option v-for="c in cinemasList" :key="c.id" :value="c.id">
                {{ c.name }}
              </option>
            </select>
          </div>

          <!-- Auditorium Filter -->
          <div>
            <select
              v-model="selectedAuditoriumId"
              :disabled="!selectedCinemaId"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-2 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:opacity-50"
            >
              <option value="">{{ t('adminShowtimes.allAuditoriums') }}</option>
              <option v-for="a in filterAuditoriumsList" :key="a.id" :value="a.id">
                {{ a.name }}
              </option>
            </select>
          </div>

          <!-- Movie Filter -->
          <div>
            <select
              v-model="selectedMovieId"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-2 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="">{{ t('adminShowtimes.allMovies') }}</option>
              <option v-for="m in moviesList" :key="m.id" :value="m.id">
                {{ m.title }}
              </option>
            </select>
          </div>

          <!-- Date Filter -->
          <div>
            <input
              v-model="selectedDate"
              type="date"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-2 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <!-- Status Filter -->
          <div>
            <select
              v-model="selectedStatus"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-2 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="">{{ t('adminShowtimes.allStatuses') }}</option>
              <option value="SCHEDULED">{{ t('adminShowtimes.statusScheduled') }}</option>
              <option value="CANCELLED">{{ t('adminShowtimes.statusCancelled') }}</option>
              <option value="FINISHED">{{ t('adminShowtimes.statusFinished') }}</option>
            </select>
          </div>

          <!-- Format Filter -->
          <div>
            <select
              v-model="selectedFormat"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-2 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="">{{ t('adminShowtimes.allFormats') }}</option>
              <option value="TWO_D">2D</option>
              <option value="THREE_D">3D</option>
              <option value="IMAX">IMAX</option>
            </select>
          </div>
        </div>
      </Card>

      <!-- Showtimes Data Table -->
      <Card padding="none">
        <div class="overflow-x-auto">
          <table class="w-full text-left border-collapse text-sm">
            <thead>
              <tr class="bg-slate-850 border-b border-slate-700/80 text-xs font-semibold uppercase text-slate-400 tracking-wider">
                <th class="px-4 py-3">{{ t('adminShowtimes.colMovie') }}</th>
                <th class="px-4 py-3">{{ t('adminShowtimes.colCinema') }}</th>
                <th class="px-4 py-3">{{ t('adminShowtimes.colStartTime') }}</th>
                <th class="px-4 py-3">{{ t('adminShowtimes.colBasePrice') }}</th>
                <th class="px-4 py-3">{{ t('adminShowtimes.colStatus') }}</th>
                <th class="px-4 py-3 text-right">{{ t('adminShowtimes.colActions') }}</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-700/60">
              <!-- Loading Skeleton Rows -->
              <template v-if="isLoading">
                <tr v-for="i in 6" :key="'skel-st-' + i" class="animate-pulse">
                  <td class="px-4 py-4"><div class="h-4 w-40 bg-slate-800 rounded"></div></td>
                  <td class="px-4 py-4"><div class="h-4 w-32 bg-slate-800 rounded"></div></td>
                  <td class="px-4 py-4"><div class="h-4 w-28 bg-slate-800 rounded"></div></td>
                  <td class="px-4 py-4"><div class="h-4 w-20 bg-slate-800 rounded"></div></td>
                  <td class="px-4 py-4"><div class="h-6 w-20 bg-slate-800 rounded-full"></div></td>
                  <td class="px-4 py-4"><div class="h-6 w-16 bg-slate-800 rounded ml-auto"></div></td>
                </tr>
              </template>

              <!-- Empty State -->
              <tr v-else-if="showtimes.length === 0">
                <td colspan="6" class="px-4 py-16 text-center text-slate-400">
                  <div class="max-w-sm mx-auto space-y-2">
                    <svg class="w-10 h-10 mx-auto text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                    </svg>
                    <p class="text-sm font-medium text-slate-300">{{ t('adminShowtimes.emptyList') }}</p>
                    <p class="text-xs text-slate-500">{{ t('common.emptyDesc') }}</p>
                  </div>
                </td>
              </tr>

              <!-- Data Rows -->
              <tr
                v-for="s in showtimes"
                :key="s.id"
                class="hover:bg-slate-750/70 transition-colors"
              >
                <td class="px-4 py-3.5">
                  <div class="flex items-center gap-3">
                    <img
                      v-if="s.moviePosterUrl"
                      :src="s.moviePosterUrl"
                      :alt="s.movieTitle"
                      class="w-9 h-13 object-cover rounded bg-slate-800 flex-shrink-0"
                    />
                    <div>
                      <p class="font-bold text-slate-100">{{ s.movieTitle }}</p>
                      <p class="text-xs text-indigo-400 mt-0.5">
                        {{ s.format }} • {{ s.language }}
                        <span v-if="s.subtitle"> ({{ s.subtitle }})</span>
                      </p>
                    </div>
                  </div>
                </td>
                <td class="px-4 py-3.5 text-xs text-slate-300">
                  <p class="font-semibold text-slate-200">{{ s.cinemaName }}</p>
                  <p class="text-slate-400 text-[11px] mt-0.5">
                    {{ s.auditoriumName }}
                    <span v-if="s.auditoriumType" class="text-indigo-400"> ({{ s.auditoriumType }})</span>
                  </p>
                </td>
                <td class="px-4 py-3.5 text-xs font-mono">
                  <p class="font-bold text-slate-200">{{ formatDateTime(s.startTime) }}</p>
                  <p class="text-[11px] text-slate-400 mt-0.5">
                    {{ t('showtimes.toTime', { time: formatTime(s.endTime) }) }}
                  </p>
                </td>
                <td class="px-4 py-3.5 font-mono font-bold text-emerald-400 text-xs">
                  {{ formatCurrency(s.basePrice) }}
                </td>
                <td class="px-4 py-3.5">
                  <Badge
                    :variant="
                      s.status === 'SCHEDULED'
                        ? 'success'
                        : s.status === 'CANCELLED'
                        ? 'danger'
                        : 'neutral'
                    "
                  >
                    {{
                      s.status === 'SCHEDULED'
                        ? t('adminShowtimes.statusScheduled')
                        : s.status === 'CANCELLED'
                        ? t('adminShowtimes.statusCancelled')
                        : t('adminShowtimes.statusFinished')
                    }}
                  </Badge>
                </td>
                <td class="px-4 py-3.5 text-right">
                  <div class="flex items-center justify-end gap-2">
                    <Button
                      variant="ghost"
                      size="sm"
                      @click="openEditModal(s)"
                    >
                      {{ t('adminShowtimes.editBtn') }}
                    </Button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Pagination -->
        <div v-if="totalPages > 1" class="p-4 border-t border-slate-700/80">
          <Pagination
            :current-page="currentPage"
            :total-pages="totalPages"
            @page-change="onPageChange"
          />
        </div>
      </Card>
    </div>

    <!-- Manual Create Showtime Modal -->
    <Modal
      v-model="isCreateModalOpen"
      size="lg"
      :title="t('adminShowtimes.createBtn')"
      @close="isCreateModalOpen = false"
    >
      <div class="space-y-4 text-xs sm:text-sm text-slate-300">
        <!-- Movie selection -->
        <div>
          <label class="block text-slate-300 font-medium mb-1">
            {{ t('adminShowtimes.colMovie') }} <span class="text-rose-400">*</span>
          </label>
          <select
            v-model="createForm.movieId"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="" disabled>{{ t('adminShowtimes.allMovies') }}</option>
            <option v-for="m in moviesList" :key="m.id" :value="m.id">
              {{ m.title }} ({{ m.durationMinutes }} {{ t('movies.minutes') }})
            </option>
          </select>
        </div>

        <!-- Cinema & Room -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label class="block text-slate-300 font-medium mb-1">
              {{ t('booking.cinemaInfo') }} <span class="text-rose-400">*</span>
            </label>
            <select
              :value="createForm.cinemaId"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
              @change="(e: any) => handleCinemaChangeInCreate(e.target.value)"
            >
              <option value="" disabled>{{ t('adminShowtimes.allCinemas') }}</option>
              <option v-for="c in cinemasList" :key="c.id" :value="c.id">
                {{ c.name }}
              </option>
            </select>
          </div>

          <div>
            <label class="block text-slate-300 font-medium mb-1">
              {{ t('booking.roomInfo') }} <span class="text-rose-400">*</span>
            </label>
            <select
              v-model="createForm.auditoriumId"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="" disabled>{{ t('booking.roomInfo') }}</option>
              <option v-for="a in createAuditoriumsList" :key="a.id" :value="a.id">
                {{ a.name }} ({{ a.type }})
              </option>
            </select>
          </div>
        </div>

        <!-- Start Time & End Time -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label class="block text-slate-300 font-medium mb-1">
              {{ t('adminShowtimes.colStartTime') }} <span class="text-rose-400">*</span>
            </label>
            <input
              v-model="createForm.startTime"
              type="datetime-local"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div>
            <label class="block text-slate-300 font-medium mb-1">
              {{ t('adminShowtimes.colEndTime') }}
            </label>
            <div class="bg-slate-900 border border-slate-700/80 rounded-lg px-3 py-2 text-sm text-slate-400 font-mono">
              <span v-if="calculatedCreateEndTime">
                {{ t('adminShowtimes.calculatedEndTime', { time: calculatedCreateEndTime }) }}
              </span>
              <span v-else class="text-slate-500 italic">--</span>
            </div>
          </div>
        </div>

        <!-- Base price, Format, Language, Subtitle -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label class="block text-slate-300 font-medium mb-1">
              {{ t('adminShowtimes.colBasePrice') }} (₫) <span class="text-rose-400">*</span>
            </label>
            <Input v-model="createForm.basePrice" type="number" />
          </div>

          <div>
            <label class="block text-slate-300 font-medium mb-1">
              {{ t('showtimes.format') }}
            </label>
            <select
              v-model="createForm.format"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="TWO_D">2D</option>
              <option value="THREE_D">3D</option>
              <option value="IMAX">IMAX</option>
            </select>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label class="block text-slate-300 font-medium mb-1">
              {{ t('adminShowtimes.generateModal.language') }}
            </label>
            <input
              v-model="createForm.language"
              type="text"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div>
            <label class="block text-slate-300 font-medium mb-1">
              {{ t('adminShowtimes.generateModal.subtitleLang') }}
            </label>
            <input
              v-model="createForm.subtitle"
              type="text"
              placeholder="VD: Phụ đề Tiếng Việt"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end gap-3">
          <Button variant="secondary" size="md" @click="isCreateModalOpen = false">
            {{ t('common.cancel') }}
          </Button>
          <Button variant="primary" size="md" :loading="isCreating" @click="handleCreateShowtime">
            {{ t('common.confirm') }}
          </Button>
        </div>
      </template>
    </Modal>

    <!-- Auto Generate Schedule Modal -->
    <ShowtimeGenerateModal
      v-model="isGenerateModalOpen"
      :movies-list="moviesList"
      :cinemas-list="cinemasList"
      @success="refreshData"
      @view-calendar="handleViewCalendarFromGenerate"
    />

    <!-- Copy Schedule Modal -->
    <ShowtimeCopyModal
      v-model="isCopyModalOpen"
      :cinemas-list="cinemasList"
      @success="refreshData"
    />

    <!-- Edit Showtime Modal -->
    <ShowtimeEditModal
      v-model="isEditModalOpen"
      :showtime="editingShowtime"
      :movies-list="moviesList"
      :cinemas-list="cinemasList"
      @success="refreshData"
      @deleted="refreshData"
    />
  </div>
</template>
