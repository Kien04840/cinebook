<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import type { MovieSummaryResponse } from '@/types/movie.types'
import { formatDuration, formatDate } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Badge from '@/components/common/Badge.vue'

interface Props {
  movie: MovieSummaryResponse
}

const props = defineProps<Props>()
const router = useRouter()
const { t, locale } = useI18n()

const imageError = ref(false)
const imageLoaded = ref(false)

function handleImageError() {
  imageError.value = true
}

function handleImageLoad() {
  imageLoaded.value = true
}

function navigateToDetail() {
  router.push(`/movies/${props.movie.id}`)
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

function getStatusBadge(): { label: string; variant: 'success' | 'primary' | 'neutral' } {
  if (props.movie.status === 'NOW_SHOWING') {
    return { label: t('movies.nowShowing'), variant: 'success' }
  }
  if (props.movie.status === 'COMING_SOON') {
    return { label: t('movies.comingSoon'), variant: 'primary' }
  }
  return { label: 'Ended', variant: 'neutral' }
}
</script>

<template>
  <div
    class="group relative flex flex-col rounded-2xl bg-slate-800/90 border border-slate-700/80 overflow-hidden shadow-lg hover:shadow-indigo-500/10 hover:border-slate-600 transition-all duration-300 cursor-pointer"
    @click="navigateToDetail"
  >
    <!-- Poster Aspect Ratio 2:3 with smooth load transition -->
    <div class="relative w-full aspect-[2/3] bg-slate-900 overflow-hidden">
      <!-- Loading Skeleton behind image -->
      <div
        v-if="!imageLoaded && !imageError"
        class="absolute inset-0 bg-slate-800 animate-pulse flex items-center justify-center text-slate-600"
      >
        <svg class="w-8 h-8 opacity-40 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
      </div>

      <img
        v-if="movie.posterUrl && !imageError"
        :src="movie.posterUrl"
        :alt="movie.title"
        :class="[
          'w-full h-full object-cover group-hover:scale-105 transition-all duration-500',
          imageLoaded ? 'opacity-100' : 'opacity-0'
        ]"
        loading="lazy"
        @load="handleImageLoad"
        @error="handleImageError"
      />
      <div
        v-else
        class="w-full h-full flex flex-col items-center justify-center p-4 text-center bg-slate-850 text-slate-500"
      >
        <svg class="w-12 h-12 mb-2 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M7 4v16M17 4v16M3 8h4m10 0h4M3 12h18M3 16h4m10 0h4M4 20h16a1 1 0 001-1V5a1 1 0 00-1-1H4a1 1 0 00-1 1v14a1 1 0 001 1z" />
        </svg>
        <span class="text-xs font-medium line-clamp-2 text-slate-400">{{ movie.title }}</span>
      </div>

      <!-- Top Badges Overlay -->
      <div class="absolute top-2.5 left-2.5 right-2.5 flex items-center justify-between pointer-events-none">
        <Badge
          v-if="movie.ageRating"
          :variant="getAgeRatingBadgeVariant(movie.ageRating)"
          size="sm"
          class="font-black shadow-md backdrop-blur-md"
        >
          {{ movie.ageRating }}
        </Badge>
        <div v-else></div>

        <Badge
          :variant="getStatusBadge().variant"
          size="sm"
          class="shadow-md backdrop-blur-md font-semibold text-[11px]"
        >
          {{ getStatusBadge().label }}
        </Badge>
      </div>

      <!-- Hover Overlay Quick Action -->
      <div class="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/40 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex items-end p-4">
        <button
          type="button"
          class="w-full py-2.5 px-4 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs sm:text-sm font-semibold shadow-lg shadow-indigo-600/30 transition-colors flex items-center justify-center gap-1.5"
          @click.stop="navigateToDetail"
        >
          <svg v-if="movie.status === 'NOW_SHOWING'" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z" />
          </svg>
          <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          {{ movie.status === 'NOW_SHOWING' ? t('home.viewShowtimesAndBook') : t('home.movieDetail') }}
        </button>
      </div>
    </div>

    <!-- Metadata Section -->
    <div class="p-3.5 sm:p-4 flex flex-col flex-1 justify-between gap-2.5">
      <div>
        <h3
          class="text-sm sm:text-base font-bold text-white group-hover:text-indigo-400 transition-colors line-clamp-1"
          :title="movie.title"
        >
          {{ movie.title }}
        </h3>
        <p
          v-if="movie.originalTitle && movie.originalTitle !== movie.title"
          class="text-xs text-slate-400 line-clamp-1 italic mt-0.5"
          :title="movie.originalTitle"
        >
          {{ movie.originalTitle }}
        </p>

        <!-- Genres -->
        <div v-if="movie.genres && movie.genres.length > 0" class="flex flex-wrap gap-1.5 mt-2">
          <span
            v-for="g in movie.genres.slice(0, 2)"
            :key="g.id"
            class="text-[11px] px-2 py-0.5 rounded-md bg-slate-700/60 text-slate-300 font-medium"
          >
            {{ g.name }}
          </span>
          <span
            v-if="movie.genres.length > 2"
            class="text-[10px] px-1.5 py-0.5 rounded-md bg-slate-700/40 text-slate-400 font-medium"
          >
            +{{ movie.genres.length - 2 }}
          </span>
        </div>
      </div>

      <!-- Footer Info -->
      <div class="flex items-center justify-between text-xs text-slate-400 pt-2 border-t border-slate-700/60">
        <span v-if="movie.durationMinutes" class="flex items-center gap-1 font-medium text-slate-300">
          <svg class="w-3.5 h-3.5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          {{ formatDuration(movie.durationMinutes, locale) }}
        </span>
        <span v-else class="text-slate-500">{{ t('movieDetail.unknownDuration') }}</span>

        <span v-if="movie.releaseDate" class="text-[11px] text-slate-400">
          {{ formatDate(movie.releaseDate) }}
        </span>
      </div>
    </div>
  </div>
</template>
