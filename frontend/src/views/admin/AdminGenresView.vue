<script setup lang="ts">
/**
 * AdminGenresView.vue — Quản lý thể loại phim cho Quản trị viên (CineBook Admin)
 * 
 * Chức năng:
 * - Xem danh sách toàn bộ thể loại phim trong hệ thống
 * - Tìm kiếm thể loại theo tên phía client
 * - Thêm mới thể loại (Validate tên tối đa 100 ký tự, mô tả 255 ký tự, chống trùng lặp HTTP 409)
 * - Chỉnh sửa thể loại
 * - Xóa thể loại (Kèm modal xác nhận)
 * - Đồng bộ thể loại trực tiếp từ TMDB API
 */
import { ref, computed, onMounted } from 'vue'
import type { GenreResponse } from '@/types/genre.types'
import genreService from '@/services/genre.service'
import { useI18n } from '@/composables/useI18n'
import { useToast } from '@/composables/useToast'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Input from '@/components/common/Input.vue'
import Modal from '@/components/common/Modal.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const { t } = useI18n()
const toast = useToast()

// Dữ liệu thể loại
const genres = ref<GenreResponse[]>([])
const isLoading = ref(true)
const errorMessage = ref('')
const searchQuery = ref('')

// Modal Thêm/Sửa
const isModalOpen = ref(false)
const isEditing = ref(false)
const editingGenreId = ref('')
const isSaving = ref(false)
const formError = ref('')

const formData = ref<{
  name: string
  description: string
}>({
  name: '',
  description: '',
})

// Modal Xác nhận xóa
const isDeleteModalOpen = ref(false)
const deletingGenre = ref<GenreResponse | null>(null)
const isDeleting = ref(false)

// Trạng thái đồng bộ TMDB
const isSyncing = ref(false)

/**
 * Danh sách thể loại sau khi áp dụng bộ lọc tìm kiếm theo tên
 */
const filteredGenres = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  if (!query) {
    return genres.value
  }
  return genres.value.filter((g) => g.name.toLowerCase().includes(query))
})

/**
 * Tải danh sách thể loại từ backend Admin API
 */
async function fetchGenres() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const data = await genreService.getAdminGenres()
    genres.value = data || []
  } catch (err: any) {
    errorMessage.value =
      err.response?.data?.message || t('adminGenres.loadFailed')
  } finally {
    isLoading.value = false
  }
}

/**
 * Mở modal tạo mới thể loại
 */
function openCreateModal() {
  isEditing.value = false
  editingGenreId.value = ''
  formError.value = ''
  formData.value = {
    name: '',
    description: '',
  }
  isModalOpen.value = true
}

/**
 * Mở modal chỉnh sửa thể loại
 */
function openEditModal(genre: GenreResponse) {
  isEditing.value = true
  editingGenreId.value = genre.id
  formError.value = ''
  formData.value = {
    name: genre.name,
    description: genre.description || '',
  }
  isModalOpen.value = true
}

/**
 * Validate form phía client trước khi gửi request
 */
function validateForm(): boolean {
  formError.value = ''
  const trimmedName = formData.value.name.trim()

  if (!trimmedName) {
    formError.value = t('adminGenres.nameRequired')
    return false
  }

  if (trimmedName.length > 100) {
    formError.value = t('adminGenres.nameMaxLength')
    return false
  }

  if (formData.value.description && formData.value.description.length > 255) {
    formError.value = t('adminGenres.descMaxLength')
    return false
  }

  return true
}

/**
 * Xử lý lưu thể loại (Tạo mới hoặc Cập nhật)
 */
async function handleSaveGenre() {
  if (!validateForm()) return

  isSaving.value = true
  formError.value = ''

  try {
    const payload = {
      name: formData.value.name.trim(),
      description: formData.value.description.trim() || undefined,
    }

    if (isEditing.value) {
      await genreService.updateGenre(editingGenreId.value, payload)
      toast.success(t('adminGenres.updateSuccess'))
    } else {
      await genreService.createGenre(payload)
      toast.success(t('adminGenres.createSuccess'))
    }

    isModalOpen.value = false
    await fetchGenres()
  } catch (err: any) {
    if (err.response?.status === 409) {
      formError.value = t('adminGenres.duplicateName')
    } else {
      formError.value =
        err.response?.data?.message ||
        (isEditing.value ? 'Cập nhật thất bại' : 'Tạo mới thất bại')
    }
  } finally {
    isSaving.value = false
  }
}

/**
 * Mở modal xác nhận xóa
 */
function openDeleteModal(genre: GenreResponse) {
  deletingGenre.value = genre
  isDeleteModalOpen.value = true
}

/**
 * Thực hiện xóa thể loại
 */
