<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import type { ShowtimeSummaryResponse, ShowtimeFormat } from '@/types/showtime.types'
import type { MovieSummaryResponse } from '@/types/movie.types'
import type { CinemaSummaryResponse } from '@/types/cinema.types'
import showtimeService from '@/services/showtime.service'
import movieService from '@/services/movie.service'
import cinemaService from '@/services/cinema.service'
import { formatCurrency, formatDuration } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import DateSelector from '@/components/showtime/DateSelector.vue'
import Badge from '@/components/common/Badge.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

interface MovieShowtimeGroup {
  movieId: string
  movieTitle: string
  posterUrl?: string
  durationMinutes?: number
  ageRating?: string
  genres?: { id: number; name: string }[]
  cinemas: {
    cinemaId: string
    cinemaName: string
    cinemaCity?: string
    formats: {
      format: ShowtimeFormat
      showtimes: ShowtimeSummaryResponse[]
    }[]
  }[]
}

const router = useRouter()
const route = useRoute()
const { t, locale } = useI18n()

// Today ISO
const getTodayIso = () => {
  const d = new Date()
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

// Filter States
const selectedDate = ref<string>((route.query.date as string) || getTodayIso())
const selectedCity = ref<string>((route.query.city as string) || '')
const selectedMovieId = ref<string>((route.query.movieId as string) || '')
const selectedCinemaId = ref<string>((route.query.cinemaId as string) || '')
const selectedFormat = ref<string>((route.query.format as string) || 'ALL')

const moviesList = ref<MovieSummaryResponse[]>([])
const cinemasList = ref<CinemaSummaryResponse[]>([])
const showtimes = ref<ShowtimeSummaryResponse[]>([])

const isLoading = ref<boolean>(true)
const errorMessage = ref<string>('')

// Formats available in general
const formats: ShowtimeFormat[] = ['2D', '3D', 'IMAX']

// Dynamically derived list of cities from cinemas
const availableCities = computed<string[]>(() => {
  const cities = new Set<string>()
  cinemasList.value.forEach((c) => {
    if (c.city && c.city.trim()) {
      cities.add(c.city.trim())
    }
  })
  return Array.from(cities).sort()
})

// Filtered cinemas matching the selected city
const filteredCinemasList = computed<CinemaSummaryResponse[]>(() => {
  if (!selectedCity.value) {
    return cinemasList.value
  }
  return cinemasList.value.filter((c) => c.city === selectedCity.value)
})

async function loadFilterOptions() {
  try {
    const [moviesRes, cinemasRes] = await Promise.all([
      movieService.getPublicMovies({ status: 'NOW_SHOWING', size: 50, sort: 'title,asc' }),
      cinemaService.getPublicCinemas({ status: 'ACTIVE', size: 50, sort: 'name,asc' }),
    ])
    moviesList.value = moviesRes.content || []
    cinemasList.value = cinemasRes.content || []
  } catch (err) {
    console.warn('Failed to load filter options', err)
  }
}

function normalizeFormat(fmt?: string): ShowtimeFormat {
  if (!fmt) return '2D'
  if (fmt === 'TWO_D') return '2D'
  if (fmt === 'THREE_D') return '3D'
  return fmt as ShowtimeFormat
}

async function fetchShowtimes() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const formatParam = selectedFormat.value !== 'ALL' ? (selectedFormat.value as ShowtimeFormat) : undefined
    const firstPage = await showtimeService.getPublicShowtimes({
      date: selectedDate.value,
      movieId: selectedMovieId.value || undefined,
      cinemaId: selectedCinemaId.value || undefined,
      format: formatParam,
      size: 50,
      page: 0,
      sort: 'startTime,asc',
    })

    const allContent: ShowtimeSummaryResponse[] = [...(firstPage.content || [])]

    if (firstPage.totalPages > 1) {
      const pagePromises = []
      for (let p = 1; p < firstPage.totalPages; p++) {
        pagePromises.push(
          showtimeService
            .getPublicShowtimes({
              date: selectedDate.value,
              movieId: selectedMovieId.value || undefined,
              cinemaId: selectedCinemaId.value || undefined,
              format: formatParam,
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

    // Update query params in URL
    const query: Record<string, string> = { date: selectedDate.value }
    if (selectedCity.value) query.city = selectedCity.value
    if (selectedMovieId.value) query.movieId = selectedMovieId.value
    if (selectedCinemaId.value) query.cinemaId = selectedCinemaId.value
    if (selectedFormat.value !== 'ALL') query.format = selectedFormat.value
    router.replace({ query })
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
    showtimes.value = []
  } finally {
    isLoading.value = false
  }
}

// Group showtimes by Movie -> Cinema -> Format with City filtering
const groupedByMovie = computed<MovieShowtimeGroup[]>(() => {
  const movieMap = new Map<string, MovieShowtimeGroup>()

  showtimes.value.forEach((st) => {
    // If a city is selected, ensure the showtime's cinema belongs to that city
    if (selectedCity.value && st.cinemaCity && st.cinemaCity !== selectedCity.value) {
      return
    }

    const mId = st.movieId || 'unknown-movie'
    if (!movieMap.has(mId)) {
      movieMap.set(mId, {
        movieId: mId,
        movieTitle: st.movieTitle || 'Movie',
        posterUrl: st.moviePosterUrl,
        durationMinutes: st.movieDurationMinutes,
        ageRating: st.movieAgeRating,
        cinemas: [],
      })
    }

    const movieGroup = movieMap.get(mId)!
    const cId = st.cinemaId || 'unknown-cinema'
    let cinemaGroup = movieGroup.cinemas.find((c) => c.cinemaId === cId)

    if (!cinemaGroup) {
      cinemaGroup = {
        cinemaId: cId,
        cinemaName: st.cinemaName || 'Rạp CineBook',
        cinemaCity: st.cinemaCity,
        formats: [],
      }
      movieGroup.cinemas.push(cinemaGroup)
    }

    const fmt = normalizeFormat(st.format)
    let formatGroup = cinemaGroup.formats.find((f) => f.format === fmt)

    if (!formatGroup) {
      formatGroup = { format: fmt, showtimes: [] }
      cinemaGroup.formats.push(formatGroup)
    }

    formatGroup.showtimes.push(st)
  })

  return Array.from(movieMap.values())
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
  router.push({
    name: 'booking',
    query: { showtimeId: showtime.id },
  })
}

function getAgeRatingBadgeVariant(rating?: string): 'success' | 'warning' | 'danger' | 'primary' | 'neutral' {
  if (!rating) return 'neutral'
  const r = rating.toUpperCase()
  if (r.includes('P') || r.includes('ALL')) return 'success'
  if (r.includes('13') || r.includes('K')) return 'primary'
  if (r.includes('16')) return 'warning'
  if (r.includes('18') || r.includes('C')) return 'danger'
  return 'neutral'
}

function resetFilters() {
  selectedCity.value = ''
  selectedMovieId.value = ''
  selectedCinemaId.value = ''
  selectedFormat.value = 'ALL'
  selectedDate.value = getTodayIso()
}

watch(selectedCity, (newCity) => {
  if (newCity && selectedCinemaId.value) {
    const stillValid = filteredCinemasList.value.some((c) => c.id === selectedCinemaId.value)
    if (!stillValid) {
      selectedCinemaId.value = ''
    }
  }
})

watch(
  () => [selectedDate.value, selectedCity.value, selectedMovieId.value, selectedCinemaId.value, selectedFormat.value],
  () => {
    fetchShowtimes()
  }
)

onMounted(async () => {
  await loadFilterOptions()
  fetchShowtimes()
})
</script>

<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-12 space-y-8">
    <!-- Header -->
    <div class="space-y-1">
      <h1 class="text-2xl sm:text-3xl font-bold text-white tracking-tight">{{ t('showtimesView.title') }}</h1>
      <p class="text-xs sm:text-sm text-slate-400">{{ t('showtimesView.subtitle') }}</p>
    </div>

    <!-- Date Selector (14 Days) -->
    <div class="p-4 sm:p-5 rounded-2xl bg-slate-800/90 border border-slate-700/80 shadow-md space-y-4">
      <DateSelector v-model="selectedDate" />

      <!-- Secondary Filters Bar -->
      <div class="pt-3 border-t border-slate-700/70 grid grid-cols-1 sm:grid-cols-12 gap-3 items-center">
        <!-- City Filter Dropdown -->
        <div class="sm:col-span-3">
          <select
            v-model="selectedCity"
            class="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-slate-700 text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-colors"
          >
            <option value="">{{ t('showtimes.allCities') || 'Tất cả thành phố' }}</option>
            <option
              v-for="city in availableCities"
              :key="city"
              :value="city"
            >
              📍 {{ city }}
            </option>
          </select>
        </div>

        <!-- Cinema Filter Dropdown -->
        <div class="sm:col-span-3">
          <select
            v-model="selectedCinemaId"
            class="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-slate-700 text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-colors"
          >
            <option value="">{{ t('showtimesView.allCinemas') }}</option>
            <option
              v-for="c in filteredCinemasList"
              :key="c.id"
              :value="c.id"
            >
              {{ c.name }} {{ c.city && !selectedCity ? `(${c.city})` : '' }}
            </option>
          </select>
        </div>

        <!-- Movie Filter Dropdown -->
        <div class="sm:col-span-3">
          <select
            v-model="selectedMovieId"
            class="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-slate-700 text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-colors"
          >
            <option value="">{{ t('showtimesView.allMovies') }}</option>
            <option
              v-for="m in moviesList"
              :key="m.id"
              :value="m.id"
            >
              {{ m.title }}
            </option>
          </select>
        </div>

        <!-- Format Filter Pills -->
        <div class="sm:col-span-3 flex items-center justify-start sm:justify-end gap-1.5 overflow-x-auto">
          <button
            type="button"
            :class="[
              'px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors shrink-0',
              selectedFormat === 'ALL'
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'bg-slate-900 text-slate-400 hover:text-slate-200 border border-slate-700',
            ]"
            @click="selectedFormat = 'ALL'"
          >
            {{ t('showtimesView.allFormats') }}
          </button>
          <button
            v-for="fmt in formats"
            :key="fmt"
            type="button"
            :class="[
              'px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors shrink-0',
              selectedFormat === fmt
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'bg-slate-900 text-slate-400 hover:text-slate-200 border border-slate-700',
            ]"
            @click="selectedFormat = fmt"
          >
            {{ fmt }}
          </button>
        </div>
      </div>
    </div>

    <!-- Error Alert -->
    <ErrorAlert
      v-if="errorMessage"
      :message="errorMessage"
      @retry="fetchShowtimes"
    />

    <!-- Loading Skeleton Grid (Maintains stable height and layout) -->
    <div v-else-if="isLoading" class="space-y-6 animate-pulse">
      <div v-for="n in 3" :key="n" class="p-6 rounded-2xl bg-slate-800/60 border border-slate-700/60 flex flex-col md:flex-row gap-6">
        <div class="w-32 aspect-[2/3] rounded-xl bg-slate-700 shrink-0"></div>
        <div class="flex-1 space-y-4">
          <div class="w-64 h-6 rounded bg-slate-700"></div>
          <div class="w-36 h-4 rounded bg-slate-700"></div>
          <div class="h-28 rounded-xl bg-slate-700/50"></div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div
      v-else-if="groupedByMovie.length === 0"
      class="p-16 rounded-2xl bg-slate-850/50 border border-slate-800 text-center space-y-4"
    >
      <div class="w-16 h-16 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center mx-auto text-slate-500">
        <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
      </div>
      <div class="space-y-1 max-w-md mx-auto">
        <h3 class="text-lg font-bold text-white">{{ t('showtimesView.emptyTitle') }}</h3>
        <p class="text-xs sm:text-sm text-slate-400">
          {{ t('showtimesView.emptyDesc') }}
        </p>
      </div>
      <button
        type="button"
        class="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-750 text-xs font-semibold text-indigo-400 border border-slate-700 transition-colors"
        @click="resetFilters"
      >
        {{ t('showtimesView.clearFilters') }}
      </button>
    </div>

    <!-- Grouped Showtimes List (Movie -> Cinema -> Format) -->
    <div v-else class="space-y-6">
      <div
        v-for="movieGroup in groupedByMovie"
        :key="movieGroup.movieId"
        class="p-5 sm:p-6 rounded-2xl bg-slate-800/90 border border-slate-700/80 shadow-md flex flex-col md:flex-row gap-6 items-start"
      >
        <!-- Left: Movie Mini Poster & Metadata -->
        <div class="flex md:flex-col items-center md:items-start gap-4 shrink-0 w-full md:w-44">
          <router-link
            :to="`/movies/${movieGroup.movieId}`"
            class="block w-28 md:w-full aspect-[2/3] rounded-xl overflow-hidden bg-slate-900 border border-slate-700 shrink-0 group relative shadow-md"
          >
            <img
              v-if="movieGroup.posterUrl"
              :src="movieGroup.posterUrl"
              :alt="movieGroup.movieTitle"
              class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
            />
            <div v-else class="w-full h-full flex items-center justify-center p-2 text-center text-slate-500 text-xs bg-slate-850">
              {{ movieGroup.movieTitle }}
            </div>
          </router-link>

          <div class="space-y-1.5 flex-1">
            <div class="flex items-center gap-1.5">
              <Badge
                v-if="movieGroup.ageRating"
                :variant="getAgeRatingBadgeVariant(movieGroup.ageRating)"
                size="sm"
                class="font-black"
              >
                {{ movieGroup.ageRating }}
              </Badge>
            </div>

            <router-link
              :to="`/movies/${movieGroup.movieId}`"
              class="text-sm sm:text-base font-bold text-white hover:text-indigo-400 transition-colors line-clamp-2"
              :title="movieGroup.movieTitle"
            >
              {{ movieGroup.movieTitle }}
            </router-link>

            <p v-if="movieGroup.durationMinutes" class="text-xs text-slate-400 font-medium">
              ⏱️ {{ formatDuration(movieGroup.durationMinutes, locale) }}
            </p>
          </div>
        </div>

        <!-- Right: Cinemas & Time Slots -->
        <div class="flex-1 w-full space-y-4 divide-y divide-slate-700/60">
          <div
            v-for="(cinema, cIdx) in movieGroup.cinemas"
            :key="cinema.cinemaId"
            :class="['space-y-3', cIdx > 0 ? 'pt-4' : '']"
          >
            <!-- Cinema Header -->
            <div class="flex items-center gap-2">
              <div class="w-2.5 h-2.5 rounded-full bg-indigo-500"></div>
              <h3 class="text-sm sm:text-base font-bold text-slate-100">
                {{ cinema.cinemaName }}
              </h3>
              <span v-if="cinema.cinemaCity" class="text-xs text-slate-400">
                ({{ cinema.cinemaCity }})
              </span>
            </div>

            <!-- Formats and Slots -->
            <div
              v-for="fmtGroup in cinema.formats"
              :key="fmtGroup.format"
              class="space-y-2 pl-4 border-l-2 border-slate-700/80"
            >
              <div class="flex items-center gap-2">
                <span class="px-2 py-0.5 rounded bg-indigo-950 text-indigo-300 border border-indigo-800 text-[11px] font-bold uppercase">
                  {{ fmtGroup.format }}
                </span>
                <span class="text-[11px] text-slate-400">
                  {{ t('showtimesView.slotsCount', { count: fmtGroup.showtimes.length }) }}
                </span>
              </div>

              <!-- Time Slot Badges Grid -->
              <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-2.5">
                <button
                  v-for="st in fmtGroup.showtimes"
                  :key="st.id"
                  type="button"
                  class="group/slot flex flex-col items-center justify-center p-2.5 rounded-xl bg-slate-900 border border-slate-700 hover:border-indigo-500 hover:bg-slate-750 hover:shadow-lg hover:shadow-indigo-500/10 active:scale-95 transition-all duration-150 cursor-pointer text-center focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  @click="handleSelectShowtime(st)"
                >
                  <span class="text-base sm:text-lg font-black text-white group-hover/slot:text-indigo-300 transition-colors">
                    {{ formatTime(st.startTime) }}
                  </span>
                  <span class="text-[10px] text-slate-400 mt-0.5">
                    ~{{ formatTime(st.endTime) }}
                  </span>
                  <span v-if="st.auditoriumName" class="text-[10px] text-slate-400 line-clamp-1">
                    {{ st.auditoriumName }}
                  </span>
                  <span class="text-[11px] font-bold text-emerald-400 mt-1">
                    {{ formatCurrency(st.basePrice) }}
                  </span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
