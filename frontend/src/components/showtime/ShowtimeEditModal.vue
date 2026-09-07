<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import type { MovieSummaryResponse } from '@/types/movie.types'
import type { CinemaSummaryResponse, AuditoriumResponse } from '@/types/cinema.types'
import type {
  ShowtimeSummaryResponse,
  ShowtimeStatus,
  UpdateShowtimeRequest,
} from '@/types/showtime.types'
import showtimeService from '@/services/showtime.service'
import cinemaService from '@/services/cinema.service'
import { formatTime } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import { useToast } from '@/composables/useToast'
import Modal from '@/components/common/Modal.vue'
import Button from '@/components/common/Button.vue'
import Input from '@/components/common/Input.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

interface Props {
  modelValue: boolean
  showtime: ShowtimeSummaryResponse | null
  moviesList: MovieSummaryResponse[]
  cinemasList: CinemaSummaryResponse[]
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'success'): void
  (e: 'deleted'): void
}>()

const { t } = useI18n()
const toast = useToast()

const editForm = ref({
  movieId: '',
  cinemaId: '',
  auditoriumId: '',
  format: 'TWO_D' as 'TWO_D' | 'THREE_D' | 'IMAX',
  language: 'Tiếng Việt',
  subtitle: '',
  startTime: '',
  basePrice: 90000,
  status: 'SCHEDULED' as ShowtimeStatus,
})

const auditoriumsList = ref<AuditoriumResponse[]>([])
const isSaving = ref(false)
const isDeleting = ref(false)
const isConfirmingDelete = ref(false)
const errorMessage = ref('')

const selectedMovie = computed(() =>
  props.moviesList.find((m) => m.id === editForm.value.movieId)
)

// Calculated end time based on start time + movie duration
const calculatedEndTimeStr = computed(() => {
  if (!editForm.value.startTime || !selectedMovie.value?.durationMinutes) return null
  try {
    const d = new Date(editForm.value.startTime)
    d.setMinutes(d.getMinutes() + selectedMovie.value.durationMinutes)
    return formatTime(d.toISOString())
  } catch {
    return null
  }
})

watch(
  () => props.showtime,
  async (st) => {
    if (st) {
      editForm.value.movieId = st.movieId
      editForm.value.cinemaId = st.cinemaId
      editForm.value.auditoriumId = st.auditoriumId
      editForm.value.format = (st.format === '2D' ? 'TWO_D' : st.format === '3D' ? 'THREE_D' : st.format) as any
      editForm.value.language = st.language || 'Tiếng Việt'
      editForm.value.subtitle = st.subtitle || ''
      // Format start time for input[type="datetime-local"] (YYYY-MM-DDTHH:mm)
      editForm.value.startTime = st.startTime ? st.startTime.slice(0, 16) : ''
      editForm.value.basePrice = st.basePrice
      editForm.value.status = st.status

      if (st.cinemaId) {
        try {
          const auds = await cinemaService.getAuditoriumsByCinema(st.cinemaId)
          auditoriumsList.value = auds || []
        } catch {
          auditoriumsList.value = []
        }
      }
    }
  },
  { immediate: true }
)

async function handleCinemaChange(cinemaId: string) {
  editForm.value.cinemaId = cinemaId
  editForm.value.auditoriumId = ''
  if (!cinemaId) {
    auditoriumsList.value = []
    return
  }
  try {
    const auds = await cinemaService.getAuditoriumsByCinema(cinemaId)
    auditoriumsList.value = auds || []
    if (auds.length > 0) {
      editForm.value.auditoriumId = auds[0].id
    }
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  }
}

async function handleSave() {
  if (!props.showtime) return
  if (!editForm.value.movieId || !editForm.value.auditoriumId || !editForm.value.startTime) {
    toast.error(t('common.errorTitle'))
    return
  }

  isSaving.value = true
  errorMessage.value = ''

  try {
    let finalStartTime = editForm.value.startTime
    if (props.showtime.startTime && props.showtime.startTime.startsWith(editForm.value.startTime)) {
      finalStartTime = props.showtime.startTime
    } else if (editForm.value.startTime.length === 16) {
      finalStartTime = `${editForm.value.startTime}:00`
    }

    const payload: UpdateShowtimeRequest = {
      movieId: editForm.value.movieId,
      auditoriumId: editForm.value.auditoriumId,
      format: editForm.value.format,
      language: editForm.value.language,
      subtitle: editForm.value.subtitle ? editForm.value.subtitle : undefined,
      startTime: finalStartTime,
      basePrice: Number(editForm.value.basePrice),
      status: editForm.value.status,
    }

    await showtimeService.updateShowtime(props.showtime.id, payload)
    toast.success(t('common.successTitle'))
    emit('update:modelValue', false)
    emit('success')
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
    toast.error(errorMessage.value)
  } finally {
    isSaving.value = false
  }
}

async function handleDelete() {
  if (!props.showtime) return
  isDeleting.value = true
  errorMessage.value = ''

  try {
    await showtimeService.deleteShowtime(props.showtime.id)
    toast.success(t('common.successTitle'))
    isConfirmingDelete.value = false
    emit('update:modelValue', false)
    emit('deleted')
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
    toast.error(errorMessage.value)
  } finally {
    isDeleting.value = false
  }
}

function closeModal() {
  isConfirmingDelete.value = false
  emit('update:modelValue', false)
}
</script>

