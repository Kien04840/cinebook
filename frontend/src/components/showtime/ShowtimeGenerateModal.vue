<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { MovieSummaryResponse } from '@/types/movie.types'
import type { CinemaSummaryResponse, AuditoriumResponse } from '@/types/cinema.types'
import type {
  ShowtimeGenerationRequest,
  ShowtimeGenerationPreviewResponse,
  ShowtimeGenerationResultResponse,
  ShowtimeSlotPreviewResponse,
} from '@/types/showtime.types'
import showtimeService from '@/services/showtime.service'
import cinemaService from '@/services/cinema.service'
import { formatTime } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import { useToast } from '@/composables/useToast'
import Modal from '@/components/common/Modal.vue'
import Button from '@/components/common/Button.vue'
import Badge from '@/components/common/Badge.vue'
import Input from '@/components/common/Input.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

interface Props {
  modelValue: boolean
  moviesList: MovieSummaryResponse[]
  cinemasList: CinemaSummaryResponse[]
}

interface SelectedMovieProgram {
  movie: MovieSummaryResponse
  targetScreeningsPerDay: number
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'success'): void
  (e: 'view-calendar', date: string, cinemaId: string): void
}>()

const { t } = useI18n()
const toast = useToast()

// Steps: 1 = Program, 2 = Resources & Settings, 3 = Review & Generate, 4 = Result
const currentStep = ref<1 | 2 | 3 | 4>(1)
const isPreviewing = ref(false)
const isGenerating = ref(false)
const errorMessage = ref('')

// Step 1: Program State (Multi-movie selection + quota)
const selectedMovies = ref<SelectedMovieProgram[]>([])
const movieSearchQuery = ref('')
const dateMode = ref<'single' | 'range'>('single')
const startDate = ref('')
const endDate = ref('')

// Step 2: Resources & Settings State
const selectedCinemaId = ref('')
const selectedAuditoriumIds = ref<string[]>([])
const format = ref<'TWO_D' | 'THREE_D' | 'IMAX'>('TWO_D')
const language = ref('Tiếng Việt')
const subtitle = ref('')
const basePrice = ref<number>(90000)

const showAdvanced = ref(false)
const openingTime = ref('08:00')
const closingTime = ref('23:30')
const snapIntervalMinutes = ref<number>(15)

const availableAuditoriums = ref<AuditoriumResponse[]>([])
const isLoadingAuditoriums = ref(false)

// Step 3 & 4 State
const previewData = ref<ShowtimeGenerationPreviewResponse | null>(null)
const resultData = ref<ShowtimeGenerationResultResponse | null>(null)

// Movie Color Palette for Timeline
const MOVIE_COLORS = [
  { bg: 'bg-indigo-950/80', border: 'border-indigo-500/80', text: 'text-indigo-300', dot: 'bg-indigo-400' },
  { bg: 'bg-emerald-950/80', border: 'border-emerald-500/80', text: 'text-emerald-300', dot: 'bg-emerald-400' },
  { bg: 'bg-amber-950/80', border: 'border-amber-500/80', text: 'text-amber-300', dot: 'bg-amber-400' },
  { bg: 'bg-rose-950/80', border: 'border-rose-500/80', text: 'text-rose-300', dot: 'bg-rose-400' },
  { bg: 'bg-purple-950/80', border: 'border-purple-500/80', text: 'text-purple-300', dot: 'bg-purple-400' },
  { bg: 'bg-cyan-950/80', border: 'border-cyan-500/80', text: 'text-cyan-300', dot: 'bg-cyan-400' },
  { bg: 'bg-orange-950/80', border: 'border-orange-500/80', text: 'text-orange-300', dot: 'bg-orange-400' },
  { bg: 'bg-teal-950/80', border: 'border-teal-500/80', text: 'text-teal-300', dot: 'bg-teal-400' },
]

function getMovieColor(movieId: string) {
  const idx = selectedMovies.value.findIndex((m) => m.movie.id === movieId)
  if (idx >= 0) return MOVIE_COLORS[idx % MOVIE_COLORS.length]
  return MOVIE_COLORS[0]
}

// Filtered movies available to add in Step 1
const availableMoviesToSelect = computed(() => {
  const selectedIds = new Set(selectedMovies.value.map((m) => m.movie.id))
  const q = movieSearchQuery.value.trim().toLowerCase()
  return props.moviesList.filter((m) => {
    if (selectedIds.has(m.id)) return false
    if (!q) return true
    return m.title.toLowerCase().includes(q)
  })
})

const totalTargetScreenings = computed(() => {
  return selectedMovies.value.reduce((sum, item) => sum + (item.targetScreeningsPerDay || 0), 0)
})

const canProceedToStep2 = computed(() => {
  if (selectedMovies.value.length === 0) return false
  if (!startDate.value) return false
  if (dateMode.value === 'range' && !endDate.value) return false
  if (dateMode.value === 'range' && endDate.value < startDate.value) return false
  return selectedMovies.value.every((m) => m.targetScreeningsPerDay > 0)
})

const canProceedToPreview = computed(() => {
  if (!canProceedToStep2.value) return false
  if (!selectedCinemaId.value) return false
  if (selectedAuditoriumIds.value.length === 0) return false
  if (!basePrice.value || basePrice.value < 0) return false
  return true
})

