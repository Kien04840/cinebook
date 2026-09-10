<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { buildSeatGrid } from '@/utils/seatGrid'
import { getAdminSeatClass, getSeatLegendClass } from '@/utils/seatTypePresentation'
import type {
  CinemaSummaryResponse,
  CinemaDetailResponse,
  AuditoriumResponse,
  AuditoriumDetailResponse,
  SeatResponse,
  CreateCinemaRequest,
  UpdateCinemaRequest,
  CreateAuditoriumRequest,
  UpdateAuditoriumRequest,
  NormalizeEmptyLayoutsResponse,
  BatchUpdateSeatTypePreviewResponse,
  CinemaStatus,
  AuditoriumStatus,
  SeatStatus,
} from '@/types/cinema.types'
import type { SeatTypeResponse } from '@/types/seatType.types'
import cinemaService from '@/services/cinema.service'
import auditoriumService from '@/services/auditorium.service'
import seatTypeService from '@/services/seatType.service'
import { formatStatus } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import { useToast } from '@/composables/useToast'
import { CANONICAL_CITIES, getCityLabel } from '@/utils/constants'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Input from '@/components/common/Input.vue'
import Badge from '@/components/common/Badge.vue'
import Modal from '@/components/common/Modal.vue'
import Pagination from '@/components/common/Pagination.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const { t, locale } = useI18n()
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
  city: 'Hanoi',
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
const adminSeatGrid = computed(() => {
  return buildSeatGrid(auditoriumSeats.value, selectedAuditoriumForSeats.value?.columnsCount)
})
const uniqueSeatTypesInAuditorium = computed(() => {
  const map = new Map<string, { code: string; name: string; colorToken?: string; capacity?: number }>()
  for (const s of auditoriumSeats.value) {
    const key = s.seatTypeCode || s.seatTypeId
    if (!map.has(key)) {
      map.set(key, {
        code: s.seatTypeCode || '',
        name: s.seatTypeName || '',
        colorToken: s.colorToken,
        capacity: s.capacity,
      })
    }
  }
  return Array.from(map.values())
})
const isLoadingSeats = ref(false)

const currentAuditoriumDetail = ref<AuditoriumDetailResponse | null>(null)
const availableSeatTypes = ref<SeatTypeResponse[]>([])
const isSeatEditModalOpen = ref(false)
const editingSeat = ref<SeatResponse | null>(null)
const editSeatTypeId = ref('')
const editSeatStatus = ref<SeatStatus>('ACTIVE')
const isSavingSeatEdit = ref(false)
const seatEditError = ref('')
const isResettingLayout = ref(false)
const isNormalizingLayouts = ref(false)
const normalizeResultModalOpen = ref(false)
const normalizeResult = ref<NormalizeEmptyLayoutsResponse | null>(null)

// Multi-seat bulk editing
const isMultiSelectMode = ref(false)
const selectedSeatIds = ref<string[]>([])
const selectedBulkSeatTypeId = ref('')
const selectedBulkStatus = ref<SeatStatus>('ACTIVE')
const isApplyingBatchSeatType = ref(false)
const isApplyingBatchStatus = ref(false)
const isCoupleConfirmModalOpen = ref(false)
const couplePreviewData = ref<BatchUpdateSeatTypePreviewResponse | null>(null)

// Delete Cinema / Auditorium Confirmation
const isDeleteCinemaModalOpen = ref(false)
const deletingCinemaId = ref('')
const isDeletingCinema = ref(false)

const isDeleteAuditoriumModalOpen = ref(false)
const deletingAuditoriumId = ref('')
const isDeletingAuditorium = ref(false)

const cities = CANONICAL_CITIES

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
    city: 'Hanoi',
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
  currentAuditoriumDetail.value = null
  selectedSeatIds.value = []
  isMultiSelectMode.value = false
  couplePreviewData.value = null

  try {
    const [detail, activeTypes] = await Promise.all([
      auditoriumService.getAuditoriumDetail(a.id),
      seatTypeService.getActiveSeatTypes().catch(() => []),
    ])
    currentAuditoriumDetail.value = detail
    auditoriumSeats.value = detail.seats || []
    availableSeatTypes.value = activeTypes || []
    if (activeTypes && activeTypes.length > 0) {
      selectedBulkSeatTypeId.value = activeTypes[0].id
    }
  } catch (err: any) {
    toast.error('Không thể tải sơ đồ ghế phòng chiếu.')
  } finally {
    isLoadingSeats.value = false
  }
}

function toggleSeatSelection(seat: SeatResponse) {
  const idx = selectedSeatIds.value.indexOf(seat.id)
  if (idx >= 0) {
    selectedSeatIds.value.splice(idx, 1)
  } else {
    selectedSeatIds.value.push(seat.id)
  }
}

