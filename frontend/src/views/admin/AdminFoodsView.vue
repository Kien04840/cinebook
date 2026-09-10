<script setup lang="ts">
/**
 * AdminFoodsView.vue — Quản lý Bắp nước & Concessions cho Quản trị viên (CineBook Admin)
 * 
 * Chức năng:
 * - Xem danh sách bắp nước, combo, đồ ăn vặt
 * - Lọc theo từ khóa tìm kiếm và trạng thái (ACTIVE / INACTIVE)
 * - Thêm mới món ăn / thức uống
 * - Chỉnh sửa thông tin & giá bán
 * - Xóa món ăn (Xóa mềm, kèm modal xác nhận)
 */
import { ref, computed, onMounted } from 'vue'
import type { FoodItemResponse, FoodItemStatus, CreateFoodItemRequest, UpdateFoodItemRequest } from '@/types/food.types'
import foodService from '@/services/food.service'
import { formatCurrency, formatStatus, getErrorMessage } from '@/utils/formatters'
import { useToast } from '@/composables/useToast'
import { useI18n } from '@/composables/useI18n'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Input from '@/components/common/Input.vue'
import Modal from '@/components/common/Modal.vue'
import Badge from '@/components/common/Badge.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const toast = useToast()
const { t } = useI18n()

// Dữ liệu món ăn
const foods = ref<FoodItemResponse[]>([])
const isLoading = ref(true)
const errorMessage = ref('')
const searchQuery = ref('')
const statusFilter = ref<string>('ALL') // 'ALL' | 'ACTIVE' | 'INACTIVE'

// Modal Thêm / Sửa
const isModalOpen = ref(false)
const isEditing = ref(false)
const editingFoodId = ref('')
const isSaving = ref(false)
const formError = ref('')

const formData = ref<{
  name: string
  price: number | ''
  imageUrl: string
  description: string
  status: FoodItemStatus
}>({
  name: '',
  price: '',
  imageUrl: '',
  description: '',
  status: 'ACTIVE',
})

// Modal Xác nhận xóa
const isDeleteModalOpen = ref(false)
const deletingFood = ref<FoodItemResponse | null>(null)
const isDeleting = ref(false)

/**
 * Danh sách món ăn sau khi lọc theo từ khóa và trạng thái
 */
const filteredFoods = computed(() => {
  let result = foods.value
  const query = searchQuery.value.trim().toLowerCase()
  if (query) {
    result = result.filter(
      (f) =>
        f.name.toLowerCase().includes(query) ||
        (f.description && f.description.toLowerCase().includes(query))
    )
  }
  if (statusFilter.value !== 'ALL') {
    result = result.filter((f) => f.status === statusFilter.value)
  }
  return result
})

/**
 * Tải danh sách món ăn từ Backend Admin API
 */
async function fetchFoods() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const data = await foodService.getAdminFoods()
    foods.value = data || []
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || 'Không thể tải danh sách bắp nước.'
  } finally {
    isLoading.value = false
  }
}

/**
 * Mở modal tạo mới
 */
function openCreateModal() {
  isEditing.value = false
  editingFoodId.value = ''
  formError.value = ''
  formData.value = {
    name: '',
    price: '',
    imageUrl: '',
    description: '',
    status: 'ACTIVE',
  }
  isModalOpen.value = true
}

/**
 * Mở modal chỉnh sửa
 */
function openEditModal(food: FoodItemResponse) {
  isEditing.value = true
  editingFoodId.value = food.id
  formError.value = ''
  formData.value = {
    name: food.name,
    price: food.price,
    imageUrl: food.imageUrl || '',
    description: food.description || '',
    status: food.status,
  }
  isModalOpen.value = true
}

/**
 * Validate form phía client
 */
function validateForm(): boolean {
  formError.value = ''
  const trimmedName = formData.value.name.trim()

  if (!trimmedName) {
    formError.value = 'Tên món không được để trống.'
    return false
  }

  if (trimmedName.length > 100) {
    formError.value = 'Tên món không được vượt quá 100 ký tự.'
    return false
  }

  if (formData.value.price === '' || isNaN(Number(formData.value.price))) {
    formError.value = 'Giá bán không hợp lệ.'
    return false
  }

  if (Number(formData.value.price) < 0) {
    formError.value = 'Giá bán phải lớn hơn hoặc bằng 0.'
    return false
  }

  if (formData.value.description && formData.value.description.length > 500) {
    formError.value = 'Mô tả không được vượt quá 500 ký tự.'
    return false
  }

  return true
}

/**
 * Lưu món ăn (Tạo mới hoặc Cập nhật)
 */
async function handleSaveFood() {
  if (!validateForm()) return

  isSaving.value = true
  formError.value = ''

  try {
    if (isEditing.value) {
      const payload: UpdateFoodItemRequest = {
        name: formData.value.name.trim(),
        price: Number(formData.value.price),
        imageUrl: formData.value.imageUrl.trim() || undefined,
        description: formData.value.description.trim() || undefined,
        status: formData.value.status,
      }
      await foodService.updateFood(editingFoodId.value, payload)
      toast.success(t('adminFoods.updateSuccess'))
    } else {
      const payload: CreateFoodItemRequest = {
        name: formData.value.name.trim(),
        price: Number(formData.value.price),
        imageUrl: formData.value.imageUrl.trim() || undefined,
        description: formData.value.description.trim() || undefined,
        status: formData.value.status,
      }
      await foodService.createFood(payload)
      toast.success(t('adminFoods.createSuccess'))
    }

    isModalOpen.value = false
    await fetchFoods()
  } catch (err: any) {
    const status = err.response?.status
    if (status === 409) {
      formError.value = t('errors.CONFLICT') !== 'errors.CONFLICT' ? t('errors.CONFLICT') : 'Tên món ăn này đã tồn tại trong hệ thống. Vui lòng chọn tên khác.'
    } else {
      formError.value = getErrorMessage(err, 'Có lỗi xảy ra khi lưu món ăn.')
    }
  } finally {
    isSaving.value = false
  }
}

/**
 * Mở modal xác nhận xóa
 */
function openDeleteModal(food: FoodItemResponse) {
  deletingFood.value = food
  isDeleteModalOpen.value = true
}

/**
 * Thực hiện xóa món ăn
 */
async function handleDeleteFood() {
  if (!deletingFood.value) return

  isDeleting.value = true
  try {
    await foodService.deleteFood(deletingFood.value.id)
    toast.success(t('adminFoods.deleteSuccess'))
    isDeleteModalOpen.value = false
    deletingFood.value = null
    await fetchFoods()
  } catch (err: any) {
    const msg = getErrorMessage(err, 'Xóa món ăn thất bại. Vui lòng thử lại.')
    toast.error(msg, t('common.errorTitle'))
  } finally {
    isDeleting.value = false
  }
}

