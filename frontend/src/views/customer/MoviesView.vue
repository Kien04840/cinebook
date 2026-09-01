<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MovieSummaryResponse, MovieStatus } from '@/types/movie.types'
import type { GenreResponse } from '@/types/genre.types'
import type { PageResponse } from '@/types/api.types'
import movieService from '@/services/movie.service'
import genreService from '@/services/genre.service'
import { useI18n } from '@/composables/useI18n'
import MovieCard from '@/components/movie/MovieCard.vue'
import Pagination from '@/components/common/Pagination.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import Button from '@/components/common/Button.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

// Filter states
const searchQuery = ref<string>((route.query.q as string) || '')
const debouncedSearch = ref<string>((route.query.q as string) || '')
const selectedStatus = ref<MovieStatus | ''>((route.query.status as MovieStatus) || '')
const selectedGenre = ref<string>((route.query.genre as string) || '')
const currentPage = ref<number>(Number(route.query.page) || 0)
const pageSize = ref<number>(12)

const moviesData = ref<PageResponse<MovieSummaryResponse> | null>(null)
const genresList = ref<GenreResponse[]>([])

const isLoading = ref<boolean>(true)
const errorMessage = ref<string>('')

// Debounce timer for search
let debounceTimer: any = null
function handleSearchInput(e: Event) {
  const val = (e.target as HTMLInputElement).value
  searchQuery.value = val
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    debouncedSearch.value = val
    currentPage.value = 0
  }, 350)
}

function clearSearch() {
  searchQuery.value = ''
  debouncedSearch.value = ''
  currentPage.value = 0
}

function selectStatus(status: MovieStatus | '') {
  selectedStatus.value = status
  currentPage.value = 0
}

function resetAllFilters() {
  searchQuery.value = ''
  debouncedSearch.value = ''
  selectedStatus.value = ''
  selectedGenre.value = ''
  currentPage.value = 0
}

async function fetchGenres() {
  try {
    genresList.value = await genreService.getAllGenres()
  } catch (err) {
    console.error('Failed to load genres', err)
  }
}