function handleSeatClick(seat: SeatResponse) {
  if (isMultiSelectMode.value || selectedSeatIds.value.length > 0) {
    toggleSeatSelection(seat)
  } else {
    openSeatEditModal(seat)
  }
}

function toggleRowSelection(rowLabel: string) {
  const rowSeats = auditoriumSeats.value.filter(s => s.rowLabel === rowLabel)
  if (rowSeats.length === 0) return
  const allSelected = rowSeats.every(s => selectedSeatIds.value.includes(s.id))
  if (allSelected) {
    selectedSeatIds.value = selectedSeatIds.value.filter(id => !rowSeats.some(s => s.id === id))
  } else {
    const toAdd = rowSeats.filter(s => !selectedSeatIds.value.includes(s.id)).map(s => s.id)
    selectedSeatIds.value.push(...toAdd)
  }
}

function isRowSelected(rowLabel: string) {
  const rowSeats = auditoriumSeats.value.filter(s => s.rowLabel === rowLabel)
  return rowSeats.length > 0 && rowSeats.every(s => selectedSeatIds.value.includes(s.id))
}

function selectAllSeats() {
  selectedSeatIds.value = auditoriumSeats.value.map(s => s.id)
}

function clearSeatSelection() {
  selectedSeatIds.value = []
}

