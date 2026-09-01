<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import type { BookingDetailResponse, BookingSummaryResponse, BookingStatus } from '@/types/booking.types'
import bookingService from '@/services/booking.service'
import { formatCurrency } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Input from '@/components/common/Input.vue'
import Badge from '@/components/common/Badge.vue'
import Pagination from '@/components/common/Pagination.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import Modal from '@/components/common/Modal.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { useToast } from '@/composables/useToast'

const { t } = useI18n()
const toast = useToast()

const bookings = ref<BookingSummaryResponse[]>([])
const isLoading = ref(true)
const errorMessage = ref('')

// Filters
const searchQuery = ref('')
const selectedStatus = ref<string>('ALL')
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const pageSize = ref(10)

// Modal states
const isDetailModalOpen = ref(false)
const selectedBooking = ref<BookingDetailResponse | null>(null)
const isCancelModalOpen = ref(false)
const cancelReason = ref('')
const isCancelling = ref(false)

function getStatusBadgeVariant(status: BookingStatus) {
  switch (status) {
    case 'PAID':
      return 'success'
    case 'PENDING_PAYMENT':
      return 'warning'
    case 'CANCELLED':
      return 'danger'
    case 'REFUNDED':
      return 'info'
    case 'EXPIRED':
      return 'neutral'
    default:
      return 'neutral'
  }
}

function getStatusLabel(status: BookingStatus) {
  switch (status) {
    case 'PAID':
      return t('status.PAID')
    case 'PENDING_PAYMENT':
      return t('status.PENDING_PAYMENT')
    case 'CANCELLED':
      return t('status.CANCELLED')
    case 'REFUNDED':
      return t('status.REFUNDED')
    case 'EXPIRED':
      return t('status.EXPIRED')
    default:
      return status
  }
}

async function fetchBookings() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const res = await bookingService.getAdminBookings({
      q: searchQuery.value.trim() || undefined,
      status: selectedStatus.value !== 'ALL' ? (selectedStatus.value as BookingStatus) : undefined,
      page: currentPage.value,
      size: pageSize.value,
    })

    bookings.value = res.content || []
    totalPages.value = res.totalPages || 0
    totalElements.value = res.totalElements || 0
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('common.errorTitle')
  } finally {
    isLoading.value = false
  }
}

async function openDetailModal(booking: BookingSummaryResponse) {
  try {
    const detail = await bookingService.getBookingDetail(booking.id)
    selectedBooking.value = detail
    isDetailModalOpen.value = true
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('common.errorTitle'))
  }
}

function promptCancelBooking(booking: BookingSummaryResponse) {
  selectedBooking.value = null
  bookingService.getBookingDetail(booking.id).then((b) => {
    selectedBooking.value = b
  }).catch(() => {})
  cancelReason.value = ''
  isCancelModalOpen.value = true
}

async function confirmCancelBooking() {
  if (!selectedBooking.value) return

  isCancelling.value = true
  try {
    await bookingService.cancelAdminBooking(selectedBooking.value.id, {
      reason: cancelReason.value.trim() || undefined,
    })
    toast.success(t('myBookings.cancelSuccess'))
    isCancelModalOpen.value = false
    await fetchBookings()
  } catch (err: any) {
    toast.error(err.response?.data?.message || t('myBookings.cancelFailed'))
  } finally {
    isCancelling.value = false
  }
}

function onPageChange(page: number) {
  currentPage.value = page
  fetchBookings()
}

// Watch filters with debounce
let debounceTimer: any = null
watch(searchQuery, () => {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    currentPage.value = 0
    fetchBookings()
  }, 400)
})

watch(selectedStatus, () => {
  currentPage.value = 0
  fetchBookings()
})

