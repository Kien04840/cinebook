<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import type { MovieSummaryResponse, MovieDetailResponse, MovieStatus } from '@/types/movie.types'
import type { GenreResponse } from '@/types/genre.types'
import movieService from '@/services/movie.service'
import genreService from '@/services/genre.service'
import tmdbService from '@/services/tmdb.service'
import { formatDate } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import { useToast } from '@/composables/useToast'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Input from '@/components/common/Input.vue'
import Badge from '@/components/common/Badge.vue'
import Modal from '@/components/common/Modal.vue'
import Pagination from '@/components/common/Pagination.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const { t } = useI18n()
const toast = useToast()

const movies = ref<MovieSummaryResponse[]>([])
const genres = ref<GenreResponse[]>([])
const isLoading = ref(true)
const errorMessage = ref('')

// Filters
const searchQuery = ref('')
const selectedGenre = ref('')
const selectedStatus = ref<string>('ALL')
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const pageSize = ref(10)

// Create/Edit modal
const isMovieModalOpen = ref(false)
const isEditing = ref(false)
const editingMovieId = ref('')
const isSaving = ref(false)

const movieForm = ref<{
  title: string
  originalTitle: string
  overview: string
  durationMinutes: number
  director: string
  actors: string
  country: string
  language: string
  releaseDate: string
  ageRating: string
  posterUrl: string
  backdropUrl: string
  trailerUrl: string
  status: MovieStatus
  genreIds: string[]
  tmdbId?: number
}>({
  title: '',
  originalTitle: '',
  overview: '',
  durationMinutes: 120,
  director: '',
  actors: '',
  country: 'Việt Nam',
  language: 'Tiếng Việt',
  releaseDate: new Date().toISOString().split('T')[0],
  ageRating: 'P',
  posterUrl: '',
  backdropUrl: '',
  trailerUrl: '',
  status: 'NOW_SHOWING',
  genreIds: [],
})

// TMDB Sync & Import Modals
const isSyncingGenres = ref(false)
const isTmdbImportModalOpen = ref(false)
const tmdbImportId = ref<number | null>(null)
const isImportingTmdb = ref(false)

// Delete modal
const isDeleteModalOpen = ref(false)
const deletingMovieId = ref('')
const isDeleting = ref(false)

const ageRatings = ['P', 'K', 'T13', 'T16', 'T18', 'C']

function getStatusBadgeVariant(status: MovieStatus) {
  switch (status) {
    case 'NOW_SHOWING':
      return 'success'
    case 'COMING_SOON':
      return 'warning'
    case 'ENDED':
      return 'neutral'
    case 'HIDDEN':
      return 'danger'
    default:
      return 'neutral'
  }
}

function getStatusLabel(status: MovieStatus) {
  switch (status) {
    case 'NOW_SHOWING':
      return 'Đang chiếu'
    case 'COMING_SOON':
      return 'Sắp chiếu'
    case 'ENDED':
      return 'Đã kết thúc'
    case 'HIDDEN':
      return 'Đã ẩn'
    default:
      return status
  }
}

async function loadGenres() {
  try {
    const res = await genreService.getAllGenres()
    genres.value = res || []
  } catch (err) {
    console.error('Failed to load genres', err)
  }
}

async function fetchMovies() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const res = await movieService.getAdminMovies({
      q: searchQuery.value.trim() || undefined,
      genre: selectedGenre.value || undefined,
      status: selectedStatus.value !== 'ALL' ? selectedStatus.value : undefined,
      page: currentPage.value,
      size: pageSize.value,
    })

    movies.value = res.content || []
    totalPages.value = res.totalPages || 0
    totalElements.value = res.totalElements || 0
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || 'Không thể tải danh sách phim.'
  } finally {
    isLoading.value = false
  }
}

