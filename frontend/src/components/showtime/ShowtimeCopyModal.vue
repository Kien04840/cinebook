<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { CinemaSummaryResponse, AuditoriumResponse } from '@/types/cinema.types'
import type { CopyScheduleRequest, CopyScheduleResultResponse } from '@/types/showtime.types'
import showtimeService from '@/services/showtime.service'
import cinemaService from '@/services/cinema.service'
import { useI18n } from '@/composables/useI18n'
import { useToast } from '@/composables/useToast'
import Modal from '@/components/common/Modal.vue'
import Button from '@/components/common/Button.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

interface Props {
  modelValue: boolean
  cinemasList: CinemaSummaryResponse[]
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'success'): void
}>()

const { t } = useI18n()
const toast = useToast()

const sourceDate = ref('')
const targetDate = ref('')
const selectedCinemaId = ref('')
const selectedAuditoriumIds = ref<string[]>([])
const availableAuditoriums = ref<AuditoriumResponse[]>([])
const isLoadingAuditoriums = ref(false)

const isCopying = ref(false)
const errorMessage = ref('')
const resultData = ref<CopyScheduleResultResponse | null>(null)

const canSubmit = computed(() => {
  if (!sourceDate.value) return false
  if (!targetDate.value) return false
  if (sourceDate.value === targetDate.value) return false
  return true
})

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      resetForm()
    }
  }
)

function resetForm() {
  const today = new Date()
  const tomorrow = new Date()
  tomorrow.setDate(today.getDate() + 1)

  sourceDate.value = today.toISOString().slice(0, 10)
  targetDate.value = tomorrow.toISOString().slice(0, 10)
  selectedCinemaId.value = ''
  selectedAuditoriumIds.value = []
  availableAuditoriums.value = []
  errorMessage.value = ''
  resultData.value = null
}

async function handleCinemaChange(cinemaId: string) {
  selectedCinemaId.value = cinemaId
  selectedAuditoriumIds.value = []
  availableAuditoriums.value = []
  if (!cinemaId) return

  isLoadingAuditoriums.value = true
  try {
    const auds = await cinemaService.getAuditoriumsByCinema(cinemaId)
    availableAuditoriums.value = auds || []
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  } finally {
    isLoadingAuditoriums.value = false
  }
}

function toggleAuditorium(id: string) {
  const index = selectedAuditoriumIds.value.indexOf(id)
  if (index >= 0) {
    selectedAuditoriumIds.value.splice(index, 1)
  } else {
    selectedAuditoriumIds.value.push(id)
  }
}

function selectAllAuditoriums() {
  selectedAuditoriumIds.value = availableAuditoriums.value.map((a) => a.id)
}

function deselectAllAuditoriums() {
  selectedAuditoriumIds.value = []
}

async function handleCopy() {
  if (!canSubmit.value) return
  isCopying.value = true
  errorMessage.value = ''

  try {
    const payload: CopyScheduleRequest = {
      sourceDate: sourceDate.value,
      targetDate: targetDate.value,
      cinemaId: selectedCinemaId.value || undefined,
      auditoriumIds: selectedAuditoriumIds.value.length > 0 ? selectedAuditoriumIds.value : undefined,
    }

    const res = await showtimeService.copySchedule(payload)
    resultData.value = res
    emit('success')
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
    toast.error(errorMessage.value)
  } finally {
    isCopying.value = false
  }
}

function closeModal() {
  emit('update:modelValue', false)
}
</script>

