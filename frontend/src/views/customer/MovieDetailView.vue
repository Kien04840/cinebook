<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MovieDetailResponse } from '@/types/movie.types'
import movieService from '@/services/movie.service'
import { formatDuration, formatDate } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Badge from '@/components/common/Badge.vue'
import Button from '@/components/common/Button.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import ShowtimeBrowser from '@/components/showtime/ShowtimeBrowser.vue'
import TrailerModal from '@/components/movie/TrailerModal.vue'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()

const movie = ref<MovieDetailResponse | null>(null)
const isLoading = ref<boolean>(true)
const errorMessage = ref<string>('')
const is404 = ref<boolean>(false)

const isTrailerOpen = ref<boolean>(false)
const posterError = ref<boolean>(false)
const posterLoaded = ref<boolean>(false)
const backdropLoaded = ref<boolean>(false)

async function fetchMovieDetail() {
  const movieId = route.params.id as string
  if (!movieId) {
    is404.value = true
    return
  }

  isLoading.value = true
  errorMessage.value = ''
  is404.value = false
  posterError.value = false
  posterLoaded.value = false
  backdropLoaded.value = false

  try {
    const data = await movieService.getMovieDetail(movieId)
    movie.value = data
    document.title = `${data.title} — CineBook`
  } catch (err: any) {
    if (err.response?.status === 404) {
      is404.value = true
    } else {
      errorMessage.value =
        err.response?.data?.message || 'Không thể tải thông tin chi tiết phim. Vui lòng thử lại.'
    }
  } finally {
    isLoading.value = false
  }
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

watch(
  () => route.params.id,
  () => {
    fetchMovieDetail()
  }
)

onMounted(() => {
  fetchMovieDetail()
})
</script>

<template>
  <div class="space-y-12 pb-20">
    <!-- 404 State -->
    <div v-if="is404" class="max-w-3xl mx-auto px-4 py-20 text-center space-y-6">
      <div class="w-20 h-20 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center mx-auto text-indigo-400 text-3xl font-black shadow-lg">
        404
      </div>
      <div class="space-y-2">
        <h1 class="text-2xl font-bold text-white">{{ t('movieDetail.notFoundTitle') }}</h1>
        <p class="text-sm text-slate-400">{{ t('movieDetail.notFoundDesc') }}</p>
      </div>
      <router-link to="/movies">
        <Button variant="primary" size="md">{{ t('movieDetail.backToCatalog') }}</Button>
      </router-link>
    </div>

    <!-- Error State -->
    <div v-else-if="errorMessage" class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-8">
      <ErrorAlert :message="errorMessage" @retry="fetchMovieDetail" />
    </div>

    <!-- Layout-Stable Loading Skeleton (Prevents Page Collapse and Footer Bouncing) -->
    <div v-else-if="isLoading" class="space-y-12 animate-pulse">
      <!-- Backdrop Hero Skeleton -->
      <section class="relative w-full bg-slate-950 border-b border-slate-800 overflow-hidden py-12">
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div class="w-20 h-4 rounded bg-slate-800 mb-6"></div>
          <div class="grid grid-cols-1 md:grid-cols-12 gap-8 items-start">
            <div class="md:col-span-4 lg:col-span-3">
              <div class="w-full aspect-[2/3] rounded-2xl bg-slate-800"></div>
            </div>
            <div class="md:col-span-8 lg:col-span-9 space-y-4">
              <div class="flex gap-2">
                <div class="w-12 h-6 rounded bg-slate-800"></div>
                <div class="w-20 h-6 rounded bg-slate-800"></div>
              </div>
              <div class="w-3/4 h-10 rounded bg-slate-800"></div>
              <div class="w-1/3 h-5 rounded bg-slate-800"></div>
              <div class="grid grid-cols-3 gap-4 py-4 border-y border-slate-800">
                <div class="h-10 rounded bg-slate-800"></div>
                <div class="h-10 rounded bg-slate-800"></div>
                <div class="h-10 rounded bg-slate-800"></div>
              </div>
              <div class="w-full h-24 rounded bg-slate-800"></div>
            </div>
          </div>
        </div>
      </section>

      <!-- Showtime Section Skeleton -->
      <section class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-4 space-y-6">
        <div class="space-y-2">
          <div class="w-48 h-7 rounded bg-slate-800"></div>
          <div class="w-72 h-4 rounded bg-slate-800"></div>
        </div>
        <div class="flex gap-2 overflow-x-auto pb-2">
          <div v-for="n in 7" :key="n" class="min-w-[80px] h-20 rounded-2xl bg-slate-800"></div>
        </div>
        <div class="h-44 rounded-2xl bg-slate-800"></div>
      </section>
    </div>

    <!-- Movie Detail Content -->
    <template v-else-if="movie">
      <!-- Backdrop Hero Section (High Definition & Crisp) -->
      <section class="relative w-full bg-slate-950 border-b border-slate-800 overflow-hidden">
        <!-- Crisp Ambient Backdrop -->
        <div class="absolute inset-0 z-0">
          <img
            v-if="movie.backdropUrl || movie.posterUrl"
            :src="movie.backdropUrl || movie.posterUrl"
            :alt="movie.title"
            :class="[
              'w-full h-full object-cover object-center transition-opacity duration-700 opacity-50',
              backdropLoaded ? 'opacity-50' : 'opacity-0'
            ]"
            @load="backdropLoaded = true"
          />
          <div class="absolute inset-0 bg-gradient-to-t from-slate-900 via-slate-900/80 to-slate-900/40"></div>
          <div class="absolute inset-0 bg-gradient-to-r from-slate-900 via-slate-900/70 to-transparent"></div>
        </div>

        <div class="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-12">
          <!-- Back Link -->
          <button
            type="button"
            class="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-400 hover:text-white transition-colors mb-6 cursor-pointer focus:outline-none"
            @click="router.back()"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
            {{ t('movieDetail.back') }}
          </button>

          <!-- Main Layout: 2:3 Poster on left + Info on right -->
          <div class="grid grid-cols-1 md:grid-cols-12 gap-8 items-start">
            <!-- Left: Poster Column (4 cols) -->
            <div class="md:col-span-4 lg:col-span-3 flex flex-col items-center sm:items-start">
              <div class="w-full max-w-[280px] md:max-w-none aspect-[2/3] rounded-2xl overflow-hidden bg-slate-900 border-2 border-slate-700/80 shadow-2xl shadow-slate-950 relative">
                <!-- Skeleton loader behind poster -->
                <div
                  v-if="!posterLoaded && !posterError"
                  class="absolute inset-0 bg-slate-800 animate-pulse flex items-center justify-center"
                ></div>

                <img
                  v-if="movie.posterUrl && !posterError"
                  :src="movie.posterUrl"
                  :alt="movie.title"
                  :class="[
                    'w-full h-full object-cover transition-opacity duration-500',
                    posterLoaded ? 'opacity-100' : 'opacity-0'
                  ]"
                  @load="posterLoaded = true"
                  @error="posterError = true"
                />
                <div v-else class="w-full h-full flex flex-col items-center justify-center p-6 text-center text-slate-500 bg-slate-850">
                  <svg class="w-16 h-16 mb-2 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M7 4v16M17 4v16M3 8h4m10 0h4M3 12h18M3 16h4m10 0h4M4 20h16a1 1 0 001-1V5a1 1 0 00-1-1H4a1 1 0 00-1 1v14a1 1 0 001 1z" />
                  </svg>
                  <span class="text-xs">{{ movie.title }}</span>
                </div>
              </div>

              <!-- Trailer Trigger Button below poster if trailerUrl exists -->
              <div v-if="movie.trailerUrl" class="w-full max-w-[280px] md:max-w-none mt-3.5">
                <Button
                  variant="secondary"
                  size="md"
                  block
                  class="shadow-md"
                  @click="isTrailerOpen = true"
                >
                  <template #prefix>
                    <svg class="w-4 h-4 text-rose-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z" />
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                  </template>
                  {{ t('movieDetail.watchTrailer') }}
                </Button>
              </div>
            </div>

            <!-- Right: Movie Info Column (8 cols) -->
            <div class="md:col-span-8 lg:col-span-9 space-y-6">
              <div class="space-y-2">
                <div class="flex flex-wrap items-center gap-2">
                  <Badge
                    v-if="movie.ageRating"
                    :variant="getAgeRatingBadgeVariant(movie.ageRating)"
                    size="sm"
                    class="font-black"
                  >
                    {{ movie.ageRating }}
                  </Badge>

                  <Badge
                    :variant="movie.status === 'NOW_SHOWING' ? 'success' : 'primary'"
                    size="sm"
                  >
                    {{ movie.status === 'NOW_SHOWING' ? t('movies.nowShowing') : (movie.status === 'COMING_SOON' ? t('movies.comingSoon') : 'Ended') }}
                  </Badge>
                </div>

                <h1 class="text-2xl sm:text-3xl lg:text-4xl font-black text-white tracking-tight">
                  {{ movie.title }}
                </h1>

                <p
                  v-if="movie.originalTitle && movie.originalTitle !== movie.title"
                  class="text-sm sm:text-base text-slate-400 italic"
                >
                  {{ movie.originalTitle }}
                </p>
              </div>

              <!-- Metadata Grid -->
              <div class="grid grid-cols-2 sm:grid-cols-3 gap-y-4 gap-x-6 text-xs sm:text-sm border-y border-slate-750/80 py-4 text-slate-300">
                <div>
                  <span class="block text-[11px] uppercase font-semibold text-slate-400">{{ t('movieDetail.duration') }}</span>
                  <span class="font-bold text-white">{{ movie.durationMinutes ? formatDuration(movie.durationMinutes, locale) : t('movieDetail.unknownDuration') }}</span>
                </div>

                <div>
                  <span class="block text-[11px] uppercase font-semibold text-slate-400">{{ t('movieDetail.releaseDate') }}</span>
                  <span class="font-bold text-white">{{ formatDate(movie.releaseDate) }}</span>
                </div>

                <div>
                  <span class="block text-[11px] uppercase font-semibold text-slate-400">{{ t('movieDetail.genre') }}</span>
                  <span class="font-medium text-slate-200">
                    {{ movie.genres?.map(g => g.name).join(', ') || t('movieDetail.unclassified') }}
                  </span>
                </div>

                <div v-if="movie.director">
                  <span class="block text-[11px] uppercase font-semibold text-slate-400">{{ t('movieDetail.director') }}</span>
                  <span class="font-medium text-slate-200">{{ movie.director }}</span>
                </div>

                <div v-if="movie.country">
                  <span class="block text-[11px] uppercase font-semibold text-slate-400">{{ t('movieDetail.country') }}</span>
                  <span class="font-medium text-slate-200">{{ movie.country }}</span>
                </div>

                <div v-if="movie.language">
                  <span class="block text-[11px] uppercase font-semibold text-slate-400">{{ t('movieDetail.language') }}</span>
                  <span class="font-medium text-slate-200">{{ movie.language }}</span>
                </div>
              </div>

              <!-- Cast / Diễn viên -->
              <div v-if="movie.actors" class="space-y-1">
                <h4 class="text-xs font-semibold uppercase tracking-wider text-slate-400">{{ t('movieDetail.actors') }}</h4>
                <p class="text-sm text-slate-200">{{ movie.actors }}</p>
              </div>

              <!-- Overview / Nội dung phim -->
              <div class="space-y-1.5">
                <h4 class="text-xs font-semibold uppercase tracking-wider text-slate-400">{{ t('movieDetail.overview') }}</h4>
                <p class="text-sm text-slate-300 leading-relaxed">
                  {{ movie.overview || t('movieDetail.noOverview') }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Showtime Selection Section -->
      <section class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-4">
        <ShowtimeBrowser
          :movie-id="movie.id"
          :movie-title="movie.title"
        />
      </section>

      <!-- Trailer Modal Dialog -->
      <TrailerModal
        v-if="movie.trailerUrl"
        :show="isTrailerOpen"
        :trailer-url="movie.trailerUrl"
        :movie-title="movie.title"
        @close="isTrailerOpen = false"
      />
    </template>
  </div>
</template>