<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import type {
  CinemaSummaryResponse,
  CinemaDetailResponse,
  AuditoriumResponse,
  SeatResponse,
  CreateCinemaRequest,
  UpdateCinemaRequest,
  CreateAuditoriumRequest,
  UpdateAuditoriumRequest,
  CinemaStatus,
  AuditoriumStatus,
  SeatStatus,
} from '@/types/cinema.types'
import cinemaService from '@/services/cinema.service'
import auditoriumService from '@/services/auditorium.service'
import { formatStatus } from '@/utils/formatters'
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

const cinemas = ref<CinemaSummaryResponse[]>([])
const isLoading = ref(true)
const errorMessage = ref('')

// Filters & Pagination
const searchQuery = ref('')
const selectedCity = ref('')
const selectedStatus = ref<string>('ALL')
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const pageSize = ref(9)

// Create / Edit Cinema Modal
const isCinemaModalOpen = ref(false)
const isEditing = ref(false)
const editingCinemaId = ref('')
const isSavingCinema = ref(false)
const cinemaForm = ref<CreateCinemaRequest>({
  name: '',
  address: '',
  city: 'Hà Nội',
  status: 'ACTIVE',
  openingTime: '08:00',
  closingTime: '23:30',
})

// Manage Auditoriums Modal
const isAuditoriumsModalOpen = ref(false)
const selectedCinemaForAuditoriums = ref<CinemaDetailResponse | null>(null)
const cinemaAuditoriums = ref<AuditoriumResponse[]>([])
const isLoadingAuditoriums = ref(false)
const isCreatingAuditorium = ref(false)
const auditoriumForm = ref<CreateAuditoriumRequest>({
  name: '',
  type: 'STANDARD',
  rowsCount: 8,
  columnsCount: 12,
  turnaroundMinutes: 15,
  snapIntervalMinutes: 15,
  status: 'ACTIVE',
})

// Edit Auditorium Modal
const isEditAuditoriumModalOpen = ref(false)
const editingAuditoriumId = ref('')
const isUpdatingAuditorium = ref(false)
const editAuditoriumForm = ref<UpdateAuditoriumRequest>({
  name: '',
  type: 'STANDARD',
  status: 'ACTIVE',
  turnaroundMinutes: 15,
  snapIntervalMinutes: 15,
})

// Manage Seats Modal
const isSeatsModalOpen = ref(false)
const selectedAuditoriumForSeats = ref<AuditoriumResponse | null>(null)
const auditoriumSeats = ref<SeatResponse[]>([])
const isLoadingSeats = ref(false)
const isUpdatingSeat = ref(false)

// Delete Cinema / Auditorium Confirmation
const isDeleteCinemaModalOpen = ref(false)
const deletingCinemaId = ref('')
const isDeletingCinema = ref(false)

const isDeleteAuditoriumModalOpen = ref(false)
const deletingAuditoriumId = ref('')
const isDeletingAuditorium = ref(false)

const cities = ['Hà Nội', 'TP. Hồ Chí Minh', 'Đà Nẵng', 'Hải Phòng', 'Cần Thơ', 'Nha Trang']

function getCinemaStatusBadgeVariant(status: CinemaStatus) {
  return status === 'ACTIVE' ? 'success' : 'danger'
}

function getAuditoriumStatusBadgeVariant(status: AuditoriumStatus) {
  switch (status) {
    case 'ACTIVE':
      return 'success'
    case 'MAINTENANCE':
      return 'warning'
    default:
      return 'neutral'
  }
}

async function fetchCinemas() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const res = await cinemaService.getAdminCinemas({
      q: searchQuery.value.trim() || undefined,
      city: selectedCity.value || undefined,
      status: selectedStatus.value !== 'ALL' ? selectedStatus.value : undefined,
      page: currentPage.value,
      size: pageSize.value,
    })

    cinemas.value = res.content || []
    totalPages.value = res.totalPages || 0
    totalElements.value = res.totalElements || 0
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || 'Không thể tải danh sách cụm rạp.'
  } finally {
    isLoading.value = false
  }
}