onMounted(() => {
  fetchFoods()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-white tracking-tight flex items-center gap-2">
          🍿 {{ t('adminFoods.title') }}
        </h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ t('adminFoods.subtitle') }}
        </p>
      </div>

      <div class="flex items-center gap-2.5">
        <Button variant="primary" size="md" @click="openCreateModal">
          <template #prefix>
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
          </template>
          {{ t('adminFoods.createBtn') }}
        </Button>
      </div>
    </div>

    <!-- Toolbar: Tìm kiếm và bộ lọc -->
    <Card padding="sm">
      <div class="flex flex-col sm:flex-row items-center justify-between gap-3">
        <div class="flex flex-col sm:flex-row items-center gap-3 w-full sm:w-auto">
          <!-- Search input -->
          <div class="relative w-full sm:w-80">
            <span class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-slate-400">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </span>
            <Input
              v-model="searchQuery"
              :placeholder="t('adminFoods.searchPlaceholder')"
              class="pl-9 w-full"
            />
          </div>

          <!-- Status filter -->
          <select
            v-model="statusFilter"
            class="px-3 py-2 rounded-xl bg-slate-900 border border-slate-700 text-white text-xs focus:outline-none focus:ring-2 focus:ring-indigo-500 w-full sm:w-auto"
          >
            <option value="ALL">{{ t('adminFoods.allStatuses') }}</option>
            <option value="ACTIVE">{{ t('adminFoods.activeStatus') }}</option>
            <option value="INACTIVE">{{ t('adminFoods.inactiveStatus') }}</option>
          </select>
        </div>

        <div class="text-xs text-slate-400 self-end sm:self-center">
          <span>{{ t('adminFoods.totalCount', { count: filteredFoods.length }) }}</span>
        </div>
      </div>
    </Card>

    <!-- Error Alert nếu tải thất bại -->
    <ErrorAlert v-if="errorMessage" :message="errorMessage" class="my-2" />

    <!-- Table danh sách món ăn -->
    <Card padding="none">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-sm">
          <thead>
            <tr class="bg-slate-850 border-b border-slate-700/80 text-xs font-semibold uppercase text-slate-400 tracking-wider">
              <th class="px-5 py-3.5 w-16">{{ t('adminFoods.colImage') }}</th>
              <th class="px-5 py-3.5 w-1/4">{{ t('adminFoods.colName') }}</th>
              <th class="px-5 py-3.5 w-32">{{ t('adminFoods.colPrice') }}</th>
              <th class="px-5 py-3.5 w-1/3">{{ t('adminFoods.colDescription') }}</th>
              <th class="px-5 py-3.5 w-28">{{ t('adminFoods.colStatus') }}</th>
              <th class="px-5 py-3.5 text-right w-28">{{ t('adminFoods.colActions') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-700/60">
            <!-- Loading State -->
            <template v-if="isLoading">
              <tr v-for="i in 5" :key="i" class="animate-pulse">
                <td class="px-5 py-4">
                  <div class="w-12 h-12 bg-slate-800 rounded-lg"></div>
                </td>
                <td class="px-5 py-4">
                  <div class="h-4 bg-slate-800 rounded w-32"></div>
                </td>
                <td class="px-5 py-4">
                  <div class="h-4 bg-slate-800 rounded w-20"></div>
                </td>
                <td class="px-5 py-4">
                  <div class="h-4 bg-slate-800 rounded w-48"></div>
                </td>
                <td class="px-5 py-4">
                  <div class="h-4 bg-slate-800 rounded w-16"></div>
                </td>
                <td class="px-5 py-4 text-right">
                  <div class="h-4 bg-slate-800 rounded w-16 ml-auto"></div>
                </td>
              </tr>
            </template>

            <!-- Empty State -->
            <tr v-else-if="filteredFoods.length === 0">
              <td colspan="6" class="px-5 py-12 text-center text-slate-400">
                <div class="flex flex-col items-center justify-center gap-2">
                  <span class="text-3xl">🍿</span>
                  <p class="font-medium text-slate-300">{{ t('adminFoods.emptyTitle') }}</p>
                  <p class="text-xs text-slate-500">
                    {{ searchQuery ? t('adminFoods.emptyDescSearch') : t('adminFoods.emptyDescCreate') }}
                  </p>
                </div>
              </td>
            </tr>

            <!-- Data Rows -->
            <tr
              v-for="food in filteredFoods"
              v-else
              :key="food.id"
              class="hover:bg-slate-800/40 transition-colors"
            >
              <!-- Image Thumbnail -->
              <td class="px-5 py-3.5">
                <div class="w-12 h-12 rounded-xl bg-slate-800 border border-slate-700 overflow-hidden flex items-center justify-center shrink-0">
                  <img
                    v-if="food.imageUrl"
                    :src="food.imageUrl"
                    :alt="food.name"
                    class="w-full h-full object-cover"
                    @error="($event.target as HTMLElement).style.display = 'none'"
                  />
                  <span v-else class="text-xl">🍿</span>
                </div>
              </td>

              <!-- Food Name -->
              <td class="px-5 py-3.5 font-medium text-white">
                <div class="font-semibold text-slate-100">{{ food.name }}</div>
              </td>

              <!-- Price -->
              <td class="px-5 py-3.5 font-mono font-bold text-emerald-400 text-sm whitespace-nowrap">
                {{ formatCurrency(food.price) }}
              </td>

              <!-- Description -->
              <td class="px-5 py-3.5 text-xs text-slate-400 max-w-xs truncate" :title="food.description">
                {{ food.description || '—' }}
              </td>

              <!-- Status -->
              <td class="px-5 py-3.5 whitespace-nowrap">
                <Badge :variant="food.status === 'ACTIVE' ? 'success' : 'neutral'" size="sm">
                  {{ formatStatus(food.status) }}
                </Badge>
              </td>

              <!-- Actions -->
              <td class="px-5 py-3.5 text-right whitespace-nowrap">
                <div class="flex items-center justify-end gap-1.5">
                  <button
                    type="button"
                    class="p-1.5 rounded-lg text-slate-400 hover:text-indigo-400 hover:bg-slate-800 transition-colors"
                    :title="t('common.edit')"
                    @click="openEditModal(food)"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                    </svg>
                  </button>
                  <button
                    type="button"
                    class="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-slate-800 transition-colors"
                    :title="t('common.delete')"
                    @click="openDeleteModal(food)"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </Card>

    <!-- Modal Thêm / Chỉnh sửa món ăn -->
    <Modal
      v-model="isModalOpen"
      :title="isEditing ? t('adminFoods.editModalTitle') : t('adminFoods.createModalTitle')"
      size="md"
      @close="isModalOpen = false"
    >
      <form class="space-y-4" @submit.prevent="handleSaveFood">
        <!-- Error Alert trong Modal -->
        <ErrorAlert v-if="formError" :message="formError" />

        <!-- Tên món -->
        <div>
          <label class="block text-xs font-semibold text-slate-300 mb-1.5">
            {{ t('adminFoods.nameLabel') }} <span class="text-rose-400">*</span>
          </label>
          <Input
            v-model="formData.name"
            placeholder="Ví dụ: Bắp rang bơ phô mai (L), Combo Couple..."
            maxlength="100"
            required
          />
        </div>

        <!-- Giá bán -->
        <div>
          <label class="block text-xs font-semibold text-slate-300 mb-1.5">
            {{ t('adminFoods.priceLabel') }} <span class="text-rose-400">*</span>
          </label>
          <Input
            v-model.number="formData.price"
            type="number"
            min="0"
            step="1000"
            placeholder="Ví dụ: 45000"
            required
          />
        </div>

        <!-- Image URL -->
        <div>
          <label class="block text-xs font-semibold text-slate-300 mb-1.5">
            {{ t('adminFoods.imageUrlLabel') }}
          </label>
          <Input
            v-model="formData.imageUrl"
            placeholder="https://example.com/popcorn.jpg"
          />
        </div>

        <!-- Trạng thái -->
        <div>
          <label class="block text-xs font-semibold text-slate-300 mb-1.5">
            {{ t('adminFoods.statusLabel') }}
          </label>
          <select
            v-model="formData.status"
            class="w-full px-3 py-2 rounded-xl bg-slate-900 border border-slate-700 text-white text-xs focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="ACTIVE">{{ t('adminFoods.activeStatus') }}</option>
            <option value="INACTIVE">{{ t('adminFoods.inactiveStatus') }}</option>
          </select>
        </div>

        <!-- Mô tả -->
        <div>
          <label class="block text-xs font-semibold text-slate-300 mb-1.5">
            {{ t('adminFoods.descLabel') }}
          </label>
          <textarea
            v-model="formData.description"
            rows="3"
            maxlength="500"
            placeholder="Ví dụ: 1 bắp rang vị phô mai cỡ lớn (L) kèm 2 ly nước ngọt tự chọn..."
            class="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-slate-700 text-white placeholder-slate-500 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none transition-colors"
          ></textarea>
        </div>

        <!-- Actions -->
        <div class="flex items-center justify-end gap-3 pt-4 border-t border-slate-700/80">
          <Button variant="secondary" size="md" type="button" @click="isModalOpen = false">
            {{ t('common.cancel') }}
          </Button>
          <Button variant="primary" size="md" type="submit" :loading="isSaving">
            {{ isEditing ? (t('adminPricing.saveChanges') !== 'adminPricing.saveChanges' ? t('adminPricing.saveChanges') : 'Cập nhật') : (t('common.save') !== 'common.save' ? t('common.save') : 'Lưu') }}
          </Button>
        </div>
      </form>
    </Modal>

    <!-- Modal Xác nhận xóa -->
    <Modal
      v-model="isDeleteModalOpen"
      :title="t('adminFoods.deleteModalTitle')"
      size="sm"
      @close="isDeleteModalOpen = false"
    >
      <div class="space-y-4">
        <div class="p-3 rounded-xl bg-rose-950/40 border border-rose-800/60 text-rose-200 text-xs leading-relaxed">
          {{ t('adminFoods.deleteConfirmDesc', { name: deletingFood?.name || '' }) }}
        </div>

        <div class="flex items-center justify-end gap-3 pt-3 border-t border-slate-700/80">
          <Button variant="secondary" size="md" :disabled="isDeleting" @click="isDeleteModalOpen = false">
            {{ t('common.cancel') }}
          </Button>
          <Button variant="danger" size="md" :loading="isDeleting" @click="handleDeleteFood">
            {{ t('common.confirm') }}
          </Button>
        </div>
      </div>
    </Modal>
  </div>
</template>
