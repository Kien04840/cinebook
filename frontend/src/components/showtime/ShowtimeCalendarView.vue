<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import type { CinemaSummaryResponse } from '@/types/cinema.types'
import type {
  CalendarScheduleResponse,
  ShowtimeSummaryResponse,
} from '@/types/showtime.types'
import showtimeService from '@/services/showtime.service'
import { formatCurrency, formatDateTime, formatTime } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import { useToast } from '@/composables/useToast'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Badge from '@/components/common/Badge.vue'
import Modal from '@/components/common/Modal.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

interface Props {
  cinemasList: CinemaSummaryResponse[]
  initialCinemaId?: string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'select-showtime', showtime: ShowtimeSummaryResponse): void
}>()

const { t } = useI18n()
const toast = useToast()

const selectedCinemaId = ref(props.initialCinemaId || '')
const currentDate = ref(new Date())
const calendarData = ref<CalendarScheduleResponse | null>(null)
const isLoading = ref(false)
const errorMessage = ref('')

// Drag & Drop State
const draggedShowtime = ref<ShowtimeSummaryResponse | null>(null)
const activeDropAuditoriumId = ref<string | null>(null)
const isMoving = ref(false)

// Quick Move Modal State (for mobile or precise time adjust)
const isQuickMoveModalOpen = ref(false)
const quickMoveShowtime = ref<ShowtimeSummaryResponse | null>(null)
const quickMoveAuditoriumId = ref('')
const quickMoveStartTime = ref('')
const quickMoveErrorMessage = ref('')
const isQuickMoveSubmitting = ref(false)

// Compute 7-day range [startOfWeek, endOfWeek]
const dateRange = computed(() => {
  const d = new Date(currentDate.value)
  const day = d.getDay()
  const diff = d.getDate() - day + (day === 0 ? -6 : 1)
  const monday = new Date(d.setDate(diff))

  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)

  return {
    from: monday.toISOString().slice(0, 10),
    to: sunday.toISOString().slice(0, 10),
  }
})

async function fetchCalendar() {
  if (!selectedCinemaId.value) {
    calendarData.value = null
    return
  }

  isLoading.value = true
  errorMessage.value = ''

  try {
    const res = await showtimeService.getCalendarSchedule({
      cinemaId: selectedCinemaId.value,
      from: dateRange.value.from,
      to: dateRange.value.to,
    })
    calendarData.value = res
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
  } finally {
    isLoading.value = false
  }
}

function prevWeek() {
  const d = new Date(currentDate.value)
  d.setDate(d.getDate() - 7)
  currentDate.value = d
}

function nextWeek() {
  const d = new Date(currentDate.value)
  d.setDate(d.getDate() + 7)
  currentDate.value = d
}

function goToday() {
  currentDate.value = new Date()
}

// Drag & Drop Handlers
function handleDragStart(e: DragEvent, s: ShowtimeSummaryResponse) {
  if (s.status !== 'SCHEDULED') {
    e.preventDefault()
    return
  }
  draggedShowtime.value = s
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', s.id)
  }
}

function handleDragEnd() {
  draggedShowtime.value = null
  activeDropAuditoriumId.value = null
}

function handleDragOver(e: DragEvent, audId: string) {
  e.preventDefault()
  if (e.dataTransfer) {
    e.dataTransfer.dropEffect = 'move'
  }
  activeDropAuditoriumId.value = audId
}

function handleDragLeave(audId: string) {
  if (activeDropAuditoriumId.value === audId) {
    activeDropAuditoriumId.value = null
  }
}

async function handleDrop(e: DragEvent, targetAudId: string) {
  e.preventDefault()
  const st = draggedShowtime.value
  draggedShowtime.value = null
  activeDropAuditoriumId.value = null

  if (!st) return
  if (st.auditoriumId === targetAudId) return // same auditorium

  isMoving.value = true
  try {
    const targetAud = calendarData.value?.auditoriums.find((a) => a.auditoriumId === targetAudId)
    await showtimeService.moveShowtimeSchedule(st.id, {
      auditoriumId: targetAudId,
      startTime: st.startTime,
    })
    toast.success(
      t('adminShowtimes.calendar.moveSuccess', {
        room: targetAud?.auditoriumName || targetAudId,
        time: formatTime(st.startTime),
      })
    )
    await fetchCalendar()
  } catch (err: any) {
    const msg = err.response?.data?.message || t('common.errorTitle')
    toast.error(t('adminShowtimes.calendar.moveFailed', { error: msg }))
  } finally {
    isMoving.value = false
  }
}

