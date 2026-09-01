<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import type { MovieSummaryResponse, MovieRecommendationResponse } from '@/types/movie.types'
import movieService from '@/services/movie.service'
import { formatDuration, formatDate } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import MovieCard from '@/components/movie/MovieCard.vue'
import Button from '@/components/common/Button.vue'
import Badge from '@/components/common/Badge.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const router = useRouter()
const { t, locale } = useI18n()

const nowShowingMovies = ref<MovieSummaryResponse[]>([])
const comingSoonMovies = ref<MovieSummaryResponse[]>([])
const featuredMovies = ref<MovieSummaryResponse[]>([])
const recommendedData = ref<MovieRecommendationResponse | null>(null)

const currentHeroIndex = ref<number>(0)
const heroAutoplayTimer = ref<any>(null)
const isHeroPaused = ref<boolean>(false)

// Carousel scroll refs & state for movie sections
const nowShowingContainer = ref<HTMLElement | null>(null)
const comingSoonContainer = ref<HTMLElement | null>(null)
const recommendationContainer = ref<HTMLElement | null>(null)

const isLoading = ref<boolean>(true)
const errorMessage = ref<string>('')
const heroImagesLoaded = ref<Record<string, boolean>>({})

// Reduced motion preference
const prefersReducedMotion = typeof window !== 'undefined'
  ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
  : false

const currentFeaturedMovie = computed<MovieSummaryResponse | null>(() => {
  if (featuredMovies.value.length === 0) return null
  return featuredMovies.value[currentHeroIndex.value] || featuredMovies.value[0]
})

async function fetchHomeData() {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const [nowShowingRes, comingSoonRes, recRes] = await Promise.all([
      movieService.getPublicMovies({ status: 'NOW_SHOWING', size: 12, sort: 'releaseDate,desc' }),
      movieService.getPublicMovies({ status: 'COMING_SOON', size: 8, sort: 'releaseDate,asc' }),
      movieService.getRecommendations(6).catch(() => null),
    ])

    nowShowingMovies.value = nowShowingRes.content || []
    comingSoonMovies.value = comingSoonRes.content || []
    recommendedData.value = recRes

    // Select 3-5 featured movies with valid backdropUrl
    const validBackdropMovies = nowShowingMovies.value.filter(
      (m) => !!m.backdropUrl && m.backdropUrl.trim() !== ''
    )

    if (validBackdropMovies.length >= 3) {
      featuredMovies.value = validBackdropMovies.slice(0, 5)
    } else if (nowShowingMovies.value.length > 0) {
      featuredMovies.value = nowShowingMovies.value.slice(0, Math.min(nowShowingMovies.value.length, 5))
    } else if (comingSoonMovies.value.length > 0) {
      featuredMovies.value = comingSoonMovies.value.slice(0, Math.min(comingSoonMovies.value.length, 5))
    }

    startHeroAutoplay()
  } catch (err: any) {
    errorMessage.value =
      err.response?.data?.message || 'Không thể tải danh sách phim từ máy chủ. Vui lòng thử lại.'
  } finally {
    isLoading.value = false
  }
}

// Hero Carousel Controls
function startHeroAutoplay() {
  stopHeroAutoplay()
  if (prefersReducedMotion || featuredMovies.value.length <= 1) return

  heroAutoplayTimer.value = setInterval(() => {
    if (!isHeroPaused.value && document.visibilityState === 'visible') {
      nextHeroSlide()
    }
  }, 6000)
}

function stopHeroAutoplay() {
  if (heroAutoplayTimer.value) {
    clearInterval(heroAutoplayTimer.value)
    heroAutoplayTimer.value = null
  }
}

function nextHeroSlide() {
  if (featuredMovies.value.length === 0) return
  currentHeroIndex.value = (currentHeroIndex.value + 1) % featuredMovies.value.length
}

function prevHeroSlide() {
  if (featuredMovies.value.length === 0) return
  currentHeroIndex.value =
    (currentHeroIndex.value - 1 + featuredMovies.value.length) % featuredMovies.value.length
}

function goToHeroSlide(index: number) {
  currentHeroIndex.value = index
}

// Horizontal Scroll Carousel Helpers
function scrollCarousel(container: HTMLElement | null, direction: 'left' | 'right') {
  if (!container) return
  const scrollAmount = container.clientWidth * 0.75
  container.scrollBy({
    left: direction === 'left' ? -scrollAmount : scrollAmount,
    behavior: 'smooth',
  })
}

