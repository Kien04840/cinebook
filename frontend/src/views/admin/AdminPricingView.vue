<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { SeatTypeResponse, CreateSeatTypeRequest, UpdateSeatTypeRequest } from '@/types/seatType.types'
import seatTypeService from '@/services/seatType.service'
import { formatCurrency, formatDate, formatStatus } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import { useToast } from '@/composables/useToast'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Badge from '@/components/common/Badge.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const toast = useToast()
const { t } = useI18n()

const seatTypes = ref<SeatTypeResponse[]>([])
const isLoading = ref<boolean>(true)
const errorMessage = ref<string>('')

// Modal state
const isModalOpen = ref<boolean>(false)
const isEditing = ref<boolean>(false)
const editingId = ref<string>('')
const isSaving = ref<boolean>(false)
const formError = ref<string>('')

const formData = ref<{
  name: string
  priceModifier: number
  description: string
}>({
  name: '',
  priceModifier: 0,
  description: '',
})

async function fetchSeatTypes() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const res = await seatTypeService.getAdminSeatTypes({ page: 0, size: 50 })
    seatTypes.value = res.content || []
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || 'Không thể tải danh sách loại ghế.'
  } finally {
    isLoading.value = false
  }
}

function openCreateModal() {
  isEditing.value = false
  editingId.value = ''
  formError.value = ''
  formData.value = {
    name: '',
    priceModifier: 0,
    description: '',
  }
  isModalOpen.value = true
}

function openEditModal(st: SeatTypeResponse) {
  isEditing.value = true
  editingId.value = st.id
  formError.value = ''
  formData.value = {
    name: st.name,
    priceModifier: st.priceModifier,
    description: st.description || '',
  }
  isModalOpen.value = true
}

async function handleSaveSeatType() {
  formError.value = ''

  if (!formData.value.name.trim()) {
    formError.value = 'Vui lòng nhập tên loại ghế.'
    return
  }

  if (formData.value.priceModifier < 0) {
    formError.value = 'Phụ thu không được âm.'
    return
  }

  isSaving.value = true
  try {
    if (isEditing.value) {
      const updatePayload: UpdateSeatTypeRequest = {
        name: formData.value.name.trim(),
        priceModifier: formData.value.priceModifier,
        description: formData.value.description.trim() || undefined,
      }
      await seatTypeService.updateSeatType(editingId.value, updatePayload)
      toast.success('Cập nhật loại ghế thành công!')
    } else {
      const createPayload: CreateSeatTypeRequest = {
        name: formData.value.name.trim(),
        priceModifier: formData.value.priceModifier,
        description: formData.value.description.trim() || undefined,
      }
      await seatTypeService.createSeatType(createPayload)
      toast.success('Tạo loại ghế mới thành công!')
    }

    isModalOpen.value = false
    await fetchSeatTypes()
  } catch (err: any) {
    formError.value = err.response?.data?.message || 'Không thể lưu loại ghế.'
  } finally {
    isSaving.value = false
  }
}