// Quick Move Modal Handlers
function openQuickMoveModal(st: ShowtimeSummaryResponse) {
  quickMoveShowtime.value = st
  quickMoveAuditoriumId.value = st.auditoriumId
  try {
    const d = new Date(st.startTime)
    const year = d.getFullYear()
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const date = String(d.getDate()).padStart(2, '0')
    const hours = String(d.getHours()).padStart(2, '0')
    const minutes = String(d.getMinutes()).padStart(2, '0')
    quickMoveStartTime.value = `${year}-${month}-${date}T${hours}:${minutes}`
  } catch {
    quickMoveStartTime.value = ''
  }
  quickMoveErrorMessage.value = ''
  isQuickMoveModalOpen.value = true
}

async function handleQuickMoveSubmit() {
  if (!quickMoveShowtime.value || !quickMoveAuditoriumId.value || !quickMoveStartTime.value) return
  isQuickMoveSubmitting.value = true
  quickMoveErrorMessage.value = ''

  try {
    const isoStart = new Date(quickMoveStartTime.value).toISOString()
    const targetAud = calendarData.value?.auditoriums.find(
      (a) => a.auditoriumId === quickMoveAuditoriumId.value
    )
    await showtimeService.moveShowtimeSchedule(quickMoveShowtime.value.id, {
      auditoriumId: quickMoveAuditoriumId.value,
      startTime: isoStart,
    })
    toast.success(
      t('adminShowtimes.calendar.moveSuccess', {
        room: targetAud?.auditoriumName || quickMoveAuditoriumId.value,
        time: formatTime(isoStart),
      })
    )
    isQuickMoveModalOpen.value = false
    await fetchCalendar()
  } catch (err: any) {
    quickMoveErrorMessage.value = err.response?.data?.message || t('common.errorTitle')
    toast.error(quickMoveErrorMessage.value)
  } finally {
    isQuickMoveSubmitting.value = false
  }
}

watch(
  () => props.cinemasList,
  (list) => {
    if (!selectedCinemaId.value && list.length > 0) {
      selectedCinemaId.value = list[0].id
    }
  },
  { immediate: true }
)

watch([selectedCinemaId, dateRange], () => {
  fetchCalendar()
})

onMounted(() => {
  if (selectedCinemaId.value) {
    fetchCalendar()
  }
})

defineExpose({
  refresh: fetchCalendar,
})
</script>