async function handleDeleteGenre() {
  if (!deletingGenre.value) return

  isDeleting.value = true
  try {
    await genreService.deleteGenre(deletingGenre.value.id)
    toast.success(t('adminGenres.deleteSuccess'))
    isDeleteModalOpen.value = false
    deletingGenre.value = null
    await fetchGenres()
  } catch (err: any) {
    toast.error(
      err.response?.data?.message || 'Xóa thể loại thất bại. Vui lòng thử lại.'
    )
  } finally {
    isDeleting.value = false
  }
}

/**
 * Đồng bộ thể loại phim từ TMDB
 */
async function handleSyncTmdb() {
  if (isSyncing.value) return

  isSyncing.value = true
  try {
    const res = await genreService.syncTmdbGenres()
    toast.success(
      t('adminGenres.syncSuccess', {
        total: res.total,
        created: res.created,
        updated: res.updated,
        unchanged: res.unchanged,
      })
    )
    await fetchGenres()
  } catch (err: any) {
    toast.error(
      err.response?.data?.message || t('adminGenres.syncFailed')
    )
  } finally {
    isSyncing.value = false
  }
}

onMounted(() => {
  fetchGenres()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-white tracking-tight flex items-center gap-2">
          🏷️ {{ t('adminGenres.title') }}
        </h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ t('adminGenres.subtitle') }}
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-2.5">
        <!-- Nút đồng bộ TMDB -->
        <Button
          variant="secondary"
          size="md"
          :loading="isSyncing"
          :disabled="isSyncing"
          @click="handleSyncTmdb"
        >
          <template #prefix>
            <span class="text-amber-400">🔄</span>
          </template>
          {{ isSyncing ? t('adminGenres.syncing') : t('adminGenres.syncTmdbBtn') }}
        </Button>

        <!-- Nút tạo mới thể loại -->
        <Button variant="primary" size="md" @click="openCreateModal">
          <template #prefix>
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
          </template>
          {{ t('adminGenres.createBtn') }}
        </Button>
      </div>
    </div>

    <!-- Toolbar: Tìm kiếm và bộ lọc -->
    <Card padding="sm">
      <div class="flex flex-col sm:flex-row items-center justify-between gap-3">
        <div class="relative w-full sm:w-80">
          <span class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </span>
          <Input
            v-model="searchQuery"
            :placeholder="t('adminGenres.searchPlaceholder')"
            class="pl-9 w-full"
          />
        </div>

        <div class="text-xs text-slate-400 self-end sm:self-center">
          <span>Tổng số: <strong class="text-white font-semibold">{{ filteredGenres.length }}</strong> thể loại</span>
        </div>
      </div>
    </Card>

    <!-- Error Alert nếu tải thất bại -->
    <ErrorAlert v-if="errorMessage" :message="errorMessage" class="my-2" />

    <!-- Table danh sách thể loại -->
    <Card padding="none">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-sm">
          <thead>
            <tr class="bg-slate-850 border-b border-slate-700/80 text-xs font-semibold uppercase text-slate-400 tracking-wider">
              <th class="px-5 py-3.5 w-1/4">{{ t('adminGenres.colName') }}</th>
              <th class="px-5 py-3.5 w-1/2">{{ t('adminGenres.colDescription') }}</th>
              <th class="px-5 py-3.5 text-right w-1/4">{{ t('adminGenres.colActions') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-700/60">
            <!-- Loading State -->
            <template v-if="isLoading">
              <tr v-for="i in 5" :key="i" class="animate-pulse">
                <td class="px-5 py-4">
                  <div class="h-4 bg-slate-800 rounded w-32"></div>
                </td>
                <td class="px-5 py-4">
                  <div class="h-4 bg-slate-800 rounded w-64"></div>
                </td>
                <td class="px-5 py-4 text-right">
                  <div class="h-6 bg-slate-800 rounded w-20 ml-auto"></div>
                </td>
              </tr>
            </template>

            <!-- Data Rows -->
            <template v-else-if="filteredGenres.length > 0">
              <tr
                v-for="genre in filteredGenres"
                :key="genre.id"
                class="hover:bg-slate-800/50 transition-colors"
              >
                <!-- Tên thể loại -->
                <td class="px-5 py-4">
                  <div class="flex items-center gap-2">
                    <span class="w-2 h-2 rounded-full bg-indigo-500 shrink-0"></span>
                    <span class="font-medium text-white">{{ genre.name }}</span>
                  </div>
                </td>

                <!-- Mô tả -->
                <td class="px-5 py-4 text-slate-400">
                  <span v-if="genre.description" class="text-xs line-clamp-2">
                    {{ genre.description }}
                  </span>
                  <span v-else class="text-xs text-slate-500 italic">
                    (Chưa có mô tả)
                  </span>
                </td>

                <!-- Thao tác -->
                <td class="px-5 py-4 text-right whitespace-nowrap">
                  <div class="flex items-center justify-end gap-2">
                    <button
                      type="button"
                      class="px-2.5 py-1 text-xs rounded bg-slate-800 text-slate-300 hover:text-white hover:bg-slate-700 border border-slate-700 transition-colors flex items-center gap-1"
                      @click="openEditModal(genre)"
                    >
                      <svg class="w-3.5 h-3.5 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                      </svg>
                      {{ t('adminGenres.editBtn') }}
                    </button>

                    <button
                      type="button"
                      class="px-2.5 py-1 text-xs rounded bg-rose-500/10 text-rose-400 hover:text-rose-300 hover:bg-rose-500/20 border border-rose-500/20 transition-colors flex items-center gap-1"
                      @click="openDeleteModal(genre)"
                    >
                      <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                      </svg>
                      {{ t('adminGenres.deleteBtn') }}
                    </button>
                  </div>
                </td>
              </tr>
            </template>

            <!-- Empty State -->
            <tr v-else>
              <td colspan="3" class="px-5 py-12 text-center text-slate-400">
                <div class="flex flex-col items-center justify-center space-y-3">
                  <div class="w-12 h-12 rounded-full bg-slate-800 flex items-center justify-center text-2xl">
                    🏷️
                  </div>
                  <div>
                    <h3 class="text-base font-semibold text-white">
                      {{ t('adminGenres.emptyList') }}
                    </h3>
                    <p class="text-xs text-slate-400 mt-1 max-w-sm">
                      {{ t('adminGenres.emptyDesc') }}
                    </p>
                  </div>
                  <Button
                    v-if="searchQuery"
                    variant="secondary"
                    size="sm"
                    @click="searchQuery = ''"
                  >
                    Xóa tìm kiếm
                  </Button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </Card>

    <!-- Modal Thêm / Chỉnh Sửa Thể Loại -->
    <Modal
      v-model="isModalOpen"
      :title="isEditing ? t('adminGenres.editModalTitle') : t('adminGenres.createModalTitle')"
      size="md"
    >
      <form class="space-y-4" @submit.prevent="handleSaveGenre">
        <ErrorAlert v-if="formError" :message="formError" />

        <!-- Tên thể loại -->
        <div>
          <label class="text-xs text-slate-400 font-medium block mb-1">
            {{ t('adminGenres.formName') }}
          </label>
          <Input
            v-model="formData.name"
            :placeholder="t('adminGenres.formNamePlaceholder')"
            maxlength="100"
            required
          />
          <span class="text-[11px] text-slate-500 mt-1 block">Tối đa 100 ký tự.</span>
        </div>

        <!-- Mô tả -->
        <div>
          <label class="text-xs text-slate-400 font-medium block mb-1">
            {{ t('adminGenres.formDescription') }}
          </label>
          <textarea
            v-model="formData.description"
            rows="3"
            maxlength="255"
            :placeholder="t('adminGenres.formDescriptionPlaceholder')"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          ></textarea>
          <span class="text-[11px] text-slate-500 mt-1 block">Tối đa 255 ký tự (không bắt buộc).</span>
        </div>
      </form>

      <template #footer>
        <div class="flex items-center justify-end gap-3 w-full">
          <Button
            variant="secondary"
            size="md"
            :disabled="isSaving"
            @click="isModalOpen = false"
          >
            {{ t('adminGenres.cancelBtn') }}
          </Button>
          <Button
            variant="primary"
            size="md"
            :loading="isSaving"
            @click="handleSaveGenre"
          >
            {{ isEditing ? t('adminGenres.updateBtn') : t('adminGenres.saveBtn') }}
          </Button>
        </div>
      </template>
    </Modal>

    <!-- Modal Xác Nhận Xóa -->
    <Modal
      v-model="isDeleteModalOpen"
      :title="t('adminGenres.deleteModalTitle')"
      size="sm"
    >
      <div class="space-y-3">
        <p class="text-sm text-slate-300">
          {{ t('adminGenres.deleteModalMessage', { name: deletingGenre?.name || '' }) }}
        </p>
      </div>

      <template #footer>
        <div class="flex items-center justify-end gap-3 w-full">
          <Button
            variant="secondary"
            size="md"
            :disabled="isDeleting"
            @click="isDeleteModalOpen = false"
          >
            {{ t('adminGenres.cancelBtn') }}
          </Button>
          <Button
            variant="danger"
            size="md"
            :loading="isDeleting"
            @click="handleDeleteGenre"
          >
            {{ t('adminGenres.confirmDeleteBtn') }}
          </Button>
        </div>
      </template>
    </Modal>
  </div>
</template>