onMounted(() => {
  fetchSeatTypes()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Top Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-white tracking-tight flex items-center gap-2">
          💺 {{ t('adminPricing.title') }}
        </h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ t('adminPricing.subtitle') }}
        </p>
      </div>

      <Button variant="primary" size="md" class="shadow-lg shadow-indigo-600/30" @click="openCreateModal">
        <template #prefix>
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
        </template>
        {{ t('adminPricing.createBtn') }}
      </Button>
    </div>

    <!-- Error Alert -->
    <ErrorAlert v-if="errorMessage" :message="errorMessage" @retry="fetchSeatTypes" />

    <!-- Table Card -->
    <Card v-else padding="none">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-xs">
          <thead>
            <tr class="bg-slate-850 border-b border-slate-700/80 text-[11px] font-bold uppercase text-slate-400 tracking-wider">
              <th class="px-4 py-3.5">{{ t('adminPricing.colName') }}</th>
              <th class="px-4 py-3.5">{{ t('adminPricing.colModifier') }}</th>
              <th class="px-4 py-3.5">{{ t('adminPricing.colDescription') }}</th>
              <th class="px-4 py-3.5">{{ t('adminPricing.colStatus') }}</th>
              <th class="px-4 py-3.5">{{ t('adminPricing.colUpdatedAt') }}</th>
              <th class="px-4 py-3.5 text-right">{{ t('adminPricing.colActions') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-800">
            <!-- Loading Skeleton -->
            <tr v-if="isLoading" v-for="n in 4" :key="n" class="animate-pulse">
              <td colspan="6" class="px-4 py-4"><div class="h-5 rounded bg-slate-800"></div></td>
            </tr>

            <!-- Empty State -->
            <tr v-else-if="seatTypes.length === 0">
              <td colspan="6" class="px-4 py-12 text-center text-slate-500">
                Chưa có dữ liệu loại ghế.
              </td>
            </tr>

            <!-- Data Rows -->
            <tr v-else v-for="st in seatTypes" :key="st.id" class="hover:bg-slate-800/50 transition-colors">
              <td class="px-4 py-3.5 font-bold text-white">
                {{ st.name }}
              </td>

              <td class="px-4 py-3.5 font-black text-emerald-400">
                +{{ formatCurrency(st.priceModifier) }}
              </td>

              <td class="px-4 py-3.5 text-slate-300 max-w-[250px] truncate">
                {{ st.description || '---' }}
              </td>

              <td class="px-4 py-3.5">
                <Badge :variant="st.status === 'ACTIVE' ? 'success' : 'neutral'" size="sm">
                  {{ formatStatus(st.status) }}
                </Badge>
              </td>

              <td class="px-4 py-3.5 text-slate-400">
                {{ formatDate(st.updatedAt || st.createdAt) }}
              </td>

              <td class="px-4 py-3.5 text-right">
                <Button variant="ghost" size="sm" @click="openEditModal(st)">
                  {{ t('common.edit') }}
                </Button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </Card>

    <!-- Create/Edit Modal -->
    <Teleport to="body">
      <transition name="modal">
        <div
          v-if="isModalOpen"
          class="fixed inset-0 z-50 overflow-y-auto bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 sm:p-6"
          @click.self="isModalOpen = false"
        >
          <div class="relative w-full max-w-md rounded-3xl bg-slate-900 border border-slate-800 shadow-2xl p-6 sm:p-7 space-y-5 animate-scale">
            <div class="flex items-center justify-between border-b border-slate-800 pb-3">
              <h2 class="text-xl font-bold text-white tracking-tight">
                {{ isEditing ? 'Chỉnh Sửa Loại Ghế' : 'Tạo Loại Ghế Mới' }}
              </h2>
              <button
                type="button"
                class="w-8 h-8 rounded-full bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white flex items-center justify-center text-sm"
                @click="isModalOpen = false"
              >
                ✕
              </button>
            </div>

            <ErrorAlert v-if="formError" :message="formError" />

            <form class="space-y-4 text-sm" @submit.prevent="handleSaveSeatType">
              <div>
                <label for="seatTypeNameInput" class="block font-semibold text-slate-300 mb-1">
                  Tên loại ghế *
                </label>
                <input
                  id="seatTypeNameInput"
                  v-model="formData.name"
                  type="text"
                  class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="VD: VIP, Standard, Couple..."
                  required
                />
              </div>

              <div>
                <label for="seatTypeModifierInput" class="block font-semibold text-slate-300 mb-1">
                  Phụ thu / Giá tăng thêm (₫) *
                </label>
                <input
                  id="seatTypeModifierInput"
                  v-model.number="formData.priceModifier"
                  type="number"
                  step="1000"
                  min="0"
                  class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="VD: 15000"
                  required
                />
              </div>

              <div>
                <label for="seatTypeDescInput" class="block font-semibold text-slate-300 mb-1">
                  Mô tả / Ghi chú
                </label>
                <textarea
                  id="seatTypeDescInput"
                  v-model="formData.description"
                  rows="2"
                  class="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-700 text-white text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="Thông tin thêm về vị trí, tiện ích..."
                ></textarea>
              </div>

              <div class="flex items-center justify-end gap-3 pt-3 border-t border-slate-800">
                <Button variant="secondary" size="md" type="button" :disabled="isSaving" @click="isModalOpen = false">
                  Hủy bỏ
                </Button>
                <Button variant="primary" size="md" type="submit" :loading="isSaving" class="shadow-lg shadow-indigo-600/30">
                  {{ isEditing ? 'Lưu thay đổi' : 'Tạo mới' }}
                </Button>
              </div>
            </form>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.25s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.animate-scale {
  animation: scaleUp 0.25s ease-out;
}

@keyframes scaleUp {
  from {
    transform: scale(0.96);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}
</style>