<template>
  <div class="space-y-4">
    <!-- Controls Header -->
    <Card padding="sm">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <!-- Cinema Picker -->
        <div class="flex items-center gap-3 w-full sm:w-auto">
          <label class="text-xs font-semibold text-slate-300 whitespace-nowrap">
            {{ t('adminShowtimes.calendar.selectCinema') }}
          </label>
          <select
            v-model="selectedCinemaId"
            class="w-full sm:w-64 bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="" disabled>{{ t('adminShowtimes.generateModal.selectCinema') }}</option>
            <option v-for="c in cinemasList" :key="c.id" :value="c.id">
              {{ c.name }} ({{ c.city }})
            </option>
          </select>
        </div>

        <!-- Date Range Navigation -->
        <div class="flex items-center gap-2">
          <Button variant="secondary" size="sm" @click="prevWeek">
            <template #prefix>
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
              </svg>
            </template>
            {{ t('adminShowtimes.calendar.prevWeek') }}
          </Button>

          <Button variant="ghost" size="sm" @click="goToday">
            {{ t('adminShowtimes.calendar.today') }}
          </Button>

          <Button variant="secondary" size="sm" @click="nextWeek">
            {{ t('adminShowtimes.calendar.nextWeek') }}
            <template #suffix>
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
              </svg>
            </template>
          </Button>

          <span class="text-xs font-mono font-bold text-indigo-300 bg-slate-900 px-3 py-1.5 rounded-lg border border-slate-700 ml-2">
            {{ t('adminShowtimes.calendar.rangeLabel', { from: dateRange.from, to: dateRange.to }) }}
          </span>
        </div>
      </div>
    </Card>

    <!-- Drag & Drop Hint Banner -->
    <div class="flex items-center gap-2 text-xs text-slate-400 bg-slate-850 px-3 py-2 rounded-lg border border-slate-700/60">
      <svg class="w-4 h-4 text-indigo-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4" />
      </svg>
      <span>{{ t('adminShowtimes.calendar.dragHint') }}</span>
    </div>

    <ErrorAlert v-if="errorMessage" :message="errorMessage" />

    <!-- Calendar Swimlanes -->
    <div v-if="isLoading" class="space-y-3">
      <div v-for="i in 3" :key="i" class="animate-pulse bg-slate-800 rounded-xl p-4 border border-slate-700 h-36"></div>
    </div>

    <div
      v-else-if="!calendarData || calendarData.auditoriums.length === 0"
      class="bg-slate-800 rounded-xl p-12 text-center border border-slate-700 text-slate-400"
    >
      <svg class="w-10 h-10 mx-auto text-slate-600 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
      </svg>
      <p class="text-sm font-medium text-slate-300">{{ t('adminShowtimes.emptyCalendar') }}</p>
    </div>

    <!-- Auditorium Boards -->
    <div v-else class="space-y-4">
      <div
        v-for="aud in calendarData.auditoriums"
        :key="aud.auditoriumId"
        class="bg-slate-800 rounded-xl border transition-all duration-200 overflow-hidden shadow-md"
        :class="
          activeDropAuditoriumId === aud.auditoriumId
            ? 'ring-2 ring-indigo-500 border-indigo-400 bg-indigo-950/20'
            : 'border-slate-700/80'
        "
        @dragover="handleDragOver($event, aud.auditoriumId)"
        @dragleave="handleDragLeave(aud.auditoriumId)"
        @drop="handleDrop($event, aud.auditoriumId)"
      >
        <!-- Auditorium Header -->
        <div class="bg-slate-850 px-4 py-2.5 border-b border-slate-700 flex items-center justify-between">
          <div class="flex items-center gap-2.5">
            <span class="w-2.5 h-2.5 rounded-full bg-indigo-500"></span>
            <h3 class="font-bold text-white text-sm tracking-tight">{{ aud.auditoriumName }}</h3>
            <Badge variant="neutral">{{ aud.auditoriumType }}</Badge>
          </div>
          <div class="flex items-center gap-3">
            <span
              v-if="activeDropAuditoriumId === aud.auditoriumId"
              class="text-xs text-emerald-400 font-medium animate-pulse"
            >
              Thả vào đây để dời sang {{ aud.auditoriumName }}
            </span>
            <span class="text-xs text-slate-400">
              {{ aud.showtimes.length }} suất chiếu
            </span>
          </div>
        </div>

        <!-- Showtimes Cards Container -->
        <div class="p-3">
          <div
            v-if="aud.showtimes.length === 0"
            class="py-6 text-center text-xs text-slate-500 italic"
          >
            {{ t('adminShowtimes.calendar.noShowtimes') }}
          </div>

          <div
            v-else
            class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-2.5"
          >
            <div
              v-for="s in aud.showtimes"
              :key="s.id"
              :draggable="s.status === 'SCHEDULED'"
              class="group relative bg-slate-900/90 hover:bg-slate-850 border border-slate-700 hover:border-indigo-500/80 rounded-lg p-2.5 transition-all duration-150 shadow-sm"
              :class="[
                s.status === 'SCHEDULED' ? 'cursor-grab active:cursor-grabbing' : 'cursor-pointer',
                draggedShowtime?.id === s.id ? 'opacity-40 border-dashed border-indigo-400' : '',
              ]"
              @dragstart="handleDragStart($event, s)"
              @dragend="handleDragEnd"
              @click="emit('select-showtime', s)"
            >
              <div class="flex items-start gap-2.5">
                <img
                  v-if="s.moviePosterUrl"
                  :src="s.moviePosterUrl"
                  :alt="s.movieTitle"
                  class="w-10 h-14 object-cover rounded bg-slate-800 flex-shrink-0"
                />
                <div class="min-w-0 flex-1">
                  <div class="flex items-center justify-between">
                    <p class="font-semibold text-slate-100 text-xs truncate group-hover:text-indigo-300 transition-colors">
                      {{ s.movieTitle }}
                    </p>
                    <!-- Quick Move Button (accessible on hover & mobile) -->
                    <button
                      v-if="s.status === 'SCHEDULED'"
                      type="button"
                      class="opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-slate-700 text-slate-400 hover:text-white transition-opacity ml-1 flex-shrink-0"
                      :title="t('adminShowtimes.calendar.quickMoveModalTitle')"
                      @click.stop="openQuickMoveModal(s)"
                    >
                      <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
                      </svg>
                    </button>
                  </div>
                  <p class="text-[11px] font-mono font-bold text-slate-200 mt-1">
                    {{ formatTime(s.startTime) }} - {{ formatTime(s.endTime) }}
                  </p>
                  <p class="text-[10px] text-slate-400 mt-0.5">
                    {{ formatDateTime(s.startTime).split(' ')[0] }}
                  </p>
                  <div class="flex items-center justify-between mt-2 pt-1.5 border-t border-slate-800">
                    <span class="text-[10px] text-indigo-400 font-medium">
                      {{ s.format }} • {{ formatCurrency(s.basePrice) }}
                    </span>
                    <Badge :variant="s.status === 'SCHEDULED' ? 'success' : 'neutral'">
                      {{ s.status }}
                    </Badge>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Quick Move Modal (Mobile & Manual Adjust) -->
    <Modal
      v-model="isQuickMoveModalOpen"
      size="md"
      :title="t('adminShowtimes.calendar.quickMoveModalTitle')"
      @close="isQuickMoveModalOpen = false"
    >
      <div v-if="quickMoveShowtime" class="space-y-4 text-xs sm:text-sm text-slate-300">
        <ErrorAlert v-if="quickMoveErrorMessage" :message="quickMoveErrorMessage" />

        <div class="flex items-center gap-3 bg-slate-850 p-3 rounded-xl border border-slate-700">
          <img
            v-if="quickMoveShowtime.moviePosterUrl"
            :src="quickMoveShowtime.moviePosterUrl"
            :alt="quickMoveShowtime.movieTitle"
            class="w-9 h-13 object-cover rounded bg-slate-800"
          />
          <div>
            <p class="font-bold text-slate-100 text-sm">{{ quickMoveShowtime.movieTitle }}</p>
            <p class="text-xs text-slate-400 mt-0.5">
              {{ quickMoveShowtime.auditoriumName }} • {{ formatTime(quickMoveShowtime.startTime) }}
            </p>
          </div>
        </div>

        <div>
          <label class="block text-slate-300 font-medium mb-1">
            {{ t('adminShowtimes.calendar.newAuditorium') }} <span class="text-rose-400">*</span>
          </label>
          <select
            v-model="quickMoveAuditoriumId"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option
              v-for="aud in calendarData?.auditoriums || []"
              :key="aud.auditoriumId"
              :value="aud.auditoriumId"
            >
              {{ aud.auditoriumName }} ({{ aud.auditoriumType }})
            </option>
          </select>
        </div>

        <div>
          <label class="block text-slate-300 font-medium mb-1">
            {{ t('adminShowtimes.calendar.newStartTime') }} <span class="text-rose-400">*</span>
          </label>
          <input
            v-model="quickMoveStartTime"
            type="datetime-local"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end gap-3">
          <Button variant="secondary" size="md" @click="isQuickMoveModalOpen = false">
            {{ t('adminShowtimes.calendar.cancelBtn') }}
          </Button>
          <Button
            variant="primary"
            size="md"
            :loading="isQuickMoveSubmitting"
            @click="handleQuickMoveSubmit"
          >
            {{ t('adminShowtimes.calendar.saveMoveBtn') }}
          </Button>
        </div>
      </template>
    </Modal>
  </div>
</template>