async function fetchMovies() {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const data = await movieService.getPublicMovies({
      q: debouncedSearch.value || undefined,
      status: selectedStatus.value || undefined,
      genre: selectedGenre.value || undefined,
      page: currentPage.value,
      size: pageSize.value,
      sort: 'releaseDate,desc',
    })
    moviesData.value = data

    // Sync query params to URL cleanly
    const query: Record<string, string> = {}
    if (debouncedSearch.value) query.q = debouncedSearch.value
    if (selectedStatus.value) query.status = selectedStatus.value
    if (selectedGenre.value) query.genre = selectedGenre.value
    if (currentPage.value > 0) query.page = String(currentPage.value)
    router.replace({ query })
  } catch (err: any) {
    errorMessage.value =
      err.response?.data?.message || 'Không thể tải danh sách phim. Vui lòng kiểm tra lại kết nối.'
    moviesData.value = null
  } finally {
    isLoading.value = false
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const hasActiveFilters = computed(() => {
  return !!debouncedSearch.value || !!selectedStatus.value || !!selectedGenre.value
})

watch(
  () => [debouncedSearch.value, selectedStatus.value, selectedGenre.value, currentPage.value],
  () => {
    fetchMovies()
  }
)

onMounted(async () => {
  await fetchGenres()
  fetchMovies()
})
</script>

<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 sm:py-12 space-y-8">
    <!-- Header Title -->
    <div>
      <h1 class="text-2xl sm:text-3xl font-bold text-white tracking-tight">{{ t('movies.title') }}</h1>
      <p class="text-xs sm:text-sm text-slate-400 mt-1">{{ t('movies.subtitle') }}</p>
    </div>

    <!-- Search & Filter Controls Card -->
    <div class="p-4 sm:p-5 rounded-2xl bg-slate-800/90 border border-slate-700/80 shadow-md space-y-4">
      <div class="grid grid-cols-1 md:grid-cols-12 gap-4">
        <!-- Search Input with Clear Button (6 cols) -->
        <div class="md:col-span-6 relative">
          <div class="relative">
            <span class="absolute inset-y-0 left-0 flex items-center pl-3.5 pointer-events-none text-slate-400">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </span>

            <input
              type="text"
              :value="searchQuery"
              :placeholder="t('movies.searchPlaceholder')"
              class="w-full pl-10 pr-10 py-2.5 rounded-xl bg-slate-900 border border-slate-700 text-sm text-slate-100 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-colors"
              @input="handleSearchInput"
            />

            <button
              v-if="searchQuery"
              type="button"
              class="absolute inset-y-0 right-0 flex items-center pr-3 text-slate-400 hover:text-slate-200"
              :aria-label="t('common.clearSearch')"
              @click="clearSearch"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>

        <!-- Genre Filter Dropdown (3 cols) -->
        <div class="md:col-span-3">
          <select
            v-model="selectedGenre"
            class="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-slate-700 text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-colors"
          >
            <option value="">{{ t('movies.allGenres') }}</option>
            <option
              v-for="g in genresList"
              :key="g.id"
              :value="g.name"
            >
              {{ g.name }}
            </option>
          </select>
        </div>

        <!-- Reset Button (3 cols) -->
        <div class="md:col-span-3 flex items-center justify-end">
          <Button
            v-if="hasActiveFilters"
            variant="ghost"
            size="md"
            class="text-xs text-rose-400 hover:text-rose-300 w-full md:w-auto"
            @click="resetAllFilters"
          >
            <template #prefix>
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
            </template>
            {{ t('movies.clearFilters') }}
          </Button>
        </div>
      </div>

      <!-- Status Tabs Pill Selector -->
      <div class="flex items-center gap-2 pt-1 overflow-x-auto pb-1">
        <button
          type="button"
          :class="[
            'px-4 py-1.5 rounded-xl text-xs font-semibold transition-colors shrink-0',
            selectedStatus === ''
              ? 'bg-indigo-600 text-white shadow-sm'
              : 'bg-slate-900/90 text-slate-300 hover:bg-slate-750 hover:text-white border border-slate-700/80',
          ]"
          @click="selectStatus('')"
        >
          {{ t('movies.allStatuses') }}
        </button>

        <button
          type="button"
          :class="[
            'px-4 py-1.5 rounded-xl text-xs font-semibold transition-colors shrink-0 flex items-center gap-1.5',
            selectedStatus === 'NOW_SHOWING'
              ? 'bg-indigo-600 text-white shadow-sm'
              : 'bg-slate-900/90 text-slate-300 hover:bg-slate-750 hover:text-white border border-slate-700/80',
          ]"
          @click="selectStatus('NOW_SHOWING')"
        >
          <span class="w-2 h-2 rounded-full bg-emerald-400"></span>
          {{ t('movies.nowShowing') }}
        </button>

        <button
          type="button"
          :class="[
            'px-4 py-1.5 rounded-xl text-xs font-semibold transition-colors shrink-0 flex items-center gap-1.5',
            selectedStatus === 'COMING_SOON'
              ? 'bg-indigo-600 text-white shadow-sm'
              : 'bg-slate-900/90 text-slate-300 hover:bg-slate-750 hover:text-white border border-slate-700/80',
          ]"
          @click="selectStatus('COMING_SOON')"
        >
          <span class="w-2 h-2 rounded-full bg-sky-400"></span>
          {{ t('movies.comingSoon') }}
        </button>
      </div>
    </div>

    <!-- Error Alert -->
    <ErrorAlert
      v-if="errorMessage"
      :message="errorMessage"
      @retry="fetchMovies"
    />

    <!-- Layout-Stable Loading Skeleton Grid -->
    <div v-else-if="isLoading" class="space-y-6 animate-pulse">
      <div class="flex justify-between">
        <div class="w-36 h-4 rounded bg-slate-800"></div>
        <div class="w-24 h-4 rounded bg-slate-800"></div>
      </div>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-4 gap-4 sm:gap-6">
        <div
          v-for="n in 12"
          :key="'cat-skel-' + n"
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
    </div>

    <!-- Empty State -->
    <EmptyState
      v-else-if="!moviesData || moviesData.content.length === 0"
      :title="t('movies.emptyTitle')"
      :description="t('movies.emptyDesc')"
      :action-text="t('movies.clearAllFiltersBtn')"
      @action="resetAllFilters"
    />

    <!-- Movie Grid Results -->
    <template v-else>
      <div class="flex items-center justify-between text-xs text-slate-400">
        <span>{{ t('movies.foundCount', { count: moviesData.totalElements }) }}</span>
        <span>{{ t('movies.pageOf', { page: moviesData.page + 1, total: moviesData.totalPages || 1 }) }}</span>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-4 gap-4 sm:gap-6">
        <MovieCard
          v-for="movie in moviesData.content"
          :key="movie.id"
          :movie="movie"
        />
      </div>

      <!-- Pagination -->
      <div class="pt-6 flex justify-center">
        <Pagination
          :current-page="moviesData.page"
          :total-pages="moviesData.totalPages"
          @update:current-page="handlePageChange"
        />
      </div>
    </template>
  </div>
</template>