function openCreateCinemaModal() {
  isEditing.value = false
  editingCinemaId.value = ''
  cinemaForm.value = {
    name: '',
    address: '',
    city: 'Hà Nội',
    status: 'ACTIVE',
    openingTime: '08:00',
    closingTime: '23:30',
  }
  isCinemaModalOpen.value = true
}

async function openEditCinemaModal(c: CinemaSummaryResponse) {
  isEditing.value = true
  editingCinemaId.value = c.id
  cinemaForm.value = {
    name: c.name,
    address: c.address,
    city: c.city,
    status: c.status,
    openingTime: c.openingTime || '08:00',
    closingTime: c.closingTime || '23:30',
  }
  isCinemaModalOpen.value = true
}

async function handleSaveCinema() {
  if (!cinemaForm.value.name.trim()) {
    toast.error('Vui lòng nhập tên cụm rạp.')
    return
  }
  if (!cinemaForm.value.address.trim()) {
    toast.error('Vui lòng nhập địa chỉ cụm rạp.')
    return
  }
  if (!cinemaForm.value.openingTime || !cinemaForm.value.closingTime) {
    toast.error('Vui lòng chọn giờ mở cửa và đóng cửa.')
    return
  }
  if (cinemaForm.value.openingTime >= cinemaForm.value.closingTime) {
    toast.error('Giờ đóng cửa phải lớn hơn giờ mở cửa.')
    return
  }

  isSavingCinema.value = true
  try {
    if (isEditing.value) {
      await cinemaService.updateCinema(editingCinemaId.value, cinemaForm.value as UpdateCinemaRequest)
      toast.success('Cập nhật cụm rạp thành công!')
    } else {
      await cinemaService.createCinema(cinemaForm.value)
      toast.success('Tạo cụm rạp mới thành công!')
    }
    isCinemaModalOpen.value = false
    await fetchCinemas()
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Thao tác lưu cụm rạp thất bại.')
  } finally {
    isSavingCinema.value = false
  }
}

async function openManageAuditoriums(c: CinemaSummaryResponse) {
  isAuditoriumsModalOpen.value = true
  isLoadingAuditoriums.value = true
  selectedCinemaForAuditoriums.value = null
  cinemaAuditoriums.value = []

  auditoriumForm.value = {
    name: '',
    type: 'STANDARD',
    rowsCount: 8,
    columnsCount: 12,
    turnaroundMinutes: 15,
    snapIntervalMinutes: 15,
    status: 'ACTIVE',
  }

  try {
    const detail = await cinemaService.getAdminCinemaDetail(c.id)
    selectedCinemaForAuditoriums.value = detail
    cinemaAuditoriums.value = detail.auditoriums || []
  } catch (err: any) {
    toast.error('Không thể tải chi tiết phòng chiếu.')
  } finally {
    isLoadingAuditoriums.value = false
  }
}

async function handleCreateAuditorium() {
  if (!selectedCinemaForAuditoriums.value) return
  if (!auditoriumForm.value.name.trim()) {
    toast.error('Vui lòng nhập tên phòng chiếu.')
    return
  }
  if (auditoriumForm.value.rowsCount <= 0 || auditoriumForm.value.columnsCount <= 0) {
    toast.error('Số hàng và số cột ghế phải lớn hơn 0.')
    return
  }

  isCreatingAuditorium.value = true
  try {
    await auditoriumService.createAuditorium(selectedCinemaForAuditoriums.value.id, auditoriumForm.value)
    toast.success('Tạo phòng chiếu và ma trận ghế tự động thành công!')

    const detail = await cinemaService.getAdminCinemaDetail(selectedCinemaForAuditoriums.value.id)
    selectedCinemaForAuditoriums.value = detail
    cinemaAuditoriums.value = detail.auditoriums || []

    auditoriumForm.value.name = ''
    await fetchCinemas()
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Tạo phòng chiếu thất bại.')
  } finally {
    isCreatingAuditorium.value = false
  }
}