// Capacity Hint Calculation
const capacityHint = computed(() => {
  const audCount = selectedAuditoriumIds.value.length
  if (audCount === 0 || selectedMovies.value.length === 0) return null

  const openTime = openingTime.value || '08:00'
  const closeTime = closingTime.value || '23:30'

  const [oh, om] = openTime.split(':').map(Number)
  const [ch, cm] = closeTime.split(':').map(Number)
  let opMinutes = (ch * 60 + cm) - (oh * 60 + om)
  if (opMinutes <= 0) opMinutes = 15 * 60

  const avgDuration =
    selectedMovies.value.reduce((acc, m) => acc + (m.movie.durationMinutes || 120), 0) /
    selectedMovies.value.length
  const avgTurnaround = 15
  const slotsPerAud = Math.max(1, Math.floor((opMinutes + avgTurnaround) / (avgDuration + avgTurnaround)))
  const estimatedCapacity = audCount * slotsPerAud
  const requested = totalTargetScreenings.value

  let status: 'feasible' | 'tight' | 'overload' = 'feasible'
  if (requested > estimatedCapacity) {
    status = 'overload'
  } else if (requested > estimatedCapacity * 0.85) {
    status = 'tight'
  }

  return {
    audCount,
    openTime,
    closeTime,
    estimatedCapacity,
    requested,
    status,
  }
})

// Group Preview Slots by Auditorium
const groupedPreviewSlots = computed(() => {
  if (!previewData.value || !previewData.value.slots) return []
  const groups: Record<
    string,
    { auditoriumId: string; auditoriumName: string; slots: ShowtimeSlotPreviewResponse[] }
  > = {}
  for (const slot of previewData.value.slots) {
    if (!groups[slot.auditoriumId]) {
      groups[slot.auditoriumId] = {
        auditoriumId: slot.auditoriumId,
        auditoriumName: slot.auditoriumName,
        slots: [],
      }
    }
    groups[slot.auditoriumId].slots.push(slot)
  }
  return Object.values(groups)
})

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      resetForm()
    }
  }
)

function resetForm() {
  currentStep.value = 1
  errorMessage.value = ''
  selectedMovies.value = []
  if (props.moviesList.length > 0) {
    selectedMovies.value.push({
      movie: props.moviesList[0],
      targetScreeningsPerDay: 4,
    })
  }
  movieSearchQuery.value = ''
  dateMode.value = 'single'
  const todayStr = new Date().toISOString().slice(0, 10)
  startDate.value = todayStr
  endDate.value = ''

  selectedCinemaId.value = props.cinemasList[0]?.id || ''
  selectedAuditoriumIds.value = []
  format.value = 'TWO_D'
  language.value = 'Tiếng Việt'
  subtitle.value = ''
  basePrice.value = 90000
  showAdvanced.value = false
  openingTime.value = '08:00'
  closingTime.value = '23:30'
  snapIntervalMinutes.value = 15

  availableAuditoriums.value = []
  previewData.value = null
  resultData.value = null

  if (selectedCinemaId.value) {
    handleCinemaChange(selectedCinemaId.value)
  }
}

function addMovieToProgram(movie: MovieSummaryResponse) {
  if (selectedMovies.value.some((m) => m.movie.id === movie.id)) return
  selectedMovies.value.push({
    movie,
    targetScreeningsPerDay: 4,
  })
  movieSearchQuery.value = ''
}

function removeMovieFromProgram(movieId: string) {
  selectedMovies.value = selectedMovies.value.filter((m) => m.movie.id !== movieId)
}

function incrementTarget(movieId: string) {
  const item = selectedMovies.value.find((m) => m.movie.id === movieId)
  if (item) {
    item.targetScreeningsPerDay += 1
  }
}

function decrementTarget(movieId: string) {
  const item = selectedMovies.value.find((m) => m.movie.id === movieId)
  if (item && item.targetScreeningsPerDay > 1) {
    item.targetScreeningsPerDay -= 1
  }
}

function updateTarget(movieId: string, value: number) {
  const item = selectedMovies.value.find((m) => m.movie.id === movieId)
  if (item) {
    item.targetScreeningsPerDay = Math.max(1, value || 1)
  }
}

async function handleCinemaChange(cinemaId: string) {
  selectedCinemaId.value = cinemaId
  selectedAuditoriumIds.value = []
  availableAuditoriums.value = []
  if (!cinemaId) return

  isLoadingAuditoriums.value = true
  try {
    const [auds, config] = await Promise.all([
      cinemaService.getAuditoriumsByCinema(cinemaId),
      showtimeService.getCinemaSchedulingConfig(cinemaId).catch(() => null),
    ])
    availableAuditoriums.value = auds || []
    selectedAuditoriumIds.value = auds.filter((a) => a.status === 'ACTIVE').map((a) => a.id)

    if (config) {
      if (config.openingTime) openingTime.value = config.openingTime.slice(0, 5)
      if (config.closingTime) closingTime.value = config.closingTime.slice(0, 5)
    }
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  } finally {
    isLoadingAuditoriums.value = false
  }
}

function toggleAuditorium(id: string) {
  const index = selectedAuditoriumIds.value.indexOf(id)
  if (index >= 0) {
    selectedAuditoriumIds.value.splice(index, 1)
  } else {
    selectedAuditoriumIds.value.push(id)
  }
}

function selectAllAuditoriums() {
  selectedAuditoriumIds.value = availableAuditoriums.value.map((a) => a.id)
}