function openCreateModal() {
  isEditing.value = false
  editingMovieId.value = ''
  movieForm.value = {
    title: '',
    originalTitle: '',
    overview: '',
    durationMinutes: 120,
    director: '',
    actors: '',
    country: 'Việt Nam',
    language: 'Tiếng Việt',
    releaseDate: new Date().toISOString().split('T')[0],
    ageRating: 'P',
    posterUrl: '',
    backdropUrl: '',
    trailerUrl: '',
    status: 'NOW_SHOWING',
    genreIds: [],
  }
  isMovieModalOpen.value = true
}

async function openEditModal(movie: MovieSummaryResponse) {
  isEditing.value = true
  editingMovieId.value = movie.id

  try {
    const detail: MovieDetailResponse = await movieService.getMovieDetail(movie.id)
    movieForm.value = {
      title: detail.title,
      originalTitle: detail.originalTitle || '',
      overview: detail.overview || '',
      durationMinutes: detail.durationMinutes ?? 120,
      director: detail.director || '',
      actors: detail.actors || '',
      country: detail.country || 'Việt Nam',
      language: detail.language || 'Tiếng Việt',
      releaseDate: detail.releaseDate ?? new Date().toISOString().split('T')[0],
      ageRating: detail.ageRating ?? 'P',
      posterUrl: detail.posterUrl || '',
      backdropUrl: detail.backdropUrl || '',
      trailerUrl: detail.trailerUrl || '',
      status: detail.status,
      genreIds: detail.genres ? detail.genres.map(g => g.id) : [],
      tmdbId: detail.tmdbId,
    }
  } catch (err) {
    movieForm.value = {
      title: movie.title,
      originalTitle: '',
      overview: '',
      durationMinutes: movie.durationMinutes ?? 120,
      director: '',
      actors: '',
      country: 'Việt Nam',
      language: 'Tiếng Việt',
      releaseDate: movie.releaseDate ?? new Date().toISOString().split('T')[0],
      ageRating: movie.ageRating ?? 'P',
      posterUrl: movie.posterUrl || '',
      backdropUrl: '',
      trailerUrl: '',
      status: movie.status,
      genreIds: [],
      tmdbId: movie.tmdbId,
    }
  }
  isMovieModalOpen.value = true
}

async function handleSaveMovie() {
  if (!movieForm.value.title.trim()) {
    toast.error('Vui lòng nhập tên phim.')
    return
  }
  if (movieForm.value.durationMinutes <= 0) {
    toast.error('Thời lượng phim phải lớn hơn 0.')
    return
  }

  isSaving.value = true
  try {
    if (isEditing.value) {
      await movieService.updateMovie(editingMovieId.value, movieForm.value)
      toast.success('Cập nhật thông tin phim thành công!')
    } else {
      await movieService.createMovie(movieForm.value)
      toast.success('Tạo phim mới thành công!')
    }
    isMovieModalOpen.value = false
    await fetchMovies()
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Lưu phim thất bại.')
  } finally {
    isSaving.value = false
  }
}

async function handleSyncGenres() {
  isSyncingGenres.value = true
  try {
    const res = await tmdbService.syncGenres()
    toast.success(`Đồng bộ TMDB thành công! Tổng cộng: ${res.total} thể loại (Mới: ${res.created}, Cập nhật: ${res.updated})`)
    await loadGenres()
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Đồng bộ thể loại TMDB thất bại. Vui lòng kiểm tra TMDB API Key.')
  } finally {
    isSyncingGenres.value = false
  }
}

async function handleImportTmdbMovie() {
  if (!tmdbImportId.value || tmdbImportId.value <= 0) {
    toast.error('Vui lòng nhập TMDB ID hợp lệ (số nguyên dương).')
    return
  }

  isImportingTmdb.value = true
  try {
    const res = await tmdbService.importMovie(tmdbImportId.value)
    toast.success(`Nhập phim "${res.title}" từ TMDB thành công!`)
    isTmdbImportModalOpen.value = false
    tmdbImportId.value = null
    await fetchMovies()
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Nhập phim từ TMDB thất bại. Vui lòng kiểm tra TMDB ID và API Key.')
  } finally {
    isImportingTmdb.value = false
  }
}