onMounted(() => {
  fetchBookings()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-white tracking-tight">{{ t('adminBookings.title') }}</h1>
        <p class="text-xs sm:text-sm text-slate-400 mt-1">
          {{ t('adminBookings.subtitle') }}
        </p>
      </div>

      <Button variant="secondary" size="md" :loading="isLoading" @click="fetchBookings">
        <template #prefix>
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </template>
        {{ t('common.refresh') }}
      </Button>
    </div>

    <!-- Error Alert -->
    <ErrorAlert v-if="errorMessage" :message="errorMessage" />

    <!-- Filters Bar -->
    <Card padding="sm">
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div class="sm:col-span-2">
          <Input
            v-model="searchQuery"
            :placeholder="t('adminBookings.searchPlaceholder')"
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
            v-model="selectedStatus"
            class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            <option value="ALL">{{ t('adminBookings.allStatuses') }}</option>
            <option value="PAID">{{ t('status.PAID') }}</option>
            <option value="PENDING_PAYMENT">{{ t('status.PENDING_PAYMENT') }}</option>
            <option value="CANCELLED">{{ t('status.CANCELLED') }}</option>
            <option value="REFUNDED">{{ t('status.REFUNDED') }}</option>
            <option value="EXPIRED">{{ t('status.EXPIRED') }}</option>
          </select>
        </div>
      </div>
    </Card>

    <!-- Bookings Table -->
    <Card padding="none">
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-sm">
          <thead>
            <tr class="bg-slate-850 border-b border-slate-700/80 text-xs font-semibold uppercase text-slate-400 tracking-wider">
              <th class="px-4 py-3">{{ t('adminBookings.colBookingCode') }}</th>
              <th class="px-4 py-3">{{ t('adminBookings.colCustomer') }}</th>
              <th class="px-4 py-3">{{ t('adminBookings.colMovieShowtime') }}</th>
              <th class="px-4 py-3">{{ t('adminBookings.colSeats') }}</th>
              <th class="px-4 py-3">{{ t('adminBookings.colAmount') }}</th>
              <th class="px-4 py-3">{{ t('adminBookings.colStatus') }}</th>
              <th class="px-4 py-3 text-right">{{ t('adminBookings.colActions') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-700/60">
            <!-- Loading Skeleton Rows -->
            <template v-if="isLoading">
              <tr v-for="i in 6" :key="'skel-bk-' + i" class="animate-pulse">
                <td class="px-4 py-4"><div class="h-4 w-20 bg-slate-800 rounded"></div></td>
                <td class="px-4 py-4">
                  <div class="flex items-center gap-2">
                    <div class="w-7 h-7 rounded-full bg-slate-800"></div>
                    <div class="h-4 w-28 bg-slate-800 rounded"></div>
                  </div>
                </td>
                <td class="px-4 py-4"><div class="h-4 w-36 bg-slate-800 rounded"></div></td>
                <td class="px-4 py-4"><div class="h-4 w-16 bg-slate-800 rounded"></div></td>
                <td class="px-4 py-4"><div class="h-4 w-20 bg-slate-800 rounded"></div></td>
                <td class="px-4 py-4"><div class="h-6 w-20 bg-slate-800 rounded-full"></div></td>
                <td class="px-4 py-4 text-right"><div class="h-8 w-20 bg-slate-800 rounded ml-auto"></div></td>
              </tr>
            </template>

            <!-- Empty State -->
            <tr v-else-if="bookings.length === 0">
              <td colspan="7" class="px-4 py-16 text-center text-slate-400">
                <div class="max-w-sm mx-auto space-y-2">
                  <svg class="w-10 h-10 mx-auto text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z" />
                  </svg>
                  <p class="text-sm font-medium text-slate-300">{{ t('adminBookings.emptyList') }}</p>
                  <p class="text-xs text-slate-500">{{ t('common.emptyDesc') }}</p>
                </div>
              </td>
            </tr>

            <!-- Rows -->
            <tr
              v-for="b in bookings"
              :key="b.id"
              class="hover:bg-slate-750/70 transition-colors"
            >
              <td class="px-4 py-3.5 font-mono font-bold text-indigo-400 text-xs">
                {{ b.bookingCode }}
              </td>
              <td class="px-4 py-3.5">
                <div class="flex items-center gap-2.5">
                  <UserAvatar :src="b.user?.avatarUrl" :name="b.user?.fullName" size="sm" />
                  <div class="text-xs truncate max-w-[150px]">
                    <p class="font-medium text-slate-200 truncate">{{ b.user?.fullName || 'Khách vãng lai' }}</p>
                    <p class="text-[11px] text-slate-400 truncate">{{ b.user?.email || '—' }}</p>
                  </div>
                </div>
              </td>
              <td class="px-4 py-3.5 text-xs">
                <p class="font-bold text-slate-200 line-clamp-1">{{ b.movieTitle || b.showtime?.movieTitle || '—' }}</p>
                <p class="text-slate-400 text-[11px] mt-0.5">{{ b.cinemaName || b.showtime?.cinemaName }} • {{ b.showtime?.auditoriumName }}</p>
              </td>
              <td class="px-4 py-3.5 font-mono text-xs text-slate-300">
                <span class="px-2 py-0.5 rounded bg-slate-800 text-[11px] border border-slate-700 font-bold">
                  {{ b.seatsCount || b.seatCount || 1 }} {{ t('adminReports.unitTickets') }}
                </span>
              </td>
              <td class="px-4 py-3.5 font-mono font-bold text-emerald-400 text-xs">
                {{ formatCurrency(b.totalAmount) }}
              </td>
              <td class="px-4 py-3.5">
                <Badge :variant="getStatusBadgeVariant(b.bookingStatus)">
                  {{ getStatusLabel(b.bookingStatus) }}
                </Badge>
              </td>
              <td class="px-4 py-3.5 text-right">
                <div class="flex items-center justify-end gap-1.5">
                  <Button variant="secondary" size="sm" @click="openDetailModal(b)">
                    {{ t('adminBookings.viewDetailBtn') }}
                  </Button>
                  <Button
                    v-if="b.bookingStatus === 'PENDING_PAYMENT'"
                    variant="danger"
                    size="sm"
                    @click="promptCancelBooking(b)"
                  >
                    {{ t('adminBookings.cancelBtn') }}
                  </Button>
                </div>
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

    <!-- Booking Detail Modal -->
    <Modal
      v-model="isDetailModalOpen"
      :title="t('adminBookings.detailModalTitle')"
      @close="isDetailModalOpen = false"
    >
      <div v-if="selectedBooking" class="space-y-4 text-xs text-slate-300">
        <div class="flex items-center justify-between border-b border-slate-800 pb-3">
          <div>
            <span class="text-slate-400">{{ t('adminBookings.colBookingCode') }}:</span>
            <strong class="font-mono text-sm text-indigo-400 ml-2 font-bold">{{ selectedBooking.bookingCode }}</strong>
          </div>
          <Badge :variant="getStatusBadgeVariant(selectedBooking.bookingStatus)">
            {{ getStatusLabel(selectedBooking.bookingStatus) }}
          </Badge>
        </div>

        <!-- Showtime & Movie -->
        <div class="grid grid-cols-2 gap-3 bg-slate-850 p-3 rounded-lg border border-slate-800">
          <div>
            <p class="text-slate-400">{{ t('booking.movieInfo') }}</p>
            <p class="font-bold text-white text-sm mt-0.5">{{ selectedBooking.showtime?.movie?.title }}</p>
          </div>
          <div>
            <p class="text-slate-400">{{ t('booking.cinemaInfo') }}</p>
            <p class="font-bold text-white mt-0.5">{{ selectedBooking.showtime?.cinema?.name }} ({{ selectedBooking.showtime?.auditorium?.name }})</p>
          </div>
        </div>

        <!-- Seats & Amount -->
        <div class="flex items-center justify-between bg-slate-850 p-3 rounded-lg border border-slate-800">
          <div>
            <p class="text-slate-400">{{ t('adminBookings.colSeats') }}</p>
            <p class="font-mono font-bold text-indigo-300 mt-0.5">
              {{ selectedBooking.seats?.map((s) => s.seatCode).join(', ') }}
            </p>
          </div>
          <div class="text-right">
            <p class="text-slate-400">{{ t('adminBookings.colAmount') }}</p>
            <p class="font-mono font-black text-emerald-400 text-sm mt-0.5">
              {{ formatCurrency(selectedBooking.totalAmount) }}
            </p>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end">
          <Button variant="secondary" size="md" @click="isDetailModalOpen = false">
            {{ t('common.close') }}
          </Button>
        </div>
      </template>
    </Modal>

    <!-- Cancel Modal -->
    <Modal
      v-model="isCancelModalOpen"
      :title="t('adminBookings.cancelModalTitle')"
      @close="isCancelModalOpen = false"
    >
      <div class="space-y-4 text-sm text-slate-300">
        <p>
          {{ t('myBookings.cancelConfirmDesc', { code: selectedBooking?.bookingCode || '' }) }}
        </p>

        <Input
          v-model="cancelReason"
          :placeholder="t('adminBookings.cancelReasonPlaceholder')"
        />
      </div>

      <template #footer>
        <div class="flex justify-end gap-3">
          <Button variant="secondary" size="md" @click="isCancelModalOpen = false">
            {{ t('common.cancel') }}
          </Button>
          <Button
            variant="danger"
            size="md"
            :loading="isCancelling"
            @click="confirmCancelBooking"
          >
            {{ t('adminBookings.confirmCancelBtn') }}
          </Button>
        </div>
      </template>
    </Modal>
  </div>
</template>