<template>
  <Modal
    :model-value="modelValue"
    size="lg"
    :title="t('adminShowtimes.editModalTitle')"
    @close="closeModal"
  >
    <div class="space-y-4 text-xs sm:text-sm text-slate-300">
      <ErrorAlert v-if="errorMessage" :message="errorMessage" />

      <!-- Delete Confirmation Banner -->
      <div
        v-if="isConfirmingDelete"
        class="bg-rose-950/40 border border-rose-800 rounded-xl p-4 space-y-3"
      >
        <div class="flex items-center gap-2 text-rose-400 font-bold text-sm">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          {{ t('adminShowtimes.deleteConfirmTitle') }}
        </div>
        <p class="text-xs text-slate-300 leading-relaxed">
          {{ t('adminShowtimes.deleteConfirmDesc') }}
        </p>
        <div class="flex justify-end gap-2 pt-2">
          <Button variant="secondary" size="sm" @click="isConfirmingDelete = false">
            {{ t('common.cancel') }}
          </Button>
          <Button variant="danger" size="sm" :loading="isDeleting" @click="handleDelete">
            {{ t('adminShowtimes.confirmDeleteBtn') }}
          </Button>
        </div>
      </div>

      <!-- Main Edit Form -->
      <div v-else class="space-y-4">
        <!-- Movie selection -->
        <div>
          <label class="block font-medium text-slate-300 mb-1">
            {{ t('adminShowtimes.colMovie') }} <span class="text-rose-400">*</span>
          </label>
          <select
            v-model="editForm.movieId"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option v-for="m in moviesList" :key="m.id" :value="m.id">
              {{ m.title }} ({{ m.durationMinutes }} {{ t('movies.minutes') }})
            </option>
          </select>
        </div>

        <!-- Cinema & Auditorium -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-300 mb-1">
              {{ t('adminShowtimes.colCinema') }} <span class="text-rose-400">*</span>
            </label>
            <select
              :value="editForm.cinemaId"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
              @change="(e: any) => handleCinemaChange(e.target.value)"
            >
              <option v-for="c in cinemasList" :key="c.id" :value="c.id">
                {{ c.name }}
              </option>
            </select>
          </div>

          <div>
            <label class="block font-medium text-slate-300 mb-1">
              {{ t('adminShowtimes.colAuditorium') }} <span class="text-rose-400">*</span>
            </label>
            <select
              v-model="editForm.auditoriumId"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option v-for="a in auditoriumsList" :key="a.id" :value="a.id">
                {{ a.name }} ({{ a.type }})
              </option>
            </select>
          </div>
        </div>

        <!-- Start time & calculated end time -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-300 mb-1">
              {{ t('adminShowtimes.colStartTime') }} <span class="text-rose-400">*</span>
            </label>
            <input
              v-model="editForm.startTime"
              type="datetime-local"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div>
            <label class="block font-medium text-slate-300 mb-1">
              {{ t('adminShowtimes.colEndTime') }}
            </label>
            <div class="bg-slate-900 border border-slate-700/80 rounded-lg px-3 py-2 text-sm text-slate-400 font-mono">
              <span v-if="calculatedEndTimeStr">
                {{ t('adminShowtimes.calculatedEndTime', { time: calculatedEndTimeStr }) }}
              </span>
              <span v-else class="text-slate-500 italic">--</span>
            </div>
          </div>
        </div>

        <!-- Price, Format, Language, Status -->
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div>
            <label class="block font-medium text-slate-300 mb-1">
              {{ t('adminShowtimes.colBasePrice') }} (₫)
            </label>
            <Input v-model="editForm.basePrice" type="number" />
          </div>

          <div>
            <label class="block font-medium text-slate-300 mb-1">
              {{ t('showtimes.format') }}
            </label>
            <select
              v-model="editForm.format"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="TWO_D">2D</option>
              <option value="THREE_D">3D</option>
              <option value="IMAX">IMAX</option>
            </select>
          </div>

          <div>
            <label class="block font-medium text-slate-300 mb-1">
              {{ t('adminShowtimes.colStatus') }}
            </label>
            <select
              v-model="editForm.status"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              <option value="SCHEDULED">{{ t('adminShowtimes.statusScheduled') }}</option>
              <option value="CANCELLED">{{ t('adminShowtimes.statusCancelled') }}</option>
              <option value="FINISHED">{{ t('adminShowtimes.statusFinished') }}</option>
            </select>
          </div>
        </div>

        <!-- Language & Subtitle -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label class="block font-medium text-slate-300 mb-1">
              {{ t('adminShowtimes.generateModal.language') }}
            </label>
            <input
              v-model="editForm.language"
              type="text"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div>
            <label class="block font-medium text-slate-300 mb-1">
              {{ t('adminShowtimes.generateModal.subtitleLang') }}
            </label>
            <input
              v-model="editForm.subtitle"
              type="text"
              class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Modal Footer -->
    <template #footer>
      <div v-if="!isConfirmingDelete" class="flex items-center justify-between w-full">
        <Button
          variant="danger"
          size="md"
          @click="isConfirmingDelete = true"
        >
          {{ t('adminShowtimes.deleteBtn') }}
        </Button>

        <div class="flex items-center gap-2">
          <Button variant="ghost" size="md" @click="closeModal">
            {{ t('common.cancel') }}
          </Button>
          <Button variant="primary" size="md" :loading="isSaving" @click="handleSave">
            {{ t('common.confirm') }}
          </Button>
        </div>
      </div>
    </template>
  </Modal>
</template>