async function handleReimportTmdb(tmdbId?: number) {
  if (!tmdbId) return
  if (!confirm(`Bạn có chắc muốn cập nhật lại thông tin phim từ TMDB (ID: ${tmdbId})? Dữ liệu TMDB sẽ được làm mới trong khi trạng thái và lịch chiếu giữ nguyên.`)) {
    return
  }

  try {
    const res = await tmdbService.importMovie(tmdbId)
    toast.success(`Cập nhật lại phim "${res.title}" từ TMDB thành công!`)
    if (isMovieModalOpen.value) {
      isMovieModalOpen.value = false
    }
    await fetchMovies()
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Cập nhật lại từ TMDB thất bại.')
  }
}

function openDeleteModal(movieId: string) {
  deletingMovieId.value = movieId
  isDeleteModalOpen.value = true
}

async function handleDeleteMovie() {
  if (!deletingMovieId.value) return

  isDeleting.value = true
  try {
    await movieService.deleteMovie(deletingMovieId.value)
    toast.success('Ẩn / Xóa phim thành công!')
    isDeleteModalOpen.value = false
    await fetchMovies()
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Xóa phim thất bại.')
  } finally {
    isDeleting.value = false
  }
}

let searchDebounce: any = null
watch(searchQuery, () => {
  clearTimeout(searchDebounce)
  searchDebounce = setTimeout(() => {
    currentPage.value = 0
    fetchMovies()
  }, 400)
})

watch([selectedGenre, selectedStatus], () => {
  currentPage.value = 0
  fetchMovies()
})

watch(currentPage, () => {
  fetchMovies()
})