function navigateToMovie(id: string) {
  router.push(`/movies/${id}`)
}

function onVisibilityChange() {
  if (document.visibilityState === 'hidden') {
    stopHeroAutoplay()
  } else {
    startHeroAutoplay()
  }
}

onMounted(() => {
  fetchHomeData()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  stopHeroAutoplay()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <div class="space-y-12 pb-16">
    <!-- Error Alert -->
    <div v-if="errorMessage" class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-6">
      <ErrorAlert :message="errorMessage" @retry="fetchHomeData" />
    </div>

    <!-- 1. Hero Section: Skeleton vs Dynamic Featured Carousel -->
    <template v-if="isLoading">
      <!-- Stable Hero Skeleton -->
      <section class="relative w-full min-h-[440px] sm:min-h-[520px] lg:min-h-[580px] bg-slate-950 flex items-end overflow-hidden border-b border-slate-800 animate-pulse">
        <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12 sm:py-16 w-full space-y-4">
          <div class="flex items-center gap-2">
            <div class="w-28 h-6 rounded bg-slate-800"></div>
            <div class="w-10 h-6 rounded bg-slate-800"></div>
          </div>
          <div class="w-3/4 max-w-xl h-10 sm:h-12 rounded-lg bg-slate-800"></div>
          <div class="flex items-center gap-4">
            <div class="w-24 h-4 rounded bg-slate-800"></div>
            <div class="w-32 h-4 rounded bg-slate-800"></div>
            <div class="w-20 h-4 rounded bg-slate-800"></div>
          </div>
          <div class="pt-3 flex items-center gap-3">
            <div class="w-48 h-12 rounded-xl bg-slate-800"></div>
            <div class="w-32 h-12 rounded-xl bg-slate-800"></div>
          </div>
        </div>
      </section>
    </template>

    <template v-else-if="currentFeaturedMovie">
      <!-- High-Definition Dynamic Hero Banner -->
      <section
        class="group/hero relative w-full min-h-[440px] sm:min-h-[520px] lg:min-h-[580px] bg-slate-950 flex items-end overflow-hidden border-b border-slate-800 select-none"
        @mouseenter="isHeroPaused = true"
        @mouseleave="isHeroPaused = false"
      >
        <!-- Backdrop Image Crossfade Layer -->
        <div class="absolute inset-0 z-0">
          <transition name="fade" mode="out-in">
            <img
              :key="currentFeaturedMovie.id"
              :src="currentFeaturedMovie.backdropUrl || currentFeaturedMovie.posterUrl"
              :alt="currentFeaturedMovie.title"
              :class="[
                'w-full h-full object-cover object-center transition-opacity duration-700',
                heroImagesLoaded[currentFeaturedMovie.id] ? 'opacity-90' : 'opacity-0'
              ]"
              @load="heroImagesLoaded[currentFeaturedMovie.id] = true"
            />
          </transition>

          <!-- Directional Left Gradient (for strong text readability without obscuring right image) -->
          <div class="absolute inset-y-0 left-0 w-full md:w-3/5 bg-gradient-to-r from-slate-950 via-slate-950/85 to-transparent"></div>

          <!-- Subtle Bottom Gradient -->
          <div class="absolute inset-x-0 bottom-0 h-40 bg-gradient-to-t from-slate-900 via-slate-900/60 to-transparent"></div>
        </div>

        <!-- Hero Content -->
        <div class="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12 sm:py-16 w-full">
          <div class="max-w-2xl space-y-4">
            <div class="flex items-center gap-2">
              <span class="px-2.5 py-1 rounded-md bg-indigo-600 text-white text-xs font-black uppercase tracking-wider shadow-md">
                {{ t('home.featuredBadge') }}
              </span>
              <Badge v-if="currentFeaturedMovie.ageRating" variant="warning" size="sm">
                {{ currentFeaturedMovie.ageRating }}
              </Badge>
            </div>

            <h1 class="text-3xl sm:text-4xl lg:text-5xl font-black text-white tracking-tight leading-tight drop-shadow-md">
              {{ currentFeaturedMovie.title }}
            </h1>

            <div class="flex flex-wrap items-center gap-4 text-xs sm:text-sm text-slate-300 font-medium">
              <span v-if="currentFeaturedMovie.durationMinutes" class="flex items-center gap-1">
                <svg class="w-4 h-4 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                {{ formatDuration(currentFeaturedMovie.durationMinutes, locale) }}
              </span>

              <span v-if="currentFeaturedMovie.releaseDate">
                {{ t('home.releaseDate') }}: {{ formatDate(currentFeaturedMovie.releaseDate) }}
              </span>

              <div v-if="currentFeaturedMovie.genres && currentFeaturedMovie.genres.length > 0" class="flex items-center gap-1.5">
                <span
                  v-for="g in currentFeaturedMovie.genres.slice(0, 3)"
                  :key="g.id"
                  class="px-2 py-0.5 rounded bg-slate-800/90 text-slate-300 text-xs border border-slate-700/60"
                >
                  {{ g.name }}
                </span>
              </div>
            </div>

            <div class="pt-2 flex flex-wrap items-center gap-3">
              <Button
                variant="primary"
                size="lg"
                class="shadow-xl shadow-indigo-600/30"
                @click="navigateToMovie(currentFeaturedMovie.id)"
              >
                <template #prefix>
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z" />
                  </svg>
                </template>
                {{ t('home.viewShowtimesAndBook') }}
              </Button>

              <Button
                variant="secondary"
                size="lg"
                @click="navigateToMovie(currentFeaturedMovie.id)"
              >
                {{ t('home.movieDetail') }}
              </Button>
            </div>
          </div>
        </div>

        <!-- Prev / Next Navigation Arrows on Hero -->
        <button
          v-if="featuredMovies.length > 1"
          type="button"
          class="absolute left-4 top-1/2 -translate-y-1/2 z-20 w-11 h-11 rounded-full bg-slate-900/70 hover:bg-slate-900 border border-slate-700 text-white flex items-center justify-center opacity-0 group-hover/hero:opacity-100 transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 shadow-xl"
          :aria-label="t('home.prevSlide')"
          @click.stop="prevHeroSlide"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M15 19l-7-7 7-7" />
          </svg>
        </button>

        <button
          v-if="featuredMovies.length > 1"
          type="button"
          class="absolute right-4 top-1/2 -translate-y-1/2 z-20 w-11 h-11 rounded-full bg-slate-900/70 hover:bg-slate-900 border border-slate-700 text-white flex items-center justify-center opacity-0 group-hover/hero:opacity-100 transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 shadow-xl"
          :aria-label="t('home.nextSlide')"
          @click.stop="nextHeroSlide"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M9 5l7 7-7 7" />
          </svg>
        </button>

        <!-- Slide Indicator Dots -->
        <div v-if="featuredMovies.length > 1" class="absolute bottom-4 right-6 z-20 flex items-center gap-1.5">
          <button
            v-for="(m, idx) in featuredMovies"
            :key="m.id"
            type="button"
            :class="[
              'h-2 rounded-full transition-all duration-300',
              currentHeroIndex === idx ? 'w-6 bg-indigo-500 shadow-sm' : 'w-2 bg-slate-600/80 hover:bg-slate-400'
            ]"
            :aria-label="`Slide ${idx + 1}`"
            @click="goToHeroSlide(idx)"
          ></button>
        </div>
      </section>
    </template>

    <!-- 2. Main Movie Sections: Carousel / Grid -->
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-12">
      <!-- Loading Skeleton Grids (Preserves Complete Page Layout Height) -->
      <template v-if="isLoading">
        <section class="space-y-6 animate-pulse">
          <div class="flex items-center justify-between border-b border-slate-800 pb-3">
            <div class="space-y-2">
              <div class="w-44 h-7 rounded bg-slate-800"></div>
              <div class="w-72 h-4 rounded bg-slate-800"></div>
            </div>
            <div class="w-20 h-4 rounded bg-slate-800"></div>
          </div>

          <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 sm:gap-6">
            <div
              v-for="n in 8"
              :key="'ns-skel-' + n"
              class="flex flex-col rounded-2xl bg-slate-800/60 border border-slate-700/60 overflow-hidden"
            >
              <div class="w-full aspect-[2/3] bg-slate-800"></div>
              <div class="p-4 space-y-2.5">
                <div class="w-3/4 h-4 rounded bg-slate-700"></div>
                <div class="w-1/2 h-3 rounded bg-slate-700"></div>
                <div class="pt-2 border-t border-slate-700/60 flex justify-between">
                  <div class="w-16 h-3 rounded bg-slate-700"></div>
                  <div class="w-16 h-3 rounded bg-slate-700"></div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </template>

      <!-- Loaded Content -->
      <template v-else>
        <!-- Section: Gợi Ý Dành Riêng Cho Bạn (Personalized Recommendations) -->
        <section v-if="recommendedData && recommendedData.movies.length > 0" class="space-y-6">
          <div class="flex flex-col sm:flex-row sm:items-center justify-between border-b border-slate-800 pb-3 gap-2">
            <div>
              <div class="flex items-center gap-2">
                <span class="p-1 rounded bg-amber-500/20 text-amber-400">
                  <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                    <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                  </svg>
                </span>
                <h2 class="text-xl sm:text-2xl font-bold text-white tracking-tight flex items-center gap-2">
                  Gợi Ý Dành Riêng Cho Bạn
                </h2>
                <span class="px-2 py-0.5 rounded text-[11px] font-semibold bg-indigo-600/30 text-indigo-300 border border-indigo-500/40">
                  AI Recommendation
                </span>
              </div>
              <p class="text-xs sm:text-sm text-slate-400 mt-1 flex items-center gap-1.5">
                <span>{{ recommendedData.explanation }}</span>
                <span v-if="recommendedData.favoriteGenres && recommendedData.favoriteGenres.length > 0" class="text-indigo-400 font-medium">
                  ({{ recommendedData.favoriteGenres.join(', ') }})
                </span>
              </p>
            </div>

            <div v-if="recommendedData.movies.length > 4" class="hidden sm:flex items-center gap-1.5">
              <button
                type="button"
                class="w-8 h-8 rounded-lg bg-slate-800 hover:bg-slate-750 border border-slate-700 text-slate-300 hover:text-white flex items-center justify-center transition-colors"
                aria-label="Scroll left"
                @click="scrollCarousel(recommendationContainer, 'left')"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
                </svg>
              </button>
              <button
                type="button"
                class="w-8 h-8 rounded-lg bg-slate-800 hover:bg-slate-750 border border-slate-700 text-slate-300 hover:text-white flex items-center justify-center transition-colors"
                aria-label="Scroll right"
                @click="scrollCarousel(recommendationContainer, 'right')"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                </svg>
              </button>
            </div>
          </div>

          <!-- Horizontal Carousel Container -->
          <div
            v-if="recommendedData.movies.length > 4"
            ref="recommendationContainer"
            class="flex items-stretch gap-4 sm:gap-6 overflow-x-auto pb-4 scrollbar-thin scrollbar-thumb-slate-700 scrollbar-track-transparent snap-x snap-mandatory"
          >
            <div
              v-for="movie in recommendedData.movies"
              :key="movie.id"
              class="w-44 sm:w-56 md:w-64 shrink-0 snap-start flex flex-col"
            >
              <MovieCard :movie="movie" class="h-full" />
            </div>
          </div>

          <!-- Standard Grid if <= 4 movies -->
          <div v-else class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 sm:gap-6">
            <MovieCard
              v-for="movie in recommendedData.movies"
              :key="movie.id"
              :movie="movie"
            />
          </div>
        </section>

        <!-- Section 1: Phim Đang Chiếu (Now Showing Carousel / Grid) -->
        <section class="space-y-6">
          <div class="flex items-center justify-between border-b border-slate-800 pb-3">
            <div>
              <h2 class="text-xl sm:text-2xl font-bold text-white tracking-tight flex items-center gap-2.5">
                <span class="w-2.5 h-6 rounded-full bg-indigo-600 inline-block"></span>
                {{ t('home.nowShowingTitle') }}
              </h2>
              <p class="text-xs sm:text-sm text-slate-400 mt-1">{{ t('home.nowShowingDesc') }}</p>
            </div>

            <div class="flex items-center gap-3">
              <!-- Carousel navigation buttons if more than 4 movies -->
              <div v-if="nowShowingMovies.length > 4" class="hidden sm:flex items-center gap-1.5">
                <button
                  type="button"
                  class="w-8 h-8 rounded-lg bg-slate-800 hover:bg-slate-750 border border-slate-700 text-slate-300 hover:text-white flex items-center justify-center transition-colors"
                  aria-label="Scroll left"
                  @click="scrollCarousel(nowShowingContainer, 'left')"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
                  </svg>
                </button>
                <button
                  type="button"
                  class="w-8 h-8 rounded-lg bg-slate-800 hover:bg-slate-750 border border-slate-700 text-slate-300 hover:text-white flex items-center justify-center transition-colors"
                  aria-label="Scroll right"
                  @click="scrollCarousel(nowShowingContainer, 'right')"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                  </svg>
                </button>
              </div>

              <router-link
                to="/movies?status=NOW_SHOWING"
                class="text-xs sm:text-sm font-semibold text-indigo-400 hover:text-indigo-300 transition-colors flex items-center gap-1"
              >
                {{ t('home.viewAll') }}
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                </svg>
              </router-link>
            </div>
          </div>

          <div
            v-if="nowShowingMovies.length === 0"
            class="p-12 text-center rounded-2xl bg-slate-850 border border-slate-800 text-slate-400 text-sm"
          >
            {{ t('home.emptyNowShowing') }}
          </div>

          <!-- Horizontal Carousel Container -->
          <div
            v-else-if="nowShowingMovies.length > 4"
            ref="nowShowingContainer"
            class="flex items-stretch gap-4 sm:gap-6 overflow-x-auto pb-4 scrollbar-thin scrollbar-thumb-slate-700 scrollbar-track-transparent snap-x snap-mandatory"
          >
            <div
              v-for="movie in nowShowingMovies"
              :key="movie.id"
              class="w-44 sm:w-56 md:w-64 shrink-0 snap-start flex flex-col"
            >
              <MovieCard :movie="movie" class="h-full" />
            </div>
          </div>

          <!-- Standard Grid if <= 4 movies -->
          <div v-else class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 sm:gap-6">
            <MovieCard
              v-for="movie in nowShowingMovies"
              :key="movie.id"
              :movie="movie"
            />
          </div>
        </section>

        <!-- Section 2: Phim Sắp Chiếu (Coming Soon Carousel / Grid) -->
        <section v-if="comingSoonMovies.length > 0" class="space-y-6">
          <div class="flex items-center justify-between border-b border-slate-800 pb-3">
            <div>
              <h2 class="text-xl sm:text-2xl font-bold text-white tracking-tight flex items-center gap-2.5">
                <span class="w-2.5 h-6 rounded-full bg-sky-500 inline-block"></span>
                {{ t('home.comingSoonTitle') }}
              </h2>
              <p class="text-xs sm:text-sm text-slate-400 mt-1">{{ t('home.comingSoonDesc') }}</p>
            </div>

            <div class="flex items-center gap-3">
              <!-- Carousel navigation buttons if more than 4 movies -->
              <div v-if="comingSoonMovies.length > 4" class="hidden sm:flex items-center gap-1.5">
                <button
                  type="button"
                  class="w-8 h-8 rounded-lg bg-slate-800 hover:bg-slate-750 border border-slate-700 text-slate-300 hover:text-white flex items-center justify-center transition-colors"
                  aria-label="Scroll left"
                  @click="scrollCarousel(comingSoonContainer, 'left')"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
                  </svg>
                </button>
                <button
                  type="button"
                  class="w-8 h-8 rounded-lg bg-slate-800 hover:bg-slate-750 border border-slate-700 text-slate-300 hover:text-white flex items-center justify-center transition-colors"
                  aria-label="Scroll right"
                  @click="scrollCarousel(comingSoonContainer, 'right')"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                  </svg>
                </button>
              </div>

              <router-link
                to="/movies?status=COMING_SOON"
                class="text-xs sm:text-sm font-semibold text-indigo-400 hover:text-indigo-300 transition-colors flex items-center gap-1"
              >
                {{ t('home.viewAll') }}
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                </svg>
              </router-link>
            </div>
          </div>

          <!-- Horizontal Carousel Container -->
          <div
            v-if="comingSoonMovies.length > 4"
            ref="comingSoonContainer"
            class="flex items-stretch gap-4 sm:gap-6 overflow-x-auto pb-4 scrollbar-thin scrollbar-thumb-slate-700 scrollbar-track-transparent snap-x snap-mandatory"
          >
            <div
              v-for="movie in comingSoonMovies"
              :key="movie.id"
              class="w-44 sm:w-56 md:w-64 shrink-0 snap-start flex flex-col"
            >
              <MovieCard :movie="movie" class="h-full" />
            </div>
          </div>

          <!-- Standard Grid if <= 4 movies -->
          <div v-else class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4 sm:gap-6">
            <MovieCard
              v-for="movie in comingSoonMovies"
              :key="movie.id"
              :movie="movie"
            />
          </div>
        </section>
      </template>
    </div>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.5s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