function deselectAllAuditoriums() {
  selectedAuditoriumIds.value = []
}

function buildPayload(): ShowtimeGenerationRequest {
  const payload: ShowtimeGenerationRequest = {
    movies: selectedMovies.value.map((item) => ({
      movieId: item.movie.id,
      targetScreeningsPerDay: item.targetScreeningsPerDay,
      format: format.value,
      language: language.value,
      subtitle: subtitle.value ? subtitle.value : undefined,
      basePrice: Number(basePrice.value),
    })),
    auditoriumIds: selectedAuditoriumIds.value,
    startDate: startDate.value,
    endDate: dateMode.value === 'range' && endDate.value ? endDate.value : undefined,
    format: format.value,
    language: language.value,
    subtitle: subtitle.value ? subtitle.value : undefined,
    basePrice: Number(basePrice.value),
  }

  if (openingTime.value) {
    payload.openingTime =
      openingTime.value.length === 5 ? `${openingTime.value}:00` : openingTime.value
  }
  if (closingTime.value) {
    payload.closingTime =
      closingTime.value.length === 5 ? `${closingTime.value}:00` : closingTime.value
  }
  if (typeof snapIntervalMinutes.value === 'number' && snapIntervalMinutes.value > 0) {
    payload.snapIntervalMinutes = snapIntervalMinutes.value
  }

  return payload
}

async function handlePreview() {
  if (!canProceedToPreview.value) return
  isPreviewing.value = true
  errorMessage.value = ''

  try {
    const payload = buildPayload()
    const res = await showtimeService.previewGeneration(payload)
    previewData.value = res
    currentStep.value = 3
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
    toast.error(errorMessage.value)
  } finally {
    isPreviewing.value = false
  }
}

async function handleGenerate() {
  if (!previewData.value || previewData.value.totalValid === 0) return
  isGenerating.value = true
  errorMessage.value = ''

  try {
    const payload = buildPayload()
    const res = await showtimeService.generateShowtimes(payload)
    resultData.value = res
    currentStep.value = 4
    emit('success')
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
    toast.error(errorMessage.value)
  } finally {
    isGenerating.value = false
  }
}

function handleViewCalendar() {
  emit('view-calendar', startDate.value, selectedCinemaId.value)
  closeModal()
}

function closeModal() {
  emit('update:modelValue', false)
}
</script>