onMounted(() => {
  loadGenres()
  fetchMovies()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-white tracking-tight flex items-center gap-2">
          🎬 {{ t('adminMovies.title') }}
        </h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ t('adminMovies.subtitle') }}
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-2.5">
        <Button variant="secondary" size="md" :loading="isSyncingGenres" @click="handleSyncGenres">
          <template #prefix>
            <span class="text-amber-400">🔄</span>
          </template>
          Đồng bộ Thể Loại TMDB
        </Button>

        <Button variant="secondary" size="md" @click="isTmdbImportModalOpen = true">
          <template #prefix>
            <span class="text-sky-400">🌐</span>
          </template>
          Nhập phim từ TMDB
        </Button>

        <Button variant="primary" size="md" @click="openCreateModal">
          <template #prefix>
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
          </template>
          Thêm Phim Mới
        </Button>
      </div>
    </div>

    <ErrorAlert v-if="errorMessage" :message="errorMessage" @retry="fetchMovies" />

    <Card padding="sm">
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div>
          <Input
            v-model="searchQuery"
            placeholder="Tìm theo tên phim, đạo diễn..."
            clearable
          >
            <template #prefix>
              <svg class="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </template>
          </Input>
        </div>

        <div>
          <select
            v-model="selectedGenre"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="">Tất cả thể loại</option>
            <option v-for="g in genres" :key="g.id" :value="g.id">{{ g.name }}</option>
          </select>
        </div>

        <div>
          <select
            v-model="selectedStatus"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="ALL">Tất cả trạng thái</option>
            <option value="NOW_SHOWING">Đang chiếu (NOW_SHOWING)</option>
            <option value="COMING_SOON">Sắp chiếu (COMING_SOON)</option>
            <option value="ENDED">Đã kết thúc (ENDED)</option>
            <option value="HIDDEN">Đã ẩn (HIDDEN)</option>
          </select>
        </div>
      </div>
    </Card>

    <Card padding="none">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-sm">
          <thead>
            <tr class="bg-slate-850 border-b border-slate-700/80 text-xs font-semibold uppercase text-slate-400 tracking-wider">
              <th class="px-4 py-3.5">Phim</th>
              <th class="px-4 py-3.5">Thời lượng</th>
              <th class="px-4 py-3.5">Độ tuổi</th>
              <th class="px-4 py-3.5">Khởi chiếu</th>
              <th class="px-4 py-3.5">TMDB ID</th>
              <th class="px-4 py-3.5">Trạng thái</th>
              <th class="px-4 py-3.5 text-right">Thao tác</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-700/60">
            <template v-if="isLoading">
              <tr v-for="i in 6" :key="'skel-mv-' + i" class="animate-pulse">
                <td class="px-4 py-4 flex items-center gap-3">
                  <div class="w-14 h-20 bg-slate-800 rounded-lg flex-shrink-0"></div>
                  <div class="space-y-2">
                    <div class="h-4 w-40 bg-slate-800 rounded"></div>
                    <div class="h-3 w-24 bg-slate-800 rounded"></div>
                  </div>
                </td>
                <td class="px-4 py-4"><div class="h-4 w-16 bg-slate-800 rounded"></div></td>
                <td class="px-4 py-4"><div class="h-5 w-10 bg-slate-800 rounded"></div></td>
                <td class="px-4 py-4"><div class="h-4 w-24 bg-slate-800 rounded"></div></td>
                <td class="px-4 py-4"><div class="h-4 w-16 bg-slate-800 rounded"></div></td>
                <td class="px-4 py-4"><div class="h-6 w-20 bg-slate-800 rounded-full"></div></td>
                <td class="px-4 py-4 text-right"><div class="h-8 w-24 bg-slate-800 rounded ml-auto"></div></td>
              </tr>
            </template>

            <tr v-else-if="movies.length === 0">
              <td colspan="7" class="px-4 py-16 text-center text-slate-400">
                <div class="max-w-sm mx-auto space-y-2">
                  <svg class="w-10 h-10 mx-auto text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M7 4v16M17 4v16M3 8h4m10 0h4M3 12h18M3 16h4m10 0h4M4 20h16a1 1 0 001-1V5a1 1 0 00-1-1H4a1 1 0 00-1 1v14a1 1 0 001 1z" />
                  </svg>
                  <p class="text-sm font-medium text-slate-300">Không tìm thấy phim nào</p>
                  <p class="text-xs text-slate-500">Thử thay đổi từ khóa tìm kiếm hoặc lọc theo trạng thái khác.</p>
                </div>
              </td>
            </tr>

            <tr
              v-for="m in movies"
              :key="m.id"
              class="hover:bg-slate-750/70 transition-colors"
            >
              <td class="px-4 py-3.5">
                <div class="flex items-center gap-3">
                  <img
                    v-if="m.posterUrl"
                    :src="m.posterUrl"
                    :alt="m.title"
                    class="w-14 h-20 rounded-lg object-cover shadow-md flex-shrink-0 bg-slate-800"
                    loading="lazy"
                  />
                  <div v-else class="w-14 h-20 rounded-lg bg-slate-800 flex items-center justify-center text-slate-600 flex-shrink-0">
                    🎬
                  </div>

                  <div class="space-y-1">
                    <p class="font-bold text-white text-sm line-clamp-1">{{ m.title }}</p>
                    <p v-if="m.genres && m.genres.length > 0" class="text-xs text-slate-400 line-clamp-1">
                      {{ m.genres.join(', ') }}
                    </p>
                  </div>
                </div>
              </td>

              <td class="px-4 py-3.5 text-slate-300 text-xs font-mono font-medium">
                {{ m.durationMinutes }} phút
              </td>

              <td class="px-4 py-3.5">
                <span class="inline-flex items-center justify-center px-2 py-0.5 rounded text-[11px] font-bold bg-amber-500/15 text-amber-400 border border-amber-500/30">
                  {{ m.ageRating }}
                </span>
              </td>

              <td class="px-4 py-3.5 text-slate-300 text-xs">
                {{ formatDate(m.releaseDate) }}
              </td>

              <td class="px-4 py-3.5">
                <span v-if="m.tmdbId" class="inline-flex items-center gap-1 font-mono text-xs text-sky-400">
                  <span>#</span>{{ m.tmdbId }}
                </span>
                <span v-else class="text-slate-600 text-xs">—</span>
              </td>

              <td class="px-4 py-3.5">
                <Badge :variant="getStatusBadgeVariant(m.status)" size="sm">
                  {{ getStatusLabel(m.status) }}
                </Badge>
              </td>

              <td class="px-4 py-3.5 text-right space-x-1.5 whitespace-nowrap">
                <Button variant="ghost" size="sm" @click="openEditModal(m)">
                  Sửa
                </Button>
                <Button
                  v-if="m.tmdbId"
                  variant="ghost"
                  size="sm"
                  class="text-sky-400 hover:text-sky-300"
                  title="Cập nhật lại từ TMDB"
                  @click="handleReimportTmdb(m.tmdbId)"
                >
                  Sync
                </Button>
                <Button
                  v-if="m.status !== 'HIDDEN'"
                  variant="ghost"
                  size="sm"
                  class="text-rose-400 hover:text-rose-300"
                  @click="openDeleteModal(m.id)"
                >
                  Ẩn
                </Button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <Pagination
        v-if="totalPages > 1"
        v-model:currentPage="currentPage"
        :totalPages="totalPages"
        :totalElements="totalElements"
        :pageSize="pageSize"
      />
    </Card>

    <Modal
      v-model="isMovieModalOpen"
      :title="isEditing ? 'Cập Nhật Thông Tin Phim' : 'Thêm Phim Mới'"
      size="xl"
    >
      <form class="space-y-4" @submit.prevent="handleSaveMovie">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Tên phim (Tiếng Việt) *</label>
            <Input v-model="movieForm.title" placeholder="Đào, Phở và Piano" required />
          </div>
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Tên gốc (Original Title)</label>
            <Input v-model="movieForm.originalTitle" placeholder="Original Title" />
          </div>
        </div>

        <div>
          <label class="text-xs text-slate-400 font-medium block mb-1">Tóm tắt nội dung (Overview)</label>
          <textarea
            v-model="movieForm.overview"
            rows="3"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
            placeholder="Mô tả nội dung tóm tắt của bộ phim..."
          ></textarea>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Thời lượng (phút) *</label>
            <Input v-model="movieForm.durationMinutes" type="number" min="1" required />
          </div>
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Độ tuổi phân loại (Age Rating) *</label>
            <select
              v-model="movieForm.ageRating"
              required
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option v-for="r in ageRatings" :key="r" :value="r">{{ r }}</option>
            </select>
          </div>
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Trạng thái phát hành *</label>
            <select
              v-model="movieForm.status"
              required
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="NOW_SHOWING">NOW_SHOWING (Đang chiếu)</option>
              <option value="COMING_SOON">COMING_SOON (Sắp chiếu)</option>
              <option value="ENDED">ENDED (Đã kết thúc)</option>
              <option value="HIDDEN">HIDDEN (Đã ẩn)</option>
            </select>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Ngày khởi chiếu *</label>
            <Input v-model="movieForm.releaseDate" type="date" required />
          </div>
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Quốc gia</label>
            <Input v-model="movieForm.country" placeholder="Việt Nam" />
          </div>
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Ngôn ngữ</label>
            <Input v-model="movieForm.language" placeholder="Tiếng Việt" />
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Đạo diễn</label>
            <Input v-model="movieForm.director" placeholder="Phi Tiến Sơn" />
          </div>
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Diễn viên chính</label>
            <Input v-model="movieForm.actors" placeholder="Doãn Quốc Đam, Cao Thùy Linh..." />
          </div>
        </div>

        <div>
          <label class="text-xs text-slate-400 font-medium block mb-1">URL Poster (Ảnh bìa dọc)</label>
          <Input v-model="movieForm.posterUrl" placeholder="https://image.tmdb.org/t/p/w500/..." />
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">URL Backdrop (Ảnh bìa ngang)</label>
            <Input v-model="movieForm.backdropUrl" placeholder="https://image.tmdb.org/t/p/original/..." />
          </div>
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">URL Trailer (YouTube Embed / Link)</label>
            <Input v-model="movieForm.trailerUrl" placeholder="https://youtube.com/watch?v=..." />
          </div>
        </div>

        <div>
          <label class="text-xs text-slate-400 font-medium block mb-2">Thể loại phim</label>
          <div class="flex flex-wrap gap-2 max-h-36 overflow-y-auto p-3 bg-slate-900 rounded-lg border border-slate-700">
            <label
              v-for="g in genres"
              :key="g.id"
              class="flex items-center gap-1.5 text-xs text-slate-300 cursor-pointer bg-slate-800 px-2.5 py-1.5 rounded-md hover:bg-slate-700"
            >
              <input
                v-model="movieForm.genreIds"
                type="checkbox"
                :value="g.id"
                class="rounded border-slate-700 text-indigo-600 focus:ring-indigo-500 bg-slate-900"
              />
              <span>{{ g.name }}</span>
            </label>
          </div>
        </div>
      </form>

      <template #footer>
        <div class="flex items-center justify-between w-full">
          <div>
            <Button
              v-if="isEditing && movieForm.tmdbId"
              variant="secondary"
              size="md"
              type="button"
              @click="handleReimportTmdb(movieForm.tmdbId)"
            >
              🔄 Làm mới từ TMDB (#{{ movieForm.tmdbId }})
            </Button>
          </div>
          <div class="space-x-3">
            <Button variant="secondary" size="md" :disabled="isSaving" @click="isMovieModalOpen = false">
              Hủy bỏ
            </Button>
            <Button variant="primary" size="md" :loading="isSaving" @click="handleSaveMovie">
              {{ isEditing ? 'Cập nhật' : 'Tạo mới' }}
            </Button>
          </div>
        </div>
      </template>
    </Modal>

    <Modal
      v-model="isTmdbImportModalOpen"
      title="Nhập Phim Trực Tiếp Từ TMDB"
      size="sm"
    >
      <div class="space-y-4">
        <p class="text-sm text-slate-200 leading-relaxed">
          Nhập <strong>TMDB ID</strong> của bộ phim (tìm trên themoviedb.org). Hệ thống sẽ tự động tải poster, backdrop, thời lượng, diễn viên, đạo diễn và phân loại độ tuổi.
        </p>

        <div>
          <label class="text-xs text-slate-400 font-medium block mb-1">TMDB Movie ID *</label>
          <Input
            v-model="tmdbImportId"
            type="number"
            placeholder="Ví dụ: 550 (Fight Club), 693134 (Dune 2)..."
            required
          />
        </div>
      </div>

      <template #footer>
        <Button variant="secondary" size="md" :disabled="isImportingTmdb" @click="isTmdbImportModalOpen = false">
          Hủy bỏ
        </Button>
        <Button variant="primary" size="md" :loading="isImportingTmdb" @click="handleImportTmdbMovie">
          Tải & Nhập Phim
        </Button>
      </template>
    </Modal>

    <Modal
      v-model="isDeleteModalOpen"
      title="Xác Nhận Ẩn / Xóa Phim"
      size="sm"
    >
      <p class="text-sm text-slate-200 leading-relaxed">
        Bạn có chắc chắn muốn ẩn phim này? Phim sẽ không hiển thị trên giao diện đặt vé công khai của khách hàng.
      </p>

      <template #footer>
        <Button variant="secondary" size="md" :disabled="isDeleting" @click="isDeleteModalOpen = false">
          Hủy bỏ
        </Button>
        <Button variant="danger" size="md" :loading="isDeleting" @click="handleDeleteMovie">
          Xác nhận ẩn
        </Button>
      </template>
    </Modal>
  </div>
</template>
