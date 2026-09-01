<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
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

const { t } = useI18n()
const toast = useToast()

const showtimes = ref<ShowtimeSummaryResponse[]>([])
const isLoading = ref(true)
const errorMessage = ref('')

// Dropdowns
const moviesList = ref<MovieSummaryResponse[]>([])
const cinemasList = ref<CinemaSummaryResponse[]>([])
const auditoriumsList = ref<AuditoriumResponse[]>([])

// Filters
const selectedCinemaId = ref('')
const selectedMovieId = ref('')
const selectedDate = ref('')
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const pageSize = ref(10)

// Create Showtime Modal
const isCreateModalOpen = ref(false)
const createForm = ref({
  movieId: '',
  auditoriumId: '',
  startTime: '',
  basePrice: 90000,
  format: 'TWO_D' as 'TWO_D' | 'THREE_D' | 'IMAX' | 'FOUR_DX',
  language: 'Tiếng Việt',
})
const isCreating = ref(false)

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
    const res = await showtimeService.getPublicShowtimes({
      cinemaId: selectedCinemaId.value || undefined,
      movieId: selectedMovieId.value || undefined,
      date: selectedDate.value || undefined,
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

async function handleCinemaChangeInCreate(cinemaId: string) {
  if (!cinemaId) {
    auditoriumsList.value = []
    createForm.value.auditoriumId = ''
    return
  }
  try {
    const auds = await cinemaService.getAuditoriumsByCinema(cinemaId)
    auditoriumsList.value = auds || []
    if (auds.length > 0) {
      createForm.value.auditoriumId = auds[0].id
    }
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  }
}

async function handleCreateShowtime() {
  if (!createForm.value.movieId || !createForm.value.auditoriumId || !createForm.value.startTime) {
    toast.error(t('common.errorTitle'))
    return
  }

  isCreating.value = true
  try {
    await showtimeService.createShowtime({
      movieId: createForm.value.movieId,
      auditoriumId: createForm.value.auditoriumId,
      startTime: createForm.value.startTime,
      basePrice: createForm.value.basePrice,
      format: createForm.value.format,
      language: createForm.value.language,
    })
    toast.success(t('common.successTitle'))
    isCreateModalOpen.value = false
    await fetchShowtimes()
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  } finally {
    isCreating.value = false
  }
}

function onPageChange(page: number) {
  currentPage.value = page
  fetchShowtimes()
}

watch([selectedCinemaId, selectedMovieId, selectedDate], () => {
  currentPage.value = 0
  fetchShowtimes()
})

onMounted(async () => {
  await fetchFilterOptions()
  await fetchShowtimes()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-white tracking-tight">{{ t('adminShowtimes.title') }}</h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ t('adminShowtimes.subtitle') }}
        </p>
      </div>

      <div class="flex items-center gap-2">
        <Button variant="primary" size="md" @click="isCreateModalOpen = true">
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

    <!-- Filters -->
    <Card padding="sm">
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div>
          <select
            v-model="selectedCinemaId"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="">{{ t('adminShowtimes.allCinemas') }}</option>
            <option v-for="c in cinemasList" :key="c.id" :value="c.id">
              {{ c.name }} ({{ c.city }})
            </option>
          </select>
        </div>

        <div>
          <select
            v-model="selectedMovieId"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="">{{ t('adminShowtimes.allMovies') }}</option>
            <option v-for="m in moviesList" :key="m.id" :value="m.id">
              {{ m.title }}
            </option>
          </select>
        </div>

        <div>
          <input
            v-model="selectedDate"
            type="date"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>
      </div>
    </Card>

    <!-- Showtimes Table -->
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
              </tr>
            </template>

            <!-- Empty State -->
            <tr v-else-if="showtimes.length === 0">
              <td colspan="5" class="px-4 py-16 text-center text-slate-400">
                <div class="max-w-sm mx-auto space-y-2">
                  <svg class="w-10 h-10 mx-auto text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
                  </svg>
                  <p class="text-sm font-medium text-slate-300">{{ t('adminShowtimes.emptyList') }}</p>
                  <p class="text-xs text-slate-500">{{ t('common.emptyDesc') }}</p>
                </div>
              </td>
            </tr>

            <!-- Rows -->
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
                    class="w-8 h-12 object-cover rounded bg-slate-800 flex-shrink-0"
                  />
                  <div>
                    <p class="font-bold text-slate-100">{{ s.movieTitle }}</p>
                    <p class="text-xs text-indigo-400 mt-0.5">{{ s.format }} • {{ s.language }}</p>
                  </div>
                </div>
              </td>
              <td class="px-4 py-3.5 text-xs text-slate-300">
                <p class="font-semibold text-slate-200">{{ s.cinemaName }}</p>
                <p class="text-slate-400 text-[11px] mt-0.5">{{ s.auditoriumName }}</p>
              </td>
              <td class="px-4 py-3.5 text-xs font-mono">
                <p class="font-bold text-slate-200">{{ formatDateTime(s.startTime) }}</p>
                <p class="text-[11px] text-slate-400 mt-0.5">{{ t('showtimes.toTime', { time: formatTime(s.endTime) }) }}</p>
              </td>
              <td class="px-4 py-3.5 font-mono font-bold text-emerald-400 text-xs">
                {{ formatCurrency(s.basePrice) }}
              </td>
              <td class="px-4 py-3.5">
                <Badge :variant="s.status === 'SCHEDULED' ? 'success' : 'neutral'">
                  {{ s.status === 'SCHEDULED' ? t('status.SCHEDULED') : s.status }}
                </Badge>
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

    <!-- Create Showtime Modal -->
    <Modal
      v-model="isCreateModalOpen"
      :title="t('adminShowtimes.createBtn')"
      @close="isCreateModalOpen = false"
    >
      <div class="space-y-4 text-xs text-slate-300">
        <div>
          <label class="block text-slate-300 font-medium mb-1">{{ t('adminShowtimes.colMovie') }}</label>
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

        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-slate-300 font-medium mb-1">{{ t('booking.cinemaInfo') }}</label>
            <select
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
              @change="(e: any) => handleCinemaChangeInCreate(e.target.value)"
            >
              <option value="" disabled selected>{{ t('adminShowtimes.allCinemas') }}</option>
              <option v-for="c in cinemasList" :key="c.id" :value="c.id">
                {{ c.name }}
              </option>
            </select>
          </div>

          <div>
            <label class="block text-slate-300 font-medium mb-1">{{ t('booking.roomInfo') }}</label>
            <select
              v-model="createForm.auditoriumId"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="" disabled>{{ t('booking.roomInfo') }}</option>
              <option v-for="a in auditoriumsList" :key="a.id" :value="a.id">
                {{ a.name }}
              </option>
            </select>
          </div>
        </div>

        <div>
          <label class="block text-slate-300 font-medium mb-1">{{ t('adminShowtimes.colStartTime') }}</label>
          <input
            v-model="createForm.startTime"
            type="datetime-local"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-slate-300 font-medium mb-1">{{ t('adminShowtimes.colBasePrice') }} (₫)</label>
            <Input v-model="createForm.basePrice" type="number" />
          </div>

          <div>
            <label class="block text-slate-300 font-medium mb-1">{{ t('showtimes.format') }}</label>
            <select
              v-model="createForm.format"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="TWO_D">2D</option>
              <option value="THREE_D">3D</option>
              <option value="IMAX">IMAX</option>
              <option value="FOUR_DX">4DX</option>
            </select>
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
  </div>
</template>