<template>
  <Modal
    :model-value="modelValue"
    size="xl"
    :title="t('adminShowtimes.generateModal.title')"
    @close="closeModal"
  >
    <div class="space-y-5 text-sm text-slate-300">
      <!-- Step Indicator -->
      <div
        class="flex items-center justify-between border-b border-slate-700/80 pb-3 text-xs font-semibold uppercase tracking-wider"
      >
        <!-- Step 1 -->
        <div
          class="flex items-center gap-2"
          :class="
            currentStep === 1
              ? 'text-indigo-400'
              : currentStep > 1
                ? 'text-emerald-400'
                : 'text-slate-500'
          "
        >
          <span
            class="w-6 h-6 rounded-full flex items-center justify-center text-xs"
            :class="
              currentStep === 1
                ? 'bg-indigo-600/30 border border-indigo-500 text-white'
                : currentStep > 1
                  ? 'bg-emerald-600/30 border border-emerald-500 text-emerald-300'
                  : 'bg-slate-800 border border-slate-700 text-slate-500'
            "
          >
            1
          </span>
          <span>{{ t('adminShowtimes.generateModal.step1') }}</span>
        </div>

        <div class="w-8 sm:w-12 h-px bg-slate-700"></div>

        <!-- Step 2 -->
        <div
          class="flex items-center gap-2"
          :class="
            currentStep === 2
              ? 'text-indigo-400'
              : currentStep > 2
                ? 'text-emerald-400'
                : 'text-slate-500'
          "
        >
          <span
            class="w-6 h-6 rounded-full flex items-center justify-center text-xs"
            :class="
              currentStep === 2
                ? 'bg-indigo-600/30 border border-indigo-500 text-white'
                : currentStep > 2
                  ? 'bg-emerald-600/30 border border-emerald-500 text-emerald-300'
                  : 'bg-slate-800 border border-slate-700 text-slate-500'
            "
          >
            2
          </span>
          <span>{{ t('adminShowtimes.generateModal.step2') }}</span>
        </div>

        <div class="w-8 sm:w-12 h-px bg-slate-700"></div>

        <!-- Step 3 -->
        <div
          class="flex items-center gap-2"
          :class="
            currentStep === 3
              ? 'text-indigo-400'
              : currentStep === 4
                ? 'text-emerald-400'
                : 'text-slate-500'
          "
        >
          <span
            class="w-6 h-6 rounded-full flex items-center justify-center text-xs"
            :class="
              currentStep === 3
                ? 'bg-indigo-600/30 border border-indigo-500 text-white'
                : currentStep === 4
                  ? 'bg-emerald-600/30 border border-emerald-500 text-emerald-300'
                  : 'bg-slate-800 border border-slate-700 text-slate-500'
            "
          >
            3
          </span>
          <span>{{ t('adminShowtimes.generateModal.step3') }}</span>
        </div>
      </div>

      <!-- Error Alert -->
      <ErrorAlert v-if="errorMessage" :message="errorMessage" />

      <!-- ========================================== -->
      <!-- STEP 1: PROGRAM CONFIGURATION (MOVIES & QUOTA) -->
      <!-- ========================================== -->
      <div v-if="currentStep === 1" class="space-y-4">
        <p class="text-xs text-slate-400 leading-relaxed">
          {{ t('adminShowtimes.generateModal.subtitle') }}
        </p>

        <!-- Date Range & Mode -->
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 bg-slate-850 p-3.5 rounded-xl border border-slate-700/60">
          <div>
            <label class="block text-xs font-medium text-slate-300 mb-1">
              {{ t('adminShowtimes.generateModal.dateMode') }}
            </label>
            <div class="flex gap-2">
              <button
                type="button"
                class="flex-1 py-1.5 px-3 rounded-lg border text-xs font-medium transition-colors"
                :class="
                  dateMode === 'single'
                    ? 'bg-indigo-600 border-indigo-500 text-white shadow'
                    : 'bg-slate-900 border-slate-700 text-slate-300 hover:border-slate-600'
                "
                @click="dateMode = 'single'"
              >
                {{ t('adminShowtimes.generateModal.singleDay') }}
              </button>
              <button
                type="button"
                class="flex-1 py-1.5 px-3 rounded-lg border text-xs font-medium transition-colors"
                :class="
                  dateMode === 'range'
                    ? 'bg-indigo-600 border-indigo-500 text-white shadow'
                    : 'bg-slate-900 border-slate-700 text-slate-300 hover:border-slate-600'
                "
                @click="dateMode = 'range'"
              >
                {{ t('adminShowtimes.generateModal.dateRange') }}
              </button>
            </div>
          </div>

          <div>
            <label class="block text-xs font-medium text-slate-300 mb-1">
              {{ t('adminShowtimes.generateModal.startDate') }} <span class="text-rose-400">*</span>
            </label>
            <input
              v-model="startDate"
              type="date"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div v-if="dateMode === 'range'">
            <label class="block text-xs font-medium text-slate-300 mb-1">
              {{ t('adminShowtimes.generateModal.endDate') }} <span class="text-rose-400">*</span>
            </label>
            <input
              v-model="endDate"
              type="date"
              :min="startDate"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
        </div>

        <!-- Search & Add Movie Selector -->
        <div>
          <div class="flex items-center justify-between mb-1.5">
            <label class="text-xs font-bold text-slate-200">
              {{ t('adminShowtimes.generateModal.programTitle') }}
            </label>
            <span class="text-xs text-indigo-400 font-mono">
              {{ t('adminShowtimes.generateModal.selectedMoviesCount', { count: selectedMovies.length }) }}
            </span>
          </div>

          <!-- Movie Search Input -->
          <div class="relative">
            <input
              v-model="movieSearchQuery"
              type="text"
              :placeholder="t('adminShowtimes.generateModal.searchMoviePlaceholder')"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg pl-9 pr-3 py-2 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
            <svg
              class="w-4 h-4 text-slate-500 absolute left-3 top-2.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </div>

          <!-- Search Results Dropdown -->
          <div
            v-if="movieSearchQuery && availableMoviesToSelect.length > 0"
            class="mt-1 bg-slate-850 border border-slate-700 rounded-lg shadow-xl max-h-48 overflow-y-auto divide-y divide-slate-700/50 z-20 relative"
          >
            <button
              v-for="m in availableMoviesToSelect"
              :key="m.id"
              type="button"
              class="w-full flex items-center justify-between p-2 hover:bg-slate-800 text-left transition-colors"
              @click="addMovieToProgram(m)"
            >
              <div class="flex items-center gap-2.5">
                <img
                  v-if="m.posterUrl"
                  :src="m.posterUrl"
                  :alt="m.title"
                  class="w-7 h-10 object-cover rounded bg-slate-800"
                />
                <div>
                  <p class="text-xs font-semibold text-slate-200">{{ m.title }}</p>
                  <p class="text-[11px] text-slate-400">
                    {{ m.durationMinutes }} {{ t('movies.minutes') }}
                    <span v-if="m.ageRating"> • {{ m.ageRating }}</span>
                  </p>
                </div>
              </div>
              <span class="text-xs text-indigo-400 font-medium px-2 py-1 bg-indigo-950/60 rounded border border-indigo-800/80">
                + {{ t('adminShowtimes.generateModal.addMovieBtn') }}
              </span>
            </button>
          </div>
        </div>

        <!-- Selected Movies Cards with Quota Stepper -->
        <div class="space-y-2">
          <div
            v-if="selectedMovies.length === 0"
            class="bg-slate-900/60 border border-dashed border-slate-700 rounded-xl p-6 text-center text-xs text-slate-500"
          >
            {{ t('adminShowtimes.generateModal.noMoviesSelected') }}
          </div>

          <div
            v-for="item in selectedMovies"
            :key="item.movie.id"
            class="flex items-center justify-between gap-3 bg-slate-850 p-2.5 rounded-xl border border-slate-700 hover:border-slate-600 transition-colors"
          >
            <!-- Movie Info -->
            <div class="flex items-center gap-3 min-w-0 flex-1">
              <span
                class="w-2.5 h-10 rounded-full flex-shrink-0"
                :class="getMovieColor(item.movie.id).dot"
              ></span>
              <img
                v-if="item.movie.posterUrl"
                :src="item.movie.posterUrl"
                :alt="item.movie.title"
                class="w-8 h-12 object-cover rounded bg-slate-800 flex-shrink-0"
              />
              <div class="min-w-0">
                <p class="font-bold text-slate-100 text-xs truncate">{{ item.movie.title }}</p>
                <div class="flex items-center gap-2 mt-0.5 text-[11px] text-slate-400">
                  <Badge variant="neutral">{{ item.movie.durationMinutes }}m</Badge>
                  <span v-if="item.movie.ageRating">{{ item.movie.ageRating }}</span>
                </div>
              </div>
            </div>

            <!-- Quota Stepper: [-] N [+] -->
            <div class="flex items-center gap-2 flex-shrink-0">
              <div class="flex items-center bg-slate-900 border border-slate-700 rounded-lg p-0.5">
                <button
                  type="button"
                  class="w-7 h-7 flex items-center justify-center text-slate-400 hover:text-white hover:bg-slate-800 rounded disabled:opacity-40 transition-colors"
                  :disabled="item.targetScreeningsPerDay <= 1"
                  @click="decrementTarget(item.movie.id)"
                >
                  −
                </button>
                <input
                  type="number"
                  min="1"
                  max="50"
                  class="w-10 text-center bg-transparent text-xs font-mono font-bold text-indigo-300 focus:outline-none"
                  :value="item.targetScreeningsPerDay"
                  @input="(e: any) => updateTarget(item.movie.id, Number(e.target.value))"
                />
                <button
                  type="button"
                  class="w-7 h-7 flex items-center justify-center text-slate-400 hover:text-white hover:bg-slate-800 rounded transition-colors"
                  @click="incrementTarget(item.movie.id)"
                >
                  +
                </button>
              </div>
              <span class="text-[11px] text-slate-400 hidden sm:inline">
                {{ t('adminShowtimes.generateModal.targetPerDay') }}
              </span>

              <!-- Remove button -->
              <button
                type="button"
                class="text-slate-500 hover:text-rose-400 p-1.5 rounded-lg hover:bg-slate-800 transition-colors ml-1"
                :title="t('adminShowtimes.generateModal.removeMovie')"
                @click="removeMovieFromProgram(item.movie.id)"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            </div>
          </div>
        </div>

        <!-- Program Summary Banner -->
        <div
          v-if="selectedMovies.length > 0"
          class="bg-indigo-950/40 border border-indigo-900/60 p-3 rounded-xl flex items-center justify-between text-xs"
        >
          <span class="text-indigo-300 font-medium">
            {{
              t('adminShowtimes.generateModal.programSummary', {
                movieCount: selectedMovies.length,
                totalTarget: totalTargetScreenings,
              })
            }}
          </span>
          <span class="text-slate-400 font-mono">
            {{ dateMode === 'single' ? startDate : `${startDate} → ${endDate}` }}
          </span>
        </div>
      </div>

      <!-- ========================================== -->
      <!-- STEP 2: RESOURCES & SETTINGS (AUDITORIUMS, HOURS) -->
      <!-- ========================================== -->
      <div v-else-if="currentStep === 2" class="space-y-4">
        <!-- Cinema & Auditoriums Selection -->
        <div class="bg-slate-850 p-4 rounded-xl border border-slate-700/60 space-y-3">
          <div>
            <label class="block text-xs font-semibold text-slate-200 mb-1">
              {{ t('adminShowtimes.generateModal.cinema') }} <span class="text-rose-400">*</span>
            </label>
            <select
              :value="selectedCinemaId"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
              @change="(e: any) => handleCinemaChange(e.target.value)"
            >
              <option value="" disabled>{{ t('adminShowtimes.generateModal.selectCinema') }}</option>
              <option v-for="c in cinemasList" :key="c.id" :value="c.id">
                {{ c.name }} ({{ c.city }})
              </option>
            </select>
          </div>

          <!-- Target Auditoriums -->
          <div>
            <div class="flex items-center justify-between mb-1.5">
              <label class="text-xs font-semibold text-slate-200">
                {{ t('adminShowtimes.generateModal.auditoriums') }} <span class="text-rose-400">*</span>
              </label>
              <div v-if="availableAuditoriums.length > 0" class="flex gap-2 text-[11px]">
                <button
                  type="button"
                  class="text-indigo-400 hover:text-indigo-300"
                  @click="selectAllAuditoriums"
                >
                  {{ t('common.all') }}
                </button>
                <span class="text-slate-600">|</span>
                <button
                  type="button"
                  class="text-slate-400 hover:text-slate-300"
                  @click="deselectAllAuditoriums"
                >
                  {{ t('common.cancel') }}
                </button>
              </div>
            </div>

            <div
              v-if="availableAuditoriums.length === 0"
              class="bg-slate-900/60 border border-dashed border-slate-700 rounded-lg p-3 text-xs text-center text-slate-500"
            >
              {{ t('adminShowtimes.generateModal.noAuditoriums') }}
            </div>
            <div v-else class="grid grid-cols-2 sm:grid-cols-3 gap-2 max-h-36 overflow-y-auto pr-1">
              <button
                v-for="a in availableAuditoriums"
                :key="a.id"
                type="button"
                class="flex items-center gap-2 px-2.5 py-1.5 rounded-lg border text-left text-xs transition-colors"
                :class="
                  selectedAuditoriumIds.includes(a.id)
                    ? 'bg-indigo-950/60 border-indigo-500 text-indigo-200'
                    : 'bg-slate-900 border-slate-700 text-slate-400 hover:border-slate-600'
                "
                @click="toggleAuditorium(a.id)"
              >
                <div
                  class="w-3.5 h-3.5 rounded border flex items-center justify-center text-[10px]"
                  :class="
                    selectedAuditoriumIds.includes(a.id)
                      ? 'bg-indigo-600 border-indigo-500 text-white'
                      : 'border-slate-600'
                  "
                >
                  <svg
                    v-if="selectedAuditoriumIds.includes(a.id)"
                    class="w-2.5 h-2.5"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7" />
                  </svg>
                </div>
                <div class="truncate">
                  <span class="font-medium text-slate-200">{{ a.name }}</span>
                  <span class="text-[10px] text-slate-400 ml-1">({{ a.type }})</span>
                </div>
              </button>
            </div>
          </div>
        </div>

        <!-- Operating Hours & Snap Settings (Strictly NO stagger) -->
        <div class="bg-slate-850 p-4 rounded-xl border border-slate-700/60 space-y-3">
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label class="block text-xs font-medium text-slate-300 mb-1">
                {{ t('adminShowtimes.generateModal.openingTime') }}
              </label>
              <input
                v-model="openingTime"
                type="time"
                class="w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>

            <div>
              <label class="block text-xs font-medium text-slate-300 mb-1">
                {{ t('adminShowtimes.generateModal.closingTime') }}
              </label>
              <input
                v-model="closingTime"
                type="time"
                class="w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>

            <div>
              <label class="block text-xs font-medium text-slate-300 mb-1">
                {{ t('adminShowtimes.generateModal.snapInterval') }}
              </label>
              <select
                v-model.number="snapIntervalMinutes"
                class="w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                <option :value="5">5 phút</option>
                <option :value="10">10 phút</option>
                <option :value="15">15 phút (Khuyên dùng)</option>
                <option :value="30">30 phút</option>
              </select>
            </div>
          </div>

          <!-- Format, Language, Price -->
          <div class="grid grid-cols-1 sm:grid-cols-4 gap-3 pt-2 border-t border-slate-750">
            <div>
              <label class="block text-xs font-medium text-slate-400 mb-1">
                {{ t('adminShowtimes.generateModal.format') }}
              </label>
              <select
                v-model="format"
                class="w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                <option value="TWO_D">2D</option>
                <option value="THREE_D">3D</option>
                <option value="IMAX">IMAX</option>
              </select>
            </div>

            <div>
              <label class="block text-xs font-medium text-slate-400 mb-1">
                {{ t('adminShowtimes.generateModal.language') }}
              </label>
              <input
                v-model="language"
                type="text"
                class="w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>

            <div>
              <label class="block text-xs font-medium text-slate-400 mb-1">
                {{ t('adminShowtimes.generateModal.subtitleLang') }}
              </label>
              <input
                v-model="subtitle"
                type="text"
                placeholder="VD: Phụ đề Tiếng Việt"
                class="w-full bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>

            <div>
              <label class="block text-xs font-medium text-slate-400 mb-1">
                {{ t('adminShowtimes.generateModal.basePrice') }}
              </label>
              <Input v-model="basePrice" type="number" />
            </div>
          </div>
        </div>

        <!-- Capacity Hint Banner -->
        <div
          v-if="capacityHint"
          class="p-3.5 rounded-xl border flex items-center justify-between text-xs"
          :class="
            capacityHint.status === 'feasible'
              ? 'bg-emerald-950/40 border-emerald-800/80 text-emerald-300'
              : capacityHint.status === 'tight'
                ? 'bg-amber-950/40 border-amber-800/80 text-amber-300'
                : 'bg-rose-950/40 border-rose-800/80 text-rose-300'
          "
        >
          <div class="flex items-center gap-2">
            <span
              class="w-2 h-2 rounded-full"
              :class="
                capacityHint.status === 'feasible'
                  ? 'bg-emerald-400'
                  : capacityHint.status === 'tight'
                    ? 'bg-amber-400'
                    : 'bg-rose-400'
              "
            ></span>
            <span>
              {{
                t('adminShowtimes.generateModal.capacityHintText', {
                  audCount: capacityHint.audCount,
                  open: capacityHint.openTime,
                  close: capacityHint.closeTime,
                  capacity: capacityHint.estimatedCapacity,
                  requested: capacityHint.requested,
                })
              }}
            </span>
          </div>
          <Badge
            :variant="
              capacityHint.status === 'feasible'
                ? 'success'
                : capacityHint.status === 'tight'
                  ? 'warning'
                  : 'danger'
            "
          >
            {{
              capacityHint.status === 'feasible'
                ? t('adminShowtimes.generateModal.capacityStatusFeasible')
                : capacityHint.status === 'tight'
                  ? t('adminShowtimes.generateModal.capacityStatusTight')
                  : t('adminShowtimes.generateModal.capacityStatusOverload')
            }}
          </Badge>
        </div>
      </div>

      <!-- ========================================== -->
      <!-- STEP 3: REVIEW & GENERATE (QUOTA, TIMELINE) -->
      <!-- ========================================== -->
      <div v-else-if="currentStep === 3 && previewData" class="space-y-4">
        <!-- Quality Indicators Banner -->
        <div class="bg-slate-850 p-3.5 rounded-xl border border-slate-700 space-y-2">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span
                class="w-2.5 h-2.5 rounded-full"
                :class="previewData.totalUnscheduled === 0 ? 'bg-emerald-400' : 'bg-amber-400'"
              ></span>
              <span class="font-bold text-white text-xs">
                {{
                  previewData.totalUnscheduled === 0
                    ? t('adminShowtimes.generateModal.qualityStatusReady')
                    : t('adminShowtimes.generateModal.qualityStatusWarning')
                }}
              </span>
            </div>
            <div class="flex items-center gap-3 text-xs font-mono">
              <span class="text-slate-400">
                {{ t('adminShowtimes.generateModal.totalRequested', { count: previewData.totalRequested || previewData.totalProposed }) }}
              </span>
              <span class="text-emerald-400 font-bold">
                {{ t('adminShowtimes.generateModal.totalScheduled', { count: previewData.totalScheduled || previewData.totalValid }) }}
              </span>
              <span v-if="(previewData.totalUnscheduled || 0) > 0" class="text-amber-400 font-bold">
                {{ t('adminShowtimes.generateModal.totalUnscheduled', { count: previewData.totalUnscheduled }) }}
              </span>
            </div>
          </div>

          <!-- Bulleted Quality Messages -->
          <div v-if="previewData.qualityIndicators && previewData.qualityIndicators.length > 0" class="space-y-1 pt-1">
            <p
              v-for="(qi, idx) in previewData.qualityIndicators"
              :key="idx"
              class="text-[11px] text-slate-300"
            >
              {{ qi }}
            </p>
          </div>
        </div>

        <!-- Scheduling Warnings Box if any -->
        <div
          v-if="previewData.warnings && previewData.warnings.length > 0"
          class="bg-amber-950/30 border border-amber-800/80 p-3 rounded-xl space-y-1"
        >
          <p class="text-xs font-bold text-amber-300 flex items-center gap-1.5">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            {{ t('adminShowtimes.generateModal.warningsTitle') }}
          </p>
          <p
            v-for="(w, idx) in previewData.warnings"
            :key="idx"
            class="text-[11px] text-amber-200 pl-5"
          >
            • {{ w }}
          </p>
        </div>

        <!-- Quota Progress Table -->
        <div
          v-if="previewData.movieSummaries && previewData.movieSummaries.length > 0"
          class="bg-slate-850 rounded-xl border border-slate-700 overflow-hidden"
        >
          <div class="px-3 py-2 border-b border-slate-700 flex items-center justify-between text-xs font-bold text-slate-200">
            <span>{{ t('adminShowtimes.generateModal.quotaTableTitle') }}</span>
          </div>
          <table class="w-full text-left border-collapse text-xs">
            <thead class="bg-slate-900 text-slate-400 border-b border-slate-700 font-semibold">
              <tr>
                <th class="px-3 py-2">{{ t('adminShowtimes.generateModal.colMovie') }}</th>
                <th class="px-3 py-2 text-center">{{ t('adminShowtimes.generateModal.colTarget') }}</th>
                <th class="px-3 py-2 text-center">{{ t('adminShowtimes.generateModal.colScheduled') }}</th>
                <th class="px-3 py-2 text-center">{{ t('adminShowtimes.generateModal.colRemaining') }}</th>
                <th class="px-3 py-2">{{ t('adminShowtimes.generateModal.colProgress') }}</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-700/50">
              <tr v-for="ms in previewData.movieSummaries" :key="ms.movieId" class="hover:bg-slate-800/40">
                <td class="px-3 py-2 font-medium text-slate-200 flex items-center gap-2">
                  <span class="w-2 h-2 rounded-full" :class="getMovieColor(ms.movieId).dot"></span>
                  {{ ms.movieTitle }}
                </td>
                <td class="px-3 py-2 text-center font-mono font-semibold text-slate-300">
                  {{ ms.targetScreenings }}
                </td>
                <td class="px-3 py-2 text-center font-mono font-bold text-emerald-400">
                  {{ ms.scheduledScreenings }}
                </td>
                <td class="px-3 py-2 text-center font-mono" :class="ms.remainingScreenings > 0 ? 'text-amber-400 font-bold' : 'text-slate-500'">
                  {{ ms.remainingScreenings }}
                </td>
                <td class="px-3 py-2 w-32">
                  <div class="flex items-center gap-2">
                    <div class="flex-1 bg-slate-800 rounded-full h-2 overflow-hidden border border-slate-700">
                      <div
                        class="h-full rounded-full transition-all"
                        :class="ms.remainingScreenings === 0 ? 'bg-emerald-500' : 'bg-amber-500'"
                        :style="{ width: `${Math.min(100, Math.round((ms.scheduledScreenings / (ms.targetScreenings || 1)) * 100))}%` }"
                      ></div>
                    </div>
                    <span class="text-[10px] font-mono text-slate-400">
                      {{ Math.round((ms.scheduledScreenings / (ms.targetScreenings || 1)) * 100) }}%
                    </span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Grouped Timeline by Auditorium -->
        <div class="space-y-2">
          <p class="text-xs font-bold text-slate-200">
            {{ t('adminShowtimes.generateModal.timelineTitle') }}
          </p>

          <div class="space-y-2.5 max-h-60 overflow-y-auto pr-1">
            <div
              v-for="grp in groupedPreviewSlots"
              :key="grp.auditoriumId"
              class="bg-slate-850 rounded-xl border border-slate-700/80 p-2.5"
            >
              <div class="flex items-center justify-between mb-2">
                <span class="font-bold text-slate-100 text-xs">{{ grp.auditoriumName }}</span>
                <span class="text-[10px] text-slate-400 font-mono">
                  {{ grp.slots.length }} suất chiếu
                </span>
              </div>

              <!-- Slots Swimlane / Cards -->
              <div class="flex flex-wrap gap-1.5">
                <div
                  v-for="(slot, idx) in grp.slots"
                  :key="idx"
                  class="rounded-lg p-2 text-left border transition-all text-xs flex flex-col justify-between"
                  :class="[
                    slot.valid
                      ? `${getMovieColor(slot.movieId).bg} ${getMovieColor(slot.movieId).border}`
                      : 'bg-amber-950/40 border-amber-600/80',
                  ]"
                >
                  <div class="flex items-center gap-1.5">
                    <span
                      class="w-1.5 h-1.5 rounded-full"
                      :class="slot.valid ? getMovieColor(slot.movieId).dot : 'bg-amber-400'"
                    ></span>
                    <span class="font-semibold text-slate-100 text-[11px] truncate max-w-[120px]">
                      {{ slot.movieTitle }}
                    </span>
                  </div>
                  <div class="mt-1 flex items-center gap-1.5 text-[10px] font-mono text-slate-300">
                    <span>{{ formatTime(slot.startTime) }} - {{ formatTime(slot.endTime) }}</span>
                    <Badge v-if="!slot.valid" variant="warning" size="sm">Xung đột</Badge>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========================================== -->
      <!-- STEP 4: RESULT SUMMARY -->
      <!-- ========================================== -->
      <div v-else-if="currentStep === 4 && resultData" class="space-y-4 text-center py-4">
        <div
          class="w-12 h-12 rounded-full bg-emerald-950 border border-emerald-500 text-emerald-400 mx-auto flex items-center justify-center shadow-lg"
        >
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <h3 class="text-lg font-bold text-white">{{ t('adminShowtimes.generateModal.resultTitle') }}</h3>

        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 max-w-lg mx-auto text-left">
          <div class="bg-emerald-950/40 p-3 rounded-xl border border-emerald-800">
            <p class="text-xs text-emerald-300 font-medium">
              {{ t('adminShowtimes.generateModal.createdCount', { count: resultData.totalCreated }) }}
            </p>
          </div>
          <div class="bg-slate-850 p-3 rounded-xl border border-slate-700">
            <p class="text-xs text-slate-400 font-medium">
              {{ t('adminShowtimes.generateModal.skippedCount', { count: resultData.totalSkipped }) }}
            </p>
          </div>
          <div class="bg-amber-950/40 p-3 rounded-xl border border-amber-800">
            <p class="text-xs text-amber-300 font-medium">
              {{ t('adminShowtimes.generateModal.conflictedCount', { count: resultData.totalConflicted }) }}
            </p>
          </div>
        </div>
      </div>
    </div>

    <!-- Modal Footer -->
    <template #footer>
      <div class="flex items-center justify-between w-full">
        <div>
          <!-- Back button from step 2 to step 1 -->
          <Button
            v-if="currentStep === 2"
            variant="secondary"
            size="md"
            @click="currentStep = 1"
          >
            {{ t('adminShowtimes.generateModal.backBtn') }}
          </Button>

          <!-- Back button from step 3 to step 2 -->
          <Button
            v-if="currentStep === 3"
            variant="secondary"
            size="md"
            @click="currentStep = 2"
          >
            {{ t('adminShowtimes.generateModal.regenerateBtn') }}
          </Button>
        </div>

        <div class="flex items-center gap-3">
          <Button
            v-if="currentStep !== 4"
            variant="ghost"
            size="md"
            @click="closeModal"
          >
            {{ t('common.cancel') }}
          </Button>

          <!-- Step 1 action: Next to Resources -->
          <Button
            v-if="currentStep === 1"
            variant="primary"
            size="md"
            :disabled="!canProceedToStep2"
            @click="currentStep = 2"
          >
            {{ t('adminShowtimes.generateModal.step2') }}
            <template #suffix>
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
              </svg>
            </template>
          </Button>

          <!-- Step 2 action: Preview -->
          <Button
            v-if="currentStep === 2"
            variant="primary"
            size="md"
            :disabled="!canProceedToPreview"
            :loading="isPreviewing"
            @click="handlePreview"
          >
            {{ t('adminShowtimes.generateModal.previewBtn') }}
          </Button>

          <!-- Step 3 action: Generate -->
          <Button
            v-if="currentStep === 3"
            variant="primary"
            size="md"
            :disabled="!previewData || previewData.totalValid === 0"
            :loading="isGenerating"
            @click="handleGenerate"
          >
            {{
              t('adminShowtimes.generateModal.confirmGenerateBtn', {
                count: previewData?.totalValid || 0,
              })
            }}
          </Button>

          <!-- Step 4 actions: View on Calendar & Close -->
          <template v-if="currentStep === 4">
            <Button
              variant="secondary"
              size="md"
              @click="closeModal"
            >
              {{ t('adminShowtimes.generateModal.finishBtn') }}
            </Button>
            <Button
              variant="primary"
              size="md"
              @click="handleViewCalendar"
            >
              <template #prefix>
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                </svg>
              </template>
              {{ t('adminShowtimes.generateModal.viewScheduleCalendarBtn') }}
            </Button>
          </template>
        </div>
      </div>
    </template>
  </Modal>
</template>
