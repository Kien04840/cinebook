<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { ShowtimeSummaryResponse, CinemaShowtimeGroup, ShowtimeFormat } from '@/types/showtime.types'
import showtimeService from '@/services/showtime.service'
import { formatCurrency } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import DateSelector from './DateSelector.vue'
import Spinner from '@/components/common/Spinner.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

interface Props {
  movieId: string
  movieTitle?: string
}

const props = defineProps<Props>()
const router = useRouter()
const { t } = useI18n()

// Get today ISO string YYYY-MM-DD
const getTodayIso = () => {
  const d = new Date()
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const selectedDate = ref<string>(getTodayIso())
const selectedFormat = ref<string>('ALL') // 'ALL' | '2D' | '3D' | 'IMAX'
const showtimes = ref<ShowtimeSummaryResponse[]>([])
const isLoading = ref<boolean>(false)
const errorMessage = ref<string>('')

async function fetchShowtimes() {
  if (!props.movieId) return

  isLoading.value = true
  errorMessage.value = ''
  try {
    // 1. Fetch first page with size=50
    const firstPage = await showtimeService.getPublicShowtimes({
      movieId: props.movieId,
      date: selectedDate.value,
      size: 50,
      page: 0,
      sort: 'startTime,asc',
    })

    const allContent: ShowtimeSummaryResponse[] = [...(firstPage.content || [])]

    // 2. If there are additional pages, fetch all remaining pages completely
    if (firstPage.totalPages > 1) {
      const pagePromises = []
      for (let p = 1; p < firstPage.totalPages; p++) {
        pagePromises.push(
          showtimeService
            .getPublicShowtimes({
              movieId: props.movieId,
              date: selectedDate.value,
              size: 50,
              page: p,
              sort: 'startTime,asc',
            })
            .catch((err) => {
              console.warn(`Warning: showtime page ${p} fetch failed`, err)
              return null
            })
        )
      }
      const remainingPages = await Promise.all(pagePromises)
      remainingPages.forEach((pageRes) => {
        if (pageRes && pageRes.content) {
          allContent.push(...pageRes.content)
        }
      })
    }

    showtimes.value = allContent
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
    showtimes.value = []
  } finally {
    isLoading.value = false
  }
}

// Available formats present in current showtimes
const availableFormats = computed<ShowtimeFormat[]>(() => {
  const set = new Set<ShowtimeFormat>()
  showtimes.value.forEach((s) => {
    if (s.format) set.add(s.format)
  })
  return Array.from(set)
})

// Filter showtimes by selected format (client-side)
const filteredShowtimes = computed<ShowtimeSummaryResponse[]>(() => {
  if (selectedFormat.value === 'ALL') {
    return showtimes.value
  }
  return showtimes.value.filter((s) => s.format === selectedFormat.value)
})

// Group filtered showtimes by Cinema -> Format
const groupedByCinema = computed<CinemaShowtimeGroup[]>(() => {
  const cinemaMap = new Map<string, CinemaShowtimeGroup>()

  filteredShowtimes.value.forEach((st) => {
    const cId = st.cinemaId || 'unknown-cinema'
    if (!cinemaMap.has(cId)) {
      cinemaMap.set(cId, {
        cinemaId: cId,
        cinemaName: st.cinemaName || 'Rạp CineBook',
        cinemaCity: st.cinemaCity,
        formats: [],
      })
    }

    const cinemaGroup = cinemaMap.get(cId)!
    const fmt = st.format || '2D'
    let formatGroup = cinemaGroup.formats.find((f) => f.format === fmt)

    if (!formatGroup) {
      formatGroup = { format: fmt, showtimes: [] }
      cinemaGroup.formats.push(formatGroup)
    }

    formatGroup.showtimes.push(st)
  })

  return Array.from(cinemaMap.values())
})

function formatTime(isoString?: string): string {
  if (!isoString) return '--:--'
  try {
    const d = new Date(isoString)
    const hours = String(d.getHours()).padStart(2, '0')
    const minutes = String(d.getMinutes()).padStart(2, '0')
    return `${hours}:${minutes}`
  } catch {
    return isoString
  }
}

function handleSelectShowtime(showtime: ShowtimeSummaryResponse) {
  // Handoff to booking module without creating transaction
  router.push({
    name: 'booking',
    query: { showtimeId: showtime.id },
  })
}

watch(
  () => [props.movieId, selectedDate.value],
  () => {
    fetchShowtimes()
  }
)

onMounted(() => {
  fetchShowtimes()
})
</script>

<template>
  <div class="space-y-6">
    <div class="space-y-4">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 class="text-xl font-bold text-white tracking-tight flex items-center gap-2">
            <svg class="w-5 h-5 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            {{ t('showtime.title') }}
          </h2>
          <p class="text-xs sm:text-sm text-slate-400 mt-0.5">{{ t('showtime.subtitle') }}</p>
        </div>

        <!-- Format Filter Pills if formats available -->
        <div v-if="availableFormats.length > 1" class="flex items-center gap-1.5 bg-slate-800 p-1 rounded-xl border border-slate-700/80">
          <button
            type="button"
            :class="[
              'px-3 py-1 text-xs font-semibold rounded-lg transition-colors',
              selectedFormat === 'ALL'
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200',
            ]"
            @click="selectedFormat = 'ALL'"
          >
            {{ t('showtime.allFormats') }}
          </button>
          <button
            v-for="fmt in availableFormats"
            :key="fmt"
            type="button"
            :class="[
              'px-3 py-1 text-xs font-semibold rounded-lg transition-colors',
              selectedFormat === fmt
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200',
            ]"
            @click="selectedFormat = fmt"
          >
            {{ fmt }}
          </button>
        </div>
      </div>

      <!-- 14-Day Date Selector -->
      <DateSelector v-model="selectedDate" />
    </div>

    <!-- Error Alert -->
    <ErrorAlert
      v-if="errorMessage"
      :message="errorMessage"
      class="my-4"
      @retry="fetchShowtimes"
    />

    <!-- Loading State -->
    <div v-else-if="isLoading" class="p-12 rounded-2xl bg-slate-850/60 border border-slate-800 flex flex-col items-center justify-center space-y-3">
      <Spinner size="md" color="text-indigo-500" />
      <p class="text-xs text-slate-400">{{ t('showtime.loading') }}</p>
    </div>

    <!-- Empty State -->
    <div
      v-else-if="groupedByCinema.length === 0"
      class="p-10 rounded-2xl bg-slate-850/40 border border-slate-800 text-center space-y-3"
    >
      <div class="w-12 h-12 rounded-full bg-slate-800 flex items-center justify-center mx-auto text-slate-500">
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      </div>
      <div class="space-y-1">
        <h3 class="text-sm sm:text-base font-bold text-slate-200">{{ t('showtime.emptyTitle') }}</h3>
        <p class="text-xs text-slate-400 max-w-sm mx-auto">
          {{ t('showtime.emptyDesc') }}
        </p>
      </div>
    </div>

    <!-- Cinema Grouped Showtimes List -->
    <div v-else class="space-y-4">
      <div
        v-for="cinema in groupedByCinema"
        :key="cinema.cinemaId"
        class="rounded-2xl bg-slate-800/90 border border-slate-700/80 overflow-hidden shadow-sm"
      >
        <!-- Cinema Header -->
        <div class="p-4 bg-slate-850/80 border-b border-slate-700/80 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
          <div class="flex items-center gap-2.5">
            <div class="w-8 h-8 rounded-lg bg-indigo-950 text-indigo-400 border border-indigo-800 flex items-center justify-center shrink-0">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
              </svg>
            </div>
            <div>
              <h3 class="text-sm sm:text-base font-bold text-white tracking-tight">
                {{ cinema.cinemaName }}
              </h3>
              <p v-if="cinema.cinemaCity" class="text-xs text-slate-400">
                📍 {{ cinema.cinemaCity }}
              </p>
            </div>
          </div>
        </div>

        <!-- Formats and Showtimes Inside Cinema -->
        <div class="p-4 sm:p-5 space-y-4 divide-y divide-slate-700/40">
          <div
            v-for="(fGroup, idx) in cinema.formats"
            :key="fGroup.format"
            :class="['space-y-3', idx > 0 ? 'pt-4' : '']"
          >
            <div class="flex items-center gap-2">
              <span class="px-2.5 py-0.5 rounded-md bg-indigo-950 text-indigo-300 border border-indigo-800 text-xs font-bold uppercase">
                {{ fGroup.format }}
              </span>
              <span class="text-xs text-slate-400">
                {{ t('showtime.slotsCount', { count: fGroup.showtimes.length }) }}
              </span>
            </div>

            <!-- Showtime Slots Grid -->
            <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-2.5">
              <button
                v-for="st in fGroup.showtimes"
                :key="st.id"
                type="button"
                class="group/slot flex flex-col items-center justify-center p-3 rounded-xl bg-slate-900/80 border border-slate-700 hover:border-indigo-500 hover:bg-slate-750 hover:shadow-lg hover:shadow-indigo-500/10 active:scale-95 transition-all duration-150 cursor-pointer text-center focus:outline-none focus:ring-2 focus:ring-indigo-500"
                @click="handleSelectShowtime(st)"
              >
                <!-- Start Time -->
                <span class="text-base sm:text-lg font-black text-white group-hover/slot:text-indigo-300 transition-colors">
                  {{ formatTime(st.startTime) }}
                </span>

                <!-- End Time & Auditorium -->
                <span class="text-[11px] text-slate-400 line-clamp-1 mt-0.5">
                  ~{{ formatTime(st.endTime) }}
                </span>

                <span v-if="st.auditoriumName" class="text-[10px] text-slate-400 line-clamp-1">
                  {{ st.auditoriumName }}
                </span>

                <!-- Price -->
                <span class="text-[11px] font-bold text-emerald-400 mt-1.5">
                  {{ formatCurrency(st.basePrice) }}
                </span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