function openEditAuditoriumModal(a: AuditoriumResponse) {
  editingAuditoriumId.value = a.id
  editAuditoriumForm.value = {
    name: a.name,
    type: a.type || 'STANDARD',
    status: a.status || 'ACTIVE',
    turnaroundMinutes: a.turnaroundMinutes ?? 15,
    snapIntervalMinutes: a.snapIntervalMinutes ?? 15,
  }
  isEditAuditoriumModalOpen.value = true
}

async function handleUpdateAuditorium() {
  if (!editAuditoriumForm.value.name.trim()) {
    toast.error('Vui lòng nhập tên phòng chiếu.')
    return
  }

  isUpdatingAuditorium.value = true
  try {
    await auditoriumService.updateAuditorium(editingAuditoriumId.value, editAuditoriumForm.value)
    toast.success('Cập nhật phòng chiếu thành công!')
    isEditAuditoriumModalOpen.value = false

    if (selectedCinemaForAuditoriums.value) {
      const detail = await cinemaService.getAdminCinemaDetail(selectedCinemaForAuditoriums.value.id)
      selectedCinemaForAuditoriums.value = detail
      cinemaAuditoriums.value = detail.auditoriums || []
    }
    await fetchCinemas()
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Cập nhật phòng chiếu thất bại.')
  } finally {
    isUpdatingAuditorium.value = false
  }
}

function openDeleteAuditoriumModal(auditoriumId: string) {
  deletingAuditoriumId.value = auditoriumId
  isDeleteAuditoriumModalOpen.value = true
}

async function handleDeleteAuditorium() {
  if (!deletingAuditoriumId.value) return

  isDeletingAuditorium.value = true
  try {
    await auditoriumService.deleteAuditorium(deletingAuditoriumId.value)
    toast.success('Xóa / ngừng hoạt động phòng chiếu thành công!')
    isDeleteAuditoriumModalOpen.value = false

    if (selectedCinemaForAuditoriums.value) {
      const detail = await cinemaService.getAdminCinemaDetail(selectedCinemaForAuditoriums.value.id)
      selectedCinemaForAuditoriums.value = detail
      cinemaAuditoriums.value = detail.auditoriums || []
    }
    await fetchCinemas()
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Xóa phòng chiếu thất bại.')
  } finally {
    isDeletingAuditorium.value = false
  }
}

async function openManageSeats(a: AuditoriumResponse) {
  selectedAuditoriumForSeats.value = a
  isSeatsModalOpen.value = true
  isLoadingSeats.value = true
  auditoriumSeats.value = []

  try {
    const seats = await auditoriumService.getAuditoriumSeats(a.id)
    auditoriumSeats.value = seats || []
  } catch (err: any) {
    toast.error('Không thể tải sơ đồ ghế phòng chiếu.')
  } finally {
    isLoadingSeats.value = false
  }
}

async function toggleSeatStatus(seat: SeatResponse) {
  if (!selectedAuditoriumForSeats.value) return
  const newStatus: SeatStatus = seat.status === 'ACTIVE' ? 'BROKEN' : 'ACTIVE'

  isUpdatingSeat.value = true
  try {
    await auditoriumService.updateSeatStatus(selectedAuditoriumForSeats.value.id, seat.id, { status: newStatus })
    seat.status = newStatus
    toast.success(`Đã đổi trạng thái ghế ${seat.seatCode} sang ${newStatus === 'ACTIVE' ? 'Hoạt động' : 'Hỏng/Bảo trì'}`)
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Cập nhật trạng thái ghế thất bại.')
  } finally {
    isUpdatingSeat.value = false
  }
}

function openDeleteCinemaModal(cinemaId: string) {
  deletingCinemaId.value = cinemaId
  isDeleteCinemaModalOpen.value = true
}

async function handleDeleteCinema() {
  if (!deletingCinemaId.value) return

  isDeletingCinema.value = true
  try {
    await cinemaService.deleteCinema(deletingCinemaId.value)
    toast.success('Đóng cửa cụm rạp thành công!')
    isDeleteCinemaModalOpen.value = false
    await fetchCinemas()
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Xóa cụm rạp thất bại.')
  } finally {
    isDeletingCinema.value = false
  }
}