<template>
  <Modal
    :model-value="modelValue"
    size="lg"
    :title="t('adminShowtimes.copyModal.title')"
    @close="closeModal"
  >
    <div class="space-y-4 text-sm text-slate-300">
      <p class="text-xs text-slate-400 leading-relaxed">
        {{ t('adminShowtimes.copyModal.subtitle') }}
      </p>

      <ErrorAlert v-if="errorMessage" :message="errorMessage" />

      <!-- Form Inputs (when no result yet) -->
      <div v-if="!resultData" class="space-y-4">
        <!-- Dates Row -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label class="block text-xs font-medium text-slate-300 mb-1">
              {{ t('adminShowtimes.copyModal.sourceDate') }} <span class="text-rose-400">*</span>
            </label>
            <input
              v-model="sourceDate"
              type="date"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div>
            <label class="block text-xs font-medium text-slate-300 mb-1">
              {{ t('adminShowtimes.copyModal.targetDate') }} <span class="text-rose-400">*</span>
            </label>
            <input
              v-model="targetDate"
              type="date"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
        </div>

        <p v-if="sourceDate && targetDate && sourceDate === targetDate" class="text-xs text-rose-400">
          ⚠️ Ngày đích phải khác ngày nguồn!
        </p>

        <!-- Cinema Filter -->
        <div>
          <label class="block text-xs font-medium text-slate-300 mb-1">
            {{ t('adminShowtimes.copyModal.cinema') }}
          </label>
          <select
            :value="selectedCinemaId"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            @change="(e: any) => handleCinemaChange(e.target.value)"
          >
            <option value="">{{ t('adminShowtimes.copyModal.allCinemas') }}</option>
            <option v-for="c in cinemasList" :key="c.id" :value="c.id">
              {{ c.name }} ({{ c.city }})
            </option>
          </select>
        </div>

        <!-- Auditoriums Multi-select if cinema selected -->
        <div v-if="selectedCinemaId" class="space-y-2">
          <div class="flex items-center justify-between">
            <label class="text-xs font-medium text-slate-300">
              {{ t('adminShowtimes.copyModal.auditoriums') }}
            </label>
            <div v-if="availableAuditoriums.length > 0" class="flex gap-2 text-[11px]">
              <button
                type="button"
                class="text-indigo-400 hover:text-indigo-300"
                @click="selectAllAuditoriums"
              >
                {{ t('common.all') }}
              </button>
              <span class="text-slate-600">|</span>
              <button
                type="button"
                class="text-slate-400 hover:text-slate-300"
                @click="deselectAllAuditoriums"
              >
                {{ t('common.cancel') }}
              </button>
            </div>
          </div>

          <div v-if="availableAuditoriums.length > 0" class="grid grid-cols-2 sm:grid-cols-3 gap-2 max-h-36 overflow-y-auto pr-1">
            <button
              v-for="a in availableAuditoriums"
              :key="a.id"
              type="button"
              class="flex items-center gap-2 px-2.5 py-1.5 rounded-lg border text-left text-xs transition-colors"
              :class="
                selectedAuditoriumIds.includes(a.id)
                  ? 'bg-indigo-950/60 border-indigo-500 text-indigo-200'
                  : 'bg-slate-900 border-slate-700 text-slate-400 hover:border-slate-600'
              "
              @click="toggleAuditorium(a.id)"
            >
              <div
                class="w-3.5 h-3.5 rounded border flex items-center justify-center text-[10px]"
                :class="
                  selectedAuditoriumIds.includes(a.id)
                    ? 'bg-indigo-600 border-indigo-500 text-white'
                    : 'border-slate-600'
                "
              >
                <svg
                  v-if="selectedAuditoriumIds.includes(a.id)"
                  class="w-2.5 h-2.5"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <span class="truncate font-medium text-slate-200">{{ a.name }}</span>
            </button>
          </div>
          <p v-if="selectedAuditoriumIds.length === 0" class="text-[11px] text-slate-400 italic">
            💡 Không chọn phòng cụ thể: Hệ thống sẽ sao chép toàn bộ các phòng có lịch trong ngày nguồn.
          </p>
        </div>

        <!-- Safe copy notice -->
        <div class="bg-slate-850 p-3 rounded-xl border border-slate-700/80 text-xs text-slate-400 flex items-start gap-2.5">
          <svg class="w-4 h-4 text-indigo-400 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <p>{{ t('adminShowtimes.copyModal.copyNotice') }}</p>
        </div>
      </div>

      <!-- Result Banner (After copy executed) -->
      <div v-else class="space-y-4 py-4 text-center">
        <div class="w-12 h-12 rounded-full bg-emerald-950 border border-emerald-500 text-emerald-400 mx-auto flex items-center justify-center">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <h3 class="text-lg font-bold text-white">{{ t('adminShowtimes.copyModal.resultTitle') }}</h3>

        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 max-w-md mx-auto text-left">
          <div class="bg-emerald-950/40 p-3 rounded-xl border border-emerald-800">
            <p class="text-xs text-emerald-300 font-medium">{{ t('adminShowtimes.copyModal.copiedResult', { count: resultData.totalCopied }) }}</p>
          </div>
          <div class="bg-slate-850 p-3 rounded-xl border border-slate-700">
            <p class="text-xs text-slate-400 font-medium">{{ t('adminShowtimes.copyModal.skippedResult', { count: resultData.totalSkipped }) }}</p>
          </div>
          <div class="bg-amber-950/40 p-3 rounded-xl border border-amber-800">
            <p class="text-xs text-amber-300 font-medium">{{ t('adminShowtimes.copyModal.conflictedResult', { count: resultData.totalConflicted }) }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Modal Footer -->
    <template #footer>
      <div class="flex items-center justify-end gap-3">
        <Button
          v-if="!resultData"
          variant="ghost"
          size="md"
          @click="closeModal"
        >
          {{ t('common.cancel') }}
        </Button>

        <Button
          v-if="!resultData"
          variant="primary"
          size="md"
          :disabled="!canSubmit"
          :loading="isCopying"
          @click="handleCopy"
        >
          {{ t('adminShowtimes.copyModal.copyBtn') }}
        </Button>

        <Button
          v-else
          variant="primary"
          size="md"
          @click="closeModal"
        >
          {{ t('adminShowtimes.copyModal.doneBtn') }}
        </Button>
      </div>
    </template>
  </Modal>
</template>