async function handleBulkApplySeatType() {
  if (!selectedAuditoriumForSeats.value || selectedSeatIds.value.length === 0 || !selectedBulkSeatTypeId.value) return
  if (!currentAuditoriumDetail.value?.canModifySeatTypes) {
    toast.error('Không thể thay đổi loại ghế của phòng chiếu đã phát sinh giao dịch đặt vé.')
    return
  }

  const targetType = availableSeatTypes.value.find(st => st.id === selectedBulkSeatTypeId.value)
  const isCouple = targetType?.code === 'COUPLE' || (targetType?.capacity || 1) > 1

  if (isCouple) {
    try {
      const preview = await auditoriumService.previewBatchUpdateSeatType(selectedAuditoriumForSeats.value.id, {
        seatIds: selectedSeatIds.value,
        seatTypeId: selectedBulkSeatTypeId.value,
      })
      couplePreviewData.value = preview
      if (!preview.isValid) {
        toast.error(preview.validationError || 'Không thể chuyển các ghế đã chọn thành ghế đôi.')
        return
      }
      if (preview.willDeleteSeatCodes && preview.willDeleteSeatCodes.length > 0) {
        isCoupleConfirmModalOpen.value = true
        return
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Lỗi khi kiểm tra sơ đồ ghế đôi.')
      return
    }
  }

  await executeBatchUpdateSeatType()
}

async function executeBatchUpdateSeatType() {
  if (!selectedAuditoriumForSeats.value || selectedSeatIds.value.length === 0 || !selectedBulkSeatTypeId.value) return

  isApplyingBatchSeatType.value = true
  try {
    const auditoriumId = selectedAuditoriumForSeats.value.id
    await auditoriumService.batchUpdateSeatType(auditoriumId, {
      seatIds: selectedSeatIds.value,
      seatTypeId: selectedBulkSeatTypeId.value,
    })
    toast.success('Cập nhật loại ghế thành công!')
    isCoupleConfirmModalOpen.value = false
    couplePreviewData.value = null
    selectedSeatIds.value = []

    const detail = await auditoriumService.getAuditoriumDetail(auditoriumId)
    currentAuditoriumDetail.value = detail
    auditoriumSeats.value = detail.seats || []
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Cập nhật loại ghế thất bại.')
  } finally {
    isApplyingBatchSeatType.value = false
  }
}

async function handleBulkApplyStatus() {
  if (!selectedAuditoriumForSeats.value || selectedSeatIds.value.length === 0) return

  isApplyingBatchStatus.value = true
  try {
    const auditoriumId = selectedAuditoriumForSeats.value.id
    await auditoriumService.batchUpdateSeatStatus(auditoriumId, {
      seatIds: selectedSeatIds.value,
      status: selectedBulkStatus.value,
    })
    toast.success(`Đã cập nhật trạng thái cho ${selectedSeatIds.value.length} ghế!`)
    selectedSeatIds.value = []

    const detail = await auditoriumService.getAuditoriumDetail(auditoriumId)
    currentAuditoriumDetail.value = detail
    auditoriumSeats.value = detail.seats || []
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Cập nhật trạng thái ghế thất bại.')
  } finally {
    isApplyingBatchStatus.value = false
  }
}

function openSeatEditModal(seat: SeatResponse) {
  editingSeat.value = seat
  editSeatTypeId.value = seat.seatTypeId
  editSeatStatus.value = seat.status
  seatEditError.value = ''
  isSeatEditModalOpen.value = true
}

async function handleSaveSeatEdit() {
  if (!selectedAuditoriumForSeats.value || !editingSeat.value) return

  seatEditError.value = ''
  isSavingSeatEdit.value = true

  const auditoriumId = selectedAuditoriumForSeats.value.id
  const seatId = editingSeat.value.id
  let hasChanged = false

  try {
    // 1. Check if seat type changed
    if (editSeatTypeId.value !== editingSeat.value.seatTypeId) {
      if (!currentAuditoriumDetail.value?.canModifySeatTypes) {
        seatEditError.value = 'Không thể thay đổi loại ghế của phòng chiếu đã phát sinh giao dịch đặt vé.'
        isSavingSeatEdit.value = false
        return
      }
      const updatedSeat = await auditoriumService.updateSeatType(auditoriumId, seatId, editSeatTypeId.value)
      editingSeat.value.seatTypeId = updatedSeat.seatTypeId
      editingSeat.value.seatTypeName = updatedSeat.seatTypeName
      editingSeat.value.seatTypeCode = updatedSeat.seatTypeCode
      editingSeat.value.priceModifier = updatedSeat.priceModifier
      editingSeat.value.capacity = updatedSeat.capacity
      editingSeat.value.colorToken = updatedSeat.colorToken
      editingSeat.value.icon = updatedSeat.icon
      hasChanged = true
    }

    // 2. Check if seat status changed
    if (editSeatStatus.value !== editingSeat.value.status) {
      const updatedSeat = await auditoriumService.updateSeatStatus(auditoriumId, seatId, { status: editSeatStatus.value })
      editingSeat.value.status = updatedSeat.status
      hasChanged = true
    }

    if (hasChanged) {
      toast.success(`Cập nhật ghế ${editingSeat.value.seatCode} thành công!`)
      // Refresh auditorium detail
      const detail = await auditoriumService.getAuditoriumDetail(auditoriumId)
      currentAuditoriumDetail.value = detail
      auditoriumSeats.value = detail.seats || []
    }
    isSeatEditModalOpen.value = false
  } catch (err: any) {
    seatEditError.value = err.response?.data?.message || 'Cập nhật ghế thất bại.'
    toast.error(err.response?.data?.message || 'Cập nhật ghế thất bại.')
  } finally {
    isSavingSeatEdit.value = false
  }
}

async function handleResetAuditoriumLayout() {
  if (!selectedAuditoriumForSeats.value) return
  if (!confirm('Bạn có chắc chắn muốn thiết lập lại sơ đồ ghế chuẩn (Standard - VIP - Couple) cho phòng chiếu này? Thao tác này chỉ áp dụng cho phòng chưa có lịch chiếu và giao dịch.')) {
    return
  }

  isResettingLayout.value = true
  try {
    const detail = await auditoriumService.resetAuditoriumLayout(selectedAuditoriumForSeats.value.id)
    currentAuditoriumDetail.value = detail
    auditoriumSeats.value = detail.seats || []
    toast.success('Thiết lập lại sơ đồ ghế thực tế thành công!')
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Không thể thiết lập lại sơ đồ ghế.')
  } finally {
    isResettingLayout.value = false
  }
}

async function handleNormalizeEmptyLayouts() {
  if (!confirm('Hệ thống sẽ tự động quét và chuẩn hóa sơ đồ ghế (Standard - VIP - Couple) cho tất cả các phòng chiếu hoàn toàn trống (0 suất chiếu, 0 đặt vé, 0 vé). Các phòng đã có dữ liệu sẽ được giữ nguyên 100%. Bạn có muốn tiếp tục?')) {
    return
  }

  isNormalizingLayouts.value = true
  try {
    const res = await auditoriumService.normalizeEmptyAuditoriumsLayout()
    normalizeResult.value = res
    normalizeResultModalOpen.value = true
    toast.success(`Đã chuẩn hóa thành công ${res.updatedCount} phòng chiếu trống!`)
    if (selectedCinemaForAuditoriums.value) {
      const detail = await cinemaService.getAdminCinemaDetail(selectedCinemaForAuditoriums.value.id)
      selectedCinemaForAuditoriums.value = detail
      cinemaAuditoriums.value = detail.auditoriums || []
    }
  } catch (err: any) {
    toast.error(err.response?.data?.message || 'Chuẩn hóa phòng chiếu thất bại.')
  } finally {
    isNormalizingLayouts.value = false
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
            <option v-for="c in cities" :key="c.value" :value="c.value">
              {{ locale === 'en' ? c.labelEn : c.labelVi }}
            </option>
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
              <p class="text-xs text-indigo-400 font-medium mt-0.5">{{ getCityLabel(c.city, locale) }}</p>
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
              <option v-for="city in cities" :key="city.value" :value="city.value">
                {{ locale === 'en' ? city.labelEn : city.labelVi }}
              </option>
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
          <label class="text-xs text-slate-400 font-medium block mb-1">Địa chỉ *</label>
          <Input v-model="cinemaForm.address" placeholder="Tầng 5, Vincom Center, 191 Bà Triệu" required />
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Giờ mở cửa</label>
            <Input v-model="cinemaForm.openingTime" type="time" placeholder="08:00" />
          </div>
          <div>
            <label class="text-xs text-slate-400 font-medium block mb-1">Giờ đóng cửa</label>
            <Input v-model="cinemaForm.closingTime" type="time" placeholder="23:30" />
          </div>
        </div>

        <div class="pt-4 border-t border-slate-800 flex justify-end space-x-3">
          <Button variant="secondary" type="button" @click="isCinemaModalOpen = false">
            Hủy
          </Button>
          <Button variant="primary" type="submit" :loading="isSavingCinema">
            {{ isEditing ? 'Lưu thay đổi' : 'Tạo cụm rạp' }}
          </Button>
        </div>
      </form>
    </Modal>

    <!-- Manage Auditoriums Modal -->
    <Modal
      v-model="isAuditoriumsModalOpen"
      :title="`Quản Lý Phòng Chiếu — ${selectedCinemaForAuditoriums?.name || ''}`"
      size="xl"
    >
      <div class="space-y-6">
        <div class="flex items-center justify-between pb-3 border-b border-slate-800">
          <div>
            <h4 class="text-base font-bold text-white">{{ selectedCinemaForAuditoriums?.name }}</h4>
            <p class="text-xs text-slate-400 mt-0.5">
              {{ selectedCinemaForAuditoriums?.address }} — {{ getCityLabel(selectedCinemaForAuditoriums?.city, locale) }}
            </p>
          </div>
          <div class="flex items-center gap-2">
            <Button
              variant="secondary"
              size="sm"
              :loading="isNormalizingLayouts"
              @click="handleNormalizeEmptyLayouts"
            >
              🪄 Chuẩn hóa phòng trống
            </Button>
            <Badge variant="primary" size="sm">
              {{ cinemaAuditoriums.length }} phòng chiếu
            </Badge>
          </div>
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
        <!-- Banner bảo vệ hoặc chỉ dẫn -->
        <div
          v-if="currentAuditoriumDetail"
          :class="[
            'p-3 rounded-lg text-xs flex items-start justify-between gap-3 border',
            !currentAuditoriumDetail.canModifySeatTypes
              ? 'bg-amber-950/40 border-amber-800/80 text-amber-200'
              : 'bg-emerald-950/40 border-emerald-800/80 text-emerald-200'
          ]"
        >
          <div class="flex items-start gap-2.5">
            <span class="text-base leading-none">
              {{ !currentAuditoriumDetail.canModifySeatTypes ? '🔒' : 'ℹ️' }}
            </span>
            <div>
              <div class="font-semibold">
                {{ !currentAuditoriumDetail.canModifySeatTypes ? 'Phòng chiếu đã phát sinh giao dịch' : 'Phòng chưa phát sinh giao dịch' }}
              </div>
              <div class="mt-0.5 opacity-90 leading-relaxed">
                {{
                  !currentAuditoriumDetail.canModifySeatTypes
                    ? 'Phòng chiếu đã phát sinh giao dịch. Cấu trúc và loại ghế được bảo vệ để đảm bảo lịch sử đặt vé. Quản trị viên chỉ có thể cập nhật trạng thái ghế khi không ảnh hưởng đến suất chiếu đang hoạt động.'
                    : 'Phòng chưa phát sinh giao dịch. Có thể thiết lập và điều chỉnh sơ đồ ghế.'
                }}
              </div>
            </div>
          </div>
          <div v-if="currentAuditoriumDetail.canModifyLayout" class="shrink-0">
            <Button
              variant="secondary"
              size="sm"
              :loading="isResettingLayout"
              @click="handleResetAuditoriumLayout"
            >
              Thiết lập lại sơ đồ chuẩn
            </Button>
          </div>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-slate-800 text-xs">
          <div class="flex flex-wrap items-center gap-4">
            <span
              v-for="st in uniqueSeatTypesInAuditorium"
              :key="st.code || st.name"
              class="flex items-center gap-1.5 text-slate-300"
            >
              <span
                :class="[
                  'h-3 rounded border',
                  (st.capacity || 1) > 1 ? 'w-5' : 'w-3',
                  getSeatLegendClass(st.colorToken)
                ]"
              ></span>
              {{ st.name }}
            </span>
            <span class="flex items-center gap-1.5 text-slate-300">
              <span class="w-3 h-3 rounded bg-rose-950 border border-rose-600"></span> Hỏng / Bảo trì
            </span>
          </div>

          <div class="flex items-center gap-2">
            <button
              type="button"
              :class="[
                'px-2.5 py-1 rounded-md text-xs font-semibold border transition-all',
                isMultiSelectMode || selectedSeatIds.length > 0
                  ? 'bg-indigo-600 border-indigo-500 text-white'
                  : 'bg-slate-900 border-slate-700 text-slate-300 hover:border-slate-600'
              ]"
              @click="isMultiSelectMode = !isMultiSelectMode"
            >
              {{ isMultiSelectMode || selectedSeatIds.length > 0 ? '✓ Đang chọn nhiều' : 'Chế độ chọn nhiều' }}
            </button>
            <button
              v-if="isMultiSelectMode || selectedSeatIds.length > 0"
              type="button"
              class="px-2 py-1 rounded-md text-xs bg-slate-900 border border-slate-700 text-slate-300 hover:text-white"
              @click="selectAllSeats"
            >
              Chọn tất cả
            </button>
            <button
              v-if="selectedSeatIds.length > 0"
              type="button"
              class="px-2 py-1 rounded-md text-xs bg-slate-900 border border-slate-700 text-slate-300 hover:text-rose-300"
              @click="clearSeatSelection"
            >
              Bỏ chọn ({{ selectedSeatIds.length }})
            </button>
          </div>
        </div>

        <!-- Sticky Bulk Actions Toolbar -->
        <div
          v-if="selectedSeatIds.length > 0"
          class="bg-indigo-950/80 border border-indigo-700/80 rounded-xl p-3 flex flex-wrap items-center justify-between gap-3 shadow-lg shadow-black/40 animate-fade-in"
        >
          <div class="flex items-center gap-3">
            <span class="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-bold bg-indigo-600 text-white shadow-sm">
              Đã chọn {{ selectedSeatIds.length }} ghế
            </span>
            <button
              type="button"
              class="text-xs text-indigo-300 hover:text-white underline"
              @click="clearSeatSelection"
            >
              Bỏ chọn
            </button>
          </div>

          <div class="flex flex-wrap items-center gap-3">
            <!-- Đổi loại ghế hàng loạt -->
            <div class="flex items-center gap-1.5">
              <select
                v-model="selectedBulkSeatTypeId"
                :disabled="!currentAuditoriumDetail?.canModifySeatTypes || isApplyingBatchSeatType"
                class="bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1 text-xs text-white focus:ring-1 focus:ring-indigo-500 focus:outline-none disabled:opacity-50"
              >
                <option v-for="st in availableSeatTypes" :key="st.id" :value="st.id">
                  {{ st.name }} ({{ st.code }})
                </option>
              </select>
              <Button
                variant="primary"
                size="sm"
                :disabled="!currentAuditoriumDetail?.canModifySeatTypes || !selectedBulkSeatTypeId"
                :loading="isApplyingBatchSeatType"
                @click="handleBulkApplySeatType"
              >
                Đổi loại ghế
              </Button>
            </div>

            <!-- Đổi trạng thái hàng loạt -->
            <div class="flex items-center gap-1.5 border-l border-slate-700 pl-3">
              <select
                v-model="selectedBulkStatus"
                :disabled="isApplyingBatchStatus"
                class="bg-slate-900 border border-slate-700 rounded-lg px-2.5 py-1 text-xs text-white focus:ring-1 focus:ring-indigo-500 focus:outline-none"
              >
                <option value="ACTIVE">Hoạt động (ACTIVE)</option>
                <option value="BROKEN">Hỏng / Bảo trì (BROKEN)</option>
              </select>
              <Button
                variant="secondary"
                size="sm"
                :loading="isApplyingBatchStatus"
                @click="handleBulkApplyStatus"
              >
                Đổi trạng thái
              </Button>
            </div>
          </div>
        </div>

        <div v-if="isLoadingSeats" class="py-12 text-center text-slate-400">
          <div class="inline-flex items-center gap-2">
            <div class="w-5 h-5 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin"></div>
            <span>Đang tải sơ đồ ghế...</span>
          </div>
        </div>

        <div v-else class="overflow-x-auto p-4 bg-slate-950 rounded-xl border border-slate-800 max-h-[55vh] scrollbar-thin scrollbar-thumb-slate-700">
          <div class="w-max mx-auto flex flex-col items-center gap-3">
            <div class="w-full max-w-xl py-1 text-center text-xs font-bold text-slate-400 bg-slate-850 rounded-lg border border-slate-700 tracking-widest uppercase">
              MÀN HÌNH CHIẾU
            </div>

            <!-- Column Header -->
            <div class="flex items-center gap-2">
              <span class="w-6 shrink-0" aria-hidden="true"></span>
              <div
                class="grid gap-1.5"
                :style="{
                  gridTemplateColumns: `repeat(${adminSeatGrid.columnsCount}, minmax(28px, 32px))`,
                }"
              >
                <div
                  v-for="col in adminSeatGrid.columnNumbers"
                  :key="col"
                  class="h-6 flex items-center justify-center text-[10px] font-bold text-slate-500 select-none"
                >
                  {{ col }}
                </div>
              </div>
              <span class="w-6 shrink-0" aria-hidden="true"></span>
            </div>

            <!-- Rows -->
            <div
              v-for="row in adminSeatGrid.rows"
              :key="row.rowLabel"
              class="flex items-center gap-2"
            >
              <button
                type="button"
                :class="[
                  'w-6 h-6 text-center text-xs font-bold shrink-0 rounded transition-colors flex items-center justify-center',
                  isRowSelected(row.rowLabel)
                    ? 'bg-indigo-600 text-white ring-1 ring-indigo-400'
                    : 'text-slate-400 hover:text-indigo-200 hover:bg-slate-800'
                ]"
                :title="`Click để chọn/bỏ chọn cả hàng ${row.rowLabel}`"
                @click="toggleRowSelection(row.rowLabel)"
              >
                {{ row.rowLabel }}
              </button>
              <div
                class="grid gap-1.5"
                :style="{
                  gridTemplateColumns: `repeat(${adminSeatGrid.columnsCount}, minmax(28px, 32px))`,
                }"
              >
                <template v-for="cell in row.cells" :key="cell.column">
                  <button
                    v-if="cell.type === 'seat' && cell.seat"
                    :style="{ gridColumn: `span ${cell.span}` }"
                    type="button"
                    :class="[
                      'h-7 px-1 rounded text-[10px] font-bold transition-all flex items-center justify-center border relative',
                      selectedSeatIds.includes(cell.seat.id)
                        ? 'ring-2 ring-indigo-400 ring-offset-1 ring-offset-slate-950 scale-105 shadow-md shadow-indigo-500/50 z-10'
                        : '',
                      cell.seat.status === 'BROKEN'
                        ? 'bg-rose-950/80 border-rose-600 text-rose-300 hover:bg-rose-900/80'
                        : getAdminSeatClass(cell.seat.colorToken)
                    ]"
                    :title="`Ghế ${cell.seat.seatCode} (${cell.seat.seatTypeName}) - ${cell.seat.status}`"
                    @click="handleSeatClick(cell.seat)"
                  >
                    {{ cell.seat.seatNumber }}
                    <span
                      v-if="selectedSeatIds.includes(cell.seat.id)"
                      class="absolute -top-1 -right-1 w-2 h-2 bg-indigo-400 rounded-full border border-white"
                      aria-hidden="true"
                    ></span>
                  </button>
                  <div
                    v-else
                    :style="{ gridColumn: `span ${cell.span}` }"
                    class="h-7 rounded border border-dashed border-slate-800/80 bg-slate-900/20 pointer-events-none"
                    aria-hidden="true"
                  />
                </template>
              </div>
              <button
                type="button"
                :class="[
                  'w-6 h-6 text-center text-xs font-bold shrink-0 rounded transition-colors flex items-center justify-center',
                  isRowSelected(row.rowLabel)
                    ? 'bg-indigo-600 text-white ring-1 ring-indigo-400'
                    : 'text-slate-400 hover:text-indigo-200 hover:bg-slate-800'
                ]"
                :title="`Click để chọn/bỏ chọn cả hàng ${row.rowLabel}`"
                @click="toggleRowSelection(row.rowLabel)"
              >
                {{ row.rowLabel }}
              </button>
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

    <!-- Seat Edit Modal -->
    <Modal
      v-model="isSeatEditModalOpen"
      :title="`Chỉnh Sửa Ghế ${editingSeat?.seatCode || ''}`"
      size="md"
    >
      <div v-if="editingSeat" class="space-y-4">
        <!-- Thông tin ghế -->
        <div class="grid grid-cols-3 gap-3 p-3 bg-slate-900 rounded-lg border border-slate-800 text-xs">
          <div>
            <span class="text-slate-400 block">Vị trí:</span>
            <span class="font-bold text-white text-sm">Hàng {{ editingSeat.rowLabel }}, Ghế {{ editingSeat.seatNumber }}</span>
          </div>
          <div>
            <span class="text-slate-400 block">Mã ghế:</span>
            <span class="font-bold text-indigo-400 text-sm">{{ editingSeat.seatCode }}</span>
          </div>
          <div>
            <span class="text-slate-400 block">Sức chứa:</span>
            <span class="font-bold text-white text-sm">{{ editingSeat.capacity || 1 }} người</span>
          </div>
        </div>

        <ErrorAlert v-if="seatEditError" :message="seatEditError" />

        <!-- Chọn loại ghế -->
        <div>
          <label class="text-xs font-semibold text-slate-300 block mb-1.5">
            Loại ghế:
          </label>
          <div v-if="!currentAuditoriumDetail?.canModifySeatTypes" class="p-2.5 bg-amber-950/30 border border-amber-800/60 rounded-lg text-xs text-amber-300">
            🔒 <strong>Không thể thay đổi loại ghế:</strong> Phòng chiếu đã phát sinh giao dịch đặt vé nên loại ghế được khóa bảo vệ.
          </div>
          <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-2">
            <label
              v-for="st in availableSeatTypes"
              :key="st.id"
              :class="[
                'flex items-center gap-2 p-2.5 rounded-lg border cursor-pointer transition-all text-xs',
                editSeatTypeId === st.id
                  ? 'border-indigo-500 bg-indigo-950/40 text-white'
                  : 'border-slate-800 bg-slate-900/60 text-slate-300 hover:border-slate-700'
              ]"
            >
              <input
                type="radio"
                name="seatType"
                :value="st.id"
                v-model="editSeatTypeId"
                class="text-indigo-600 focus:ring-indigo-500"
              />
              <span
                :class="[
                  'h-3 rounded border shrink-0',
                  (st.capacity || 1) > 1 ? 'w-5' : 'w-3',
                  getSeatLegendClass(st.colorToken)
                ]"
              ></span>
              <div class="flex-1 min-w-0">
                <div class="font-medium truncate">{{ st.name }}</div>
                <div class="text-[10px] text-slate-400">
                  Phụ thu: {{ st.priceModifier ? Number(st.priceModifier).toLocaleString('vi-VN') + ' đ' : '0 đ' }}
                </div>
              </div>
            </label>
          </div>
        </div>

        <!-- Chọn trạng thái ghế -->
        <div>
          <label class="text-xs font-semibold text-slate-300 block mb-1.5">
            Trạng thái ghế:
          </label>
          <div class="grid grid-cols-2 gap-2">
            <label
              :class="[
                'flex items-center gap-2 p-2.5 rounded-lg border cursor-pointer transition-all text-xs',
                editSeatStatus === 'ACTIVE'
                  ? 'border-emerald-500 bg-emerald-950/40 text-white'
                  : 'border-slate-800 bg-slate-900/60 text-slate-300 hover:border-slate-700'
              ]"
            >
              <input
                type="radio"
                name="seatStatus"
                value="ACTIVE"
                v-model="editSeatStatus"
                class="text-emerald-600 focus:ring-emerald-500"
              />
              <span class="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>
              <span class="font-medium">Hoạt động (ACTIVE)</span>
            </label>

            <label
              :class="[
                'flex items-center gap-2 p-2.5 rounded-lg border cursor-pointer transition-all text-xs',
                editSeatStatus === 'BROKEN'
                  ? 'border-rose-500 bg-rose-950/40 text-white'
                  : 'border-slate-800 bg-slate-900/60 text-slate-300 hover:border-slate-700'
              ]"
            >
              <input
                type="radio"
                name="seatStatus"
                value="BROKEN"
                v-model="editSeatStatus"
                class="text-rose-600 focus:ring-rose-500"
              />
              <span class="w-2.5 h-2.5 rounded-full bg-rose-500"></span>
              <span class="font-medium">Hỏng / Bảo trì (BROKEN)</span>
            </label>
          </div>
          <p class="text-[11px] text-slate-400 mt-1">
            * Lưu ý: Không thể đánh dấu hỏng nếu ghế đang được giữ chỗ hoặc đã có vé cho suất chiếu sắp tới.
          </p>
        </div>
      </div>

      <template #footer>
        <Button variant="secondary" size="md" :disabled="isSavingSeatEdit" @click="isSeatEditModalOpen = false">
          Hủy bỏ
        </Button>
        <Button variant="primary" size="md" :loading="isSavingSeatEdit" @click="handleSaveSeatEdit">
          Lưu thay đổi
        </Button>
      </template>
    </Modal>

    <!-- Couple Conversion Guard Confirmation Modal -->
    <Modal
      v-model="isCoupleConfirmModalOpen"
      title="Xác Nhận Thiết Lập Ghế Đôi (Couple)"
      size="md"
    >
      <div v-if="couplePreviewData" class="space-y-4 text-xs">
        <div class="p-3 bg-indigo-950/40 border border-indigo-800/80 rounded-lg text-indigo-200 leading-relaxed">
          <div class="font-bold text-white text-sm mb-1">
            Chuyển đổi {{ couplePreviewData.seatCount }} ghế thành {{ couplePreviewData.targetSeatTypeName }}
          </div>
          <div>
            Ghế đôi có sức chứa <strong>2 người</strong> và chiếm <strong>2 cột</strong> liền kề. Để đảm bảo sơ đồ phòng chiếu chính xác, hệ thống sẽ <strong>tự động xóa các ghế liền kề bị chiếm chỗ</strong>.
          </div>
        </div>

        <div v-if="couplePreviewData.willDeleteSeatCodes && couplePreviewData.willDeleteSeatCodes.length > 0" class="p-3 bg-rose-950/30 border border-rose-800/60 rounded-lg">
          <div class="font-semibold text-rose-300 mb-1.5 flex items-center gap-1.5">
            <span>⚠️</span>
            <span>Các ghế liền kề sẽ tự động bị xóa ({{ couplePreviewData.willDeleteSeatCodes.length }} ghế):</span>
          </div>
          <div class="flex flex-wrap gap-1.5 max-h-32 overflow-y-auto p-1 bg-slate-950/60 rounded border border-rose-950">
            <span
              v-for="code in couplePreviewData.willDeleteSeatCodes"
              :key="code"
              class="px-2 py-0.5 rounded bg-rose-900/60 text-rose-200 border border-rose-700 font-mono font-bold text-[11px]"
            >
              {{ code }}
            </span>
          </div>
        </div>

        <div class="text-slate-400 text-[11px] italic">
          * Thao tác này chỉ thực hiện được khi phòng chiếu chưa phát sinh giao dịch đặt vé hoặc vé.
        </div>
      </div>

      <template #footer>
        <Button
          variant="secondary"
          size="md"
          :disabled="isApplyingBatchSeatType"
          @click="isCoupleConfirmModalOpen = false"
        >
          Hủy bỏ
        </Button>
        <Button
          variant="primary"
          size="md"
          :loading="isApplyingBatchSeatType"
          @click="executeBatchUpdateSeatType"
        >
          Xác nhận & Cập nhật
        </Button>
      </template>
    </Modal>

    <!-- Normalize Result Modal -->
    <Modal
      v-model="normalizeResultModalOpen"
      title="Kết Quả Chuẩn Hóa Phòng Chiếu"
      size="md"
    >
      <div v-if="normalizeResult" class="space-y-3 text-xs">
        <div class="grid grid-cols-4 gap-2 p-3 bg-slate-900 rounded-lg text-center">
          <div>
            <div class="text-slate-400">Đã quét</div>
            <div class="text-base font-bold text-white">{{ normalizeResult.scannedCount ?? normalizeResult.processedCount }}</div>
          </div>
          <div>
            <div class="text-slate-400">Đã chuẩn hóa</div>
            <div class="text-base font-bold text-emerald-400">{{ normalizeResult.normalizedCount ?? normalizeResult.updatedCount }}</div>
          </div>
          <div>
            <div class="text-slate-400">Không đổi</div>
            <div class="text-base font-bold text-indigo-400">{{ normalizeResult.unchangedCount ?? 0 }}</div>
          </div>
          <div>
            <div class="text-slate-400">Bỏ qua (bảo vệ)</div>
            <div class="text-base font-bold text-amber-400">{{ normalizeResult.skippedCount }}</div>
          </div>
        </div>

        <!-- Chi tiết phân loại lý do bỏ qua -->
        <div
          v-if="(normalizeResult.skippedBecauseBookings || 0) > 0 || (normalizeResult.skippedBecauseTickets || 0) > 0 || (normalizeResult.skippedBecauseActiveHolds || 0) > 0 || (normalizeResult.failedCount || 0) > 0"
          class="grid grid-cols-2 sm:grid-cols-4 gap-2 text-[11px] p-2.5 bg-slate-900/60 rounded border border-slate-800"
        >
          <div class="p-1 text-center bg-slate-950/50 rounded">
            <span class="text-slate-400 block">Đã có vé:</span>
            <span class="font-bold text-amber-300">{{ normalizeResult.skippedBecauseTickets ?? 0 }}</span>
          </div>
          <div class="p-1 text-center bg-slate-950/50 rounded">
            <span class="text-slate-400 block">Đã có booking:</span>
            <span class="font-bold text-amber-300">{{ normalizeResult.skippedBecauseBookings ?? 0 }}</span>
          </div>
          <div class="p-1 text-center bg-slate-950/50 rounded">
            <span class="text-slate-400 block">Đang giữ chỗ:</span>
            <span class="font-bold text-amber-300">{{ normalizeResult.skippedBecauseActiveHolds ?? 0 }}</span>
          </div>
          <div class="p-1 text-center bg-slate-950/50 rounded">
            <span class="text-slate-400 block">Lỗi:</span>
            <span class="font-bold text-rose-400">{{ normalizeResult.failedCount ?? 0 }}</span>
          </div>
        </div>

        <div v-if="normalizeResult.skippedDetails && normalizeResult.skippedDetails.length > 0" class="mt-2">
          <div class="font-semibold text-slate-300 mb-1.5">Chi tiết các phòng được bảo vệ (bỏ qua):</div>
          <div class="max-h-48 overflow-y-auto space-y-1.5 p-2 bg-slate-950 rounded border border-slate-800 scrollbar-thin scrollbar-thumb-slate-700">
            <div
              v-for="item in normalizeResult.skippedDetails"
              :key="item.auditoriumId"
              class="p-1.5 bg-slate-900/70 rounded border border-slate-800 text-[11px]"
            >
              <span class="font-medium text-white">{{ item.cinemaName }} — {{ item.auditoriumName }}</span>:
              <span class="text-amber-300 ml-1">{{ item.reason }}</span>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <Button variant="secondary" size="md" @click="normalizeResultModalOpen = false">
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