let debounceTimer: any = null
watch(searchQuery, () => {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    currentPage.value = 0
    fetchCinemas()
  }, 400)
})

watch([selectedCity, selectedStatus], () => {
  currentPage.value = 0
  fetchCinemas()
})

watch(currentPage, () => {
  fetchCinemas()
})

onMounted(() => {
  fetchCinemas()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-white tracking-tight flex items-center gap-2">
          🏢 {{ t('adminCinemas.title') }}
        </h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ t('adminCinemas.subtitle') }}
        </p>
      </div>

      <div class="flex items-center gap-3">
        <Button variant="primary" size="md" @click="openCreateCinemaModal">
          <template #prefix>
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
          </template>
          Thêm Cụm Rạp Mới
        </Button>
      </div>
    </div>

    <!-- Error Alert -->
    <ErrorAlert v-if="errorMessage" :message="errorMessage" @retry="fetchCinemas" />

    <!-- Filters -->
    <Card padding="sm">
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div>
          <Input
            v-model="searchQuery"
            placeholder="Tìm theo tên rạp, địa chỉ..."
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
            v-model="selectedCity"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="">Tất cả thành phố</option>
            <option v-for="c in cities" :key="c" :value="c">{{ c }}</option>
          </select>
        </div>

        <div>
          <select
            v-model="selectedStatus"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="ALL">Tất cả trạng thái</option>
            <option value="ACTIVE">Đang hoạt động (ACTIVE)</option>
            <option value="CLOSED">Đã đóng cửa (CLOSED)</option>
          </select>
        </div>
      </div>
    </Card>

    <!-- Grid of Cinemas -->
    <div v-if="isLoading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 animate-pulse">
      <div
        v-for="i in 6"
        :key="'skel-cin-' + i"
        class="rounded-2xl bg-slate-900 border border-slate-800 p-6 space-y-4"
      >
        <div class="flex justify-between items-start">
          <div class="space-y-1.5">
            <div class="h-5 w-36 bg-slate-800 rounded"></div>
            <div class="h-3.5 w-20 bg-slate-800 rounded"></div>
          </div>
          <div class="h-6 w-20 bg-slate-800 rounded-full"></div>
        </div>
        <div class="space-y-2 pt-3 border-t border-slate-800">
          <div class="h-3 w-full bg-slate-800 rounded"></div>
          <div class="h-3 w-3/4 bg-slate-800 rounded"></div>
          <div class="h-3 w-1/2 bg-slate-800 rounded"></div>
        </div>
        <div class="pt-4 border-t border-slate-800 flex justify-between">
          <div class="h-8 w-24 bg-slate-800 rounded"></div>
          <div class="h-8 w-16 bg-slate-800 rounded"></div>
        </div>
      </div>
    </div>

    <div v-else-if="cinemas.length === 0" class="py-16 text-center text-slate-400">
      <div class="max-w-sm mx-auto space-y-2">
        <svg class="w-10 h-10 mx-auto text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
        </svg>
        <p class="text-sm font-medium text-slate-300">Không tìm thấy cụm rạp nào</p>
        <p class="text-xs text-slate-500">Thử thay đổi từ khóa tìm kiếm hoặc chọn thành phố khác.</p>
      </div>
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <Card
        v-for="c in cinemas"
        :key="c.id"
        class="flex flex-col justify-between hover:border-slate-600 transition-colors"
      >
        <div class="space-y-3">
          <div class="flex items-start justify-between gap-2">
            <div>
              <h3 class="text-base font-bold text-white">{{ c.name }}</h3>
              <p class="text-xs text-indigo-400 font-medium mt-0.5">{{ c.city }}</p>
            </div>
            <Badge :variant="getCinemaStatusBadgeVariant(c.status)" size="sm">
              {{ formatStatus(c.status) }}
            </Badge>
          </div>

          <div class="text-xs text-slate-300 space-y-1.5 pt-3 border-t border-slate-700/60">
            <p class="line-clamp-2">
              <span class="text-slate-400">Địa chỉ:</span> {{ c.address }}
            </p>
            <p>
              <span class="text-slate-400">Số phòng chiếu:</span>
              <strong class="text-emerald-400 ml-1">{{ c.auditoriumsCount || 0 }} phòng</strong>
            </p>
            <p v-if="c.openingTime && c.closingTime">
              <span class="text-slate-400">Giờ hoạt động:</span> {{ c.openingTime }} - {{ c.closingTime }}
            </p>
          </div>
        </div>

        <template #footer>
          <div class="flex items-center justify-between w-full pt-2">
            <Button variant="secondary" size="sm" @click="openManageAuditoriums(c)">
              Phòng chiếu ({{ c.auditoriumsCount || 0 }})
            </Button>
            <div class="space-x-1">
              <Button variant="ghost" size="sm" @click="openEditCinemaModal(c)">
                Sửa
              </Button>
              <Button
                v-if="c.status === 'ACTIVE'"
                variant="ghost"
                size="sm"
                class="text-rose-400 hover:text-rose-300"
                @click="openDeleteCinemaModal(c.id)"
              >
                Đóng cửa
              </Button>
            </div>
          </div>
        </template>
      </Card>
    </div>

    <!-- Pagination -->
    <Pagination
      v-if="totalPages > 1"
      v-model:currentPage="currentPage"
      :totalPages="totalPages"
      :totalElements="totalElements"
      :pageSize="pageSize"
    />

    <!-- Create / Edit Cinema Modal -->
    <Modal
      v-model="isCinemaModalOpen"
      :title="isEditing ? 'Cập Nhật Cụm Rạp' : 'Thêm Cụm Rạp Mới'"
      size="md"
    >
      <form class="space-y-4" @submit.prevent="handleSaveCinema">
        <div>
          <label class="text-xs text-slate-400 font-medium block mb-1">Tên cụm rạp *</label>
          <Input v-model="cinemaForm.name" placeholder="CineBook Vincom Bà Triệu" required />
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Thành phố *</label>
            <select
              v-model="cinemaForm.city"
              required
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option v-for="city in cities" :key="city" :value="city">{{ city }}</option>
            </select>
          </div>

          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Trạng thái</label>
            <select
              v-model="cinemaForm.status"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="ACTIVE">ACTIVE (Đang hoạt động)</option>
              <option value="CLOSED">CLOSED (Đã đóng cửa)</option>
            </select>
          </div>
        </div>

        <div>
          <label class="text-xs text-slate-400 font-medium block mb-1">Địa chỉ chi tiết *</label>
          <Input v-model="cinemaForm.address" placeholder="Tầng 6, Vincom Center, 191 Bà Triệu, Q. Hai Bà Trưng" required />
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Giờ mở cửa *</label>
            <input
              v-model="cinemaForm.openingTime"
              type="time"
              required
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Giờ đóng cửa *</label>
            <input
              v-model="cinemaForm.closingTime"
              type="time"
              required
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
        </div>
      </form>

      <template #footer>
        <Button variant="secondary" size="md" :disabled="isSavingCinema" @click="isCinemaModalOpen = false">
          Hủy bỏ
        </Button>
        <Button variant="primary" size="md" :loading="isSavingCinema" @click="handleSaveCinema">
          {{ isEditing ? 'Cập nhật' : 'Tạo mới' }}
        </Button>
      </template>
    </Modal>

    <!-- Manage Auditoriums Modal -->
    <Modal
      v-model="isAuditoriumsModalOpen"
      title="Quản Lý Phòng Chiếu & Sơ Đồ Ghế"
      size="xl"
    >
      <div class="space-y-6">
        <div class="flex items-center justify-between pb-3 border-b border-slate-800">
          <div>
            <h4 class="text-base font-bold text-white">{{ selectedCinemaForAuditoriums?.name }}</h4>
            <p class="text-xs text-slate-400 mt-0.5">
              {{ selectedCinemaForAuditoriums?.address }} — {{ selectedCinemaForAuditoriums?.city }}
            </p>
          </div>
          <Badge variant="primary" size="sm">
            {{ cinemaAuditoriums.length }} phòng chiếu
          </Badge>
        </div>

        <!-- Create Auditorium Form -->
        <div class="p-4 rounded-xl bg-slate-900 border border-slate-800 space-y-4">
          <h5 class="text-xs font-bold text-white uppercase tracking-wider flex items-center gap-2">
            <span>✨</span> Thêm Phòng Chiếu Mới (Tự động sinh sơ đồ ghế)
          </h5>
          <form class="space-y-3" @submit.prevent="handleCreateAuditorium">
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label class="text-xs text-slate-400 font-medium block mb-1">Tên phòng *</label>
                <Input v-model="auditoriumForm.name" placeholder="Phòng 01 (IMAX)" required />
              </div>
              <div>
                <label class="text-xs text-slate-400 font-medium block mb-1">Loại phòng *</label>
                <select
                  v-model="auditoriumForm.type"
                  required
                  class="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                >
                  <option value="STANDARD">STANDARD (Tiêu chuẩn)</option>
                  <option value="IMAX">IMAX (Cao cấp)</option>
                </select>
              </div>
              <div>
                <label class="text-xs text-slate-400 font-medium block mb-1">Trạng thái *</label>
                <select
                  v-model="auditoriumForm.status"
                  required
                  class="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
                >
                  <option value="ACTIVE">ACTIVE (Hoạt động)</option>
                  <option value="MAINTENANCE">MAINTENANCE (Bảo trì)</option>
                </select>
              </div>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-4 gap-3">
              <div>
                <label class="text-xs text-slate-400 font-medium block mb-1">Số hàng (A-Z) *</label>
                <Input v-model="auditoriumForm.rowsCount" type="number" min="1" max="26" required />
              </div>
              <div>
                <label class="text-xs text-slate-400 font-medium block mb-1">Số cột (1-50) *</label>
                <Input v-model="auditoriumForm.columnsCount" type="number" min="1" max="50" required />
              </div>
              <div>
                <label class="text-xs text-slate-400 font-medium block mb-1">Dọn dẹp (phút)</label>
                <Input v-model="auditoriumForm.turnaroundMinutes" type="number" min="0" />
              </div>
              <div>
                <label class="text-xs text-slate-400 font-medium block mb-1">Snap slot (phút)</label>
                <Input v-model="auditoriumForm.snapIntervalMinutes" type="number" min="1" />
              </div>
            </div>

            <div class="flex justify-end pt-1">
              <Button variant="primary" size="sm" type="submit" :loading="isCreatingAuditorium">
                Tạo phòng & Sinh {{ (auditoriumForm.rowsCount || 0) * (auditoriumForm.columnsCount || 0) }} ghế
              </Button>
            </div>
          </form>
        </div>

        <!-- Auditoriums List -->
        <div class="space-y-3">
          <h5 class="text-xs font-bold text-slate-400 uppercase tracking-wider">Danh Sách Phòng Chiếu</h5>
          <div v-if="isLoadingAuditoriums" class="py-6 text-center text-slate-400">
            <div class="inline-flex items-center gap-2">
              <div class="w-4 h-4 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin"></div>
              <span>Đang tải phòng chiếu...</span>
            </div>
          </div>
          <div v-else-if="cinemaAuditoriums.length === 0" class="py-6 text-center text-slate-400 text-sm">
            Rạp này chưa có phòng chiếu nào. Hãy tạo phòng chiếu đầu tiên ở trên.
          </div>
          <div v-else class="divide-y divide-slate-800 border border-slate-800 rounded-xl overflow-hidden">
            <div
              v-for="a in cinemaAuditoriums"
              :key="a.id"
              class="p-4 bg-slate-900/50 flex flex-col sm:flex-row sm:items-center justify-between gap-3 hover:bg-slate-900 transition-colors"
            >
              <div class="space-y-1">
                <div class="flex items-center gap-2">
                  <p class="font-bold text-white text-sm">{{ a.name }}</p>
                  <span class="text-[11px] font-mono px-2 py-0.5 rounded bg-indigo-950 text-indigo-300 border border-indigo-800">
                    {{ a.type }}
                  </span>
                  <Badge :variant="getAuditoriumStatusBadgeVariant(a.status)" size="sm">
                    {{ a.status }}
                  </Badge>
                </div>
                <p class="text-xs text-slate-400">
                  Tổng ghế: <strong class="text-emerald-400">{{ a.totalSeats }}</strong> ({{ a.rowsCount }} hàng x {{ a.columnsCount }} cột) • Dọn dẹp: {{ a.turnaroundMinutes || 15 }}p
                </p>
              </div>

              <div class="flex items-center gap-2">
                <Button variant="secondary" size="sm" @click="openManageSeats(a)">
                  Sơ đồ ghế
                </Button>
                <Button variant="ghost" size="sm" @click="openEditAuditoriumModal(a)">
                  Sửa
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  class="text-rose-400 hover:text-rose-300"
                  @click="openDeleteAuditoriumModal(a.id)"
                >
                  Xóa
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <Button variant="secondary" size="md" @click="isAuditoriumsModalOpen = false">
          Đóng
        </Button>
      </template>
    </Modal>

    <!-- Edit Auditorium Modal -->
    <Modal
      v-model="isEditAuditoriumModalOpen"
      title="Cập Nhật Phòng Chiếu"
      size="md"
    >
      <form class="space-y-4" @submit.prevent="handleUpdateAuditorium">
        <div>
          <label class="text-xs text-slate-400 font-medium block mb-1">Tên phòng chiếu *</label>
          <Input v-model="editAuditoriumForm.name" required />
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Loại phòng *</label>
            <select
              v-model="editAuditoriumForm.type"
              required
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="STANDARD">STANDARD (Tiêu chuẩn)</option>
              <option value="IMAX">IMAX (Cao cấp)</option>
            </select>
          </div>
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Trạng thái *</label>
            <select
              v-model="editAuditoriumForm.status"
              required
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="ACTIVE">ACTIVE (Đang hoạt động)</option>
              <option value="MAINTENANCE">MAINTENANCE (Đang bảo trì)</option>
            </select>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Dọn dẹp giữa 2 suất (phút)</label>
            <Input v-model="editAuditoriumForm.turnaroundMinutes" type="number" min="0" />
          </div>
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Snap interval (phút)</label>
            <Input v-model="editAuditoriumForm.snapIntervalMinutes" type="number" min="1" />
          </div>
        </div>
      </form>

      <template #footer>
        <Button variant="secondary" size="md" :disabled="isUpdatingAuditorium" @click="isEditAuditoriumModalOpen = false">
          Hủy bỏ
        </Button>
        <Button variant="primary" size="md" :loading="isUpdatingAuditorium" @click="handleUpdateAuditorium">
          Lưu thay đổi
        </Button>
      </template>
    </Modal>

    <!-- Manage Seats Modal -->
    <Modal
      v-model="isSeatsModalOpen"
      :title="`Sơ Đồ Ghế — ${selectedAuditoriumForSeats?.name || ''}`"
      size="xl"
    >
      <div class="space-y-4">
        <div class="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-slate-800 text-xs">
          <div class="flex items-center gap-4">
            <span class="flex items-center gap-1.5 text-slate-300">
              <span class="w-3 h-3 rounded bg-slate-800 border border-slate-600"></span> Standard
            </span>
            <span class="flex items-center gap-1.5 text-slate-300">
              <span class="w-3 h-3 rounded bg-amber-500/20 border border-amber-500/50"></span> VIP
            </span>
            <span class="flex items-center gap-1.5 text-slate-300">
              <span class="w-3 h-3 rounded bg-rose-950 border border-rose-600"></span> Hỏng / Bảo trì
            </span>
          </div>
          <span class="text-slate-400 italic">Click vào ghế để bật/tắt trạng thái Hỏng (BROKEN)</span>
        </div>

        <div v-if="isLoadingSeats" class="py-12 text-center text-slate-400">
          <div class="inline-flex items-center gap-2">
            <div class="w-5 h-5 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin"></div>
            <span>Đang tải sơ đồ ghế...</span>
          </div>
        </div>

        <div v-else class="overflow-x-auto p-4 bg-slate-950 rounded-xl border border-slate-800 max-h-[55vh]">
          <div class="w-full flex justify-center mb-6">
            <div class="w-3/4 py-1 text-center text-xs font-bold text-slate-400 bg-slate-850 rounded-lg border border-slate-700 tracking-widest uppercase">
              MÀN HÌNH CHIẾU
            </div>
          </div>

          <div class="flex flex-col items-center gap-2 min-w-[500px]">
            <div
              v-for="row in Array.from(new Set(auditoriumSeats.map(s => s.rowLabel))).sort()"
              :key="row"
              class="flex items-center gap-1.5"
            >
              <span class="w-6 text-center text-xs font-bold text-slate-400">{{ row }}</span>
              <div class="flex items-center gap-1.5">
                <button
                  v-for="seat in auditoriumSeats.filter(s => s.rowLabel === row).sort((a,b) => a.seatNumber - b.seatNumber)"
                  :key="seat.id"
                  type="button"
                  :class="[
                    'w-7 h-7 rounded text-[10px] font-bold transition-all flex items-center justify-center border',
                    seat.status === 'BROKEN'
                      ? 'bg-rose-950/80 border-rose-600 text-rose-300'
                      : seat.seatTypeName === 'VIP'
                      ? 'bg-amber-500/20 border-amber-500 text-amber-300 hover:bg-amber-500/30'
                      : 'bg-slate-800 border-slate-600 text-slate-200 hover:bg-slate-700'
                  ]"
                  :title="`Ghế ${seat.seatCode} (${seat.seatTypeName}) - ${seat.status}`"
                  @click="toggleSeatStatus(seat)"
                >
                  {{ seat.seatNumber }}
                </button>
              </div>
              <span class="w-6 text-center text-xs font-bold text-slate-400">{{ row }}</span>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <Button variant="secondary" size="md" @click="isSeatsModalOpen = false">
          Đóng
        </Button>
      </template>
    </Modal>

    <!-- Delete Cinema Confirmation Modal -->
    <Modal
      v-model="isDeleteCinemaModalOpen"
      title="Xác Nhận Đóng Cửa Cụm Rạp"
      size="sm"
    >
      <p class="text-sm text-slate-200 leading-relaxed">
        Bạn có chắc chắn muốn đóng cửa cụm rạp này? Sau khi đóng cửa, cụm rạp sẽ chuyển sang trạng thái <strong>CLOSED</strong> và các suất chiếu tương lai tại đây sẽ ngừng mở bán.
      </p>

      <template #footer>
        <Button variant="secondary" size="md" :disabled="isDeletingCinema" @click="isDeleteCinemaModalOpen = false">
          Hủy bỏ
        </Button>
        <Button variant="danger" size="md" :loading="isDeletingCinema" @click="handleDeleteCinema">
          Xác nhận đóng cửa
        </Button>
      </template>
    </Modal>

    <!-- Delete Auditorium Confirmation Modal -->
    <Modal
      v-model="isDeleteAuditoriumModalOpen"
      title="Xác Nhận Xóa Phòng Chiếu"
      size="sm"
    >
      <p class="text-sm text-slate-200 leading-relaxed">
        Bạn có chắc chắn muốn xóa phòng chiếu này? Phòng chiếu sẽ được chuyển sang trạng thái ngừng hoạt động và không thể tạo thêm suất chiếu mới.
      </p>

      <template #footer>
        <Button variant="secondary" size="md" :disabled="isDeletingAuditorium" @click="isDeleteAuditoriumModalOpen = false">
          Hủy bỏ
        </Button>
        <Button variant="danger" size="md" :loading="isDeletingAuditorium" @click="handleDeleteAuditorium">
          Xác nhận xóa
        </Button>
      </template>
    </Modal>
  </div>
</template>
