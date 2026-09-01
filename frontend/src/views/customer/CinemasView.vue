<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { CinemaSummaryResponse } from '@/types/cinema.types'
import cinemaService from '@/services/cinema.service'
import { useI18n } from '@/composables/useI18n'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Input from '@/components/common/Input.vue'
import Badge from '@/components/common/Badge.vue'
import Pagination from '@/components/common/Pagination.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const { t } = useI18n()
const router = useRouter()

const cinemas = ref<CinemaSummaryResponse[]>([])
const isLoading = ref(true)
const errorMessage = ref('')

const searchQuery = ref('')
const selectedCity = ref('')
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const pageSize = ref(9)

const availableCities = ref<string[]>([])

async function loadAvailableCities() {
  try {
    const res = await cinemaService.getPublicCinemas({ status: 'ACTIVE', size: 100 })
    const citySet = new Set<string>()
    ;(res.content || []).forEach((c) => {
      if (c.city && c.city.trim()) {
        citySet.add(c.city.trim())
      }
    })
    availableCities.value = Array.from(citySet).sort()
  } catch (err) {
    console.warn('Failed to load cities', err)
  }
}

async function fetchCinemas() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const res = await cinemaService.getPublicCinemas({
      q: searchQuery.value.trim() || undefined,
      city: selectedCity.value && selectedCity.value !== 'ALL' && selectedCity.value !== 'Tất cả' ? selectedCity.value : undefined,
      status: 'ACTIVE',
      page: currentPage.value,
      size: pageSize.value,
    })

    cinemas.value = res.content || []
    totalPages.value = res.totalPages || 0
    totalElements.value = res.totalElements || 0
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('cinemas.emptyDesc')
  } finally {
    isLoading.value = false
  }
}

function selectCity(city: string) {
  selectedCity.value = city === 'ALL' || city === 'Tất cả' ? '' : city
}

function viewShowtimes(cinemaId: string) {
  router.push({ path: '/showtimes', query: { cinemaId } })
}

let debounceTimer: any = null
watch(searchQuery, () => {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    currentPage.value = 0
    fetchCinemas()
  }, 400)
})

watch(selectedCity, () => {
  currentPage.value = 0
  fetchCinemas()
})

watch(currentPage, () => {
  fetchCinemas()
  window.scrollTo({ top: 0, behavior: 'smooth' })
})

onMounted(async () => {
  await loadAvailableCities()
  fetchCinemas()
})
</script>

<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
    <!-- Hero Header -->
    <div class="text-center max-w-2xl mx-auto space-y-3">
      <h1 class="text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
        {{ t('cinemas.title') }}
      </h1>
      <p class="text-sm sm:text-base text-slate-300">
        {{ t('cinemas.subtitle') }}
      </p>
    </div>

    <!-- Search & City Tabs -->
    <div class="space-y-4">
      <div class="max-w-md mx-auto">
        <Input
          v-model="searchQuery"
          :placeholder="t('cinemas.searchPlaceholder')"
          clearable
        >
          <template #prefix>
            <svg class="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </template>
        </Input>
      </div>

      <!-- City Filter Pills -->
      <div class="flex items-center justify-center flex-wrap gap-2 pt-2">
        <button
          type="button"
          :class="[
            'px-4 py-1.5 rounded-full text-xs font-semibold transition-all',
            !selectedCity
              ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-500/25 ring-2 ring-indigo-400/50'
              : 'bg-slate-800 text-slate-300 hover:bg-slate-700 hover:text-white',
          ]"
          @click="selectCity('')"
        >
          {{ t('cinemas.allCities') }}
        </button>
        <button
          v-for="city in availableCities"
          :key="city"
          type="button"
          :class="[
            'px-4 py-1.5 rounded-full text-xs font-semibold transition-all',
            selectedCity === city
              ? 'bg-indigo-600 text-white shadow-lg shadow-indigo-500/25 ring-2 ring-indigo-400/50'
              : 'bg-slate-800 text-slate-300 hover:bg-slate-700 hover:text-white',
          ]"
          @click="selectCity(city)"
        >
          {{ city }}
        </button>
      </div>
    </div>

    <!-- Error Alert -->
    <ErrorAlert v-if="errorMessage" :message="errorMessage" @retry="fetchCinemas" />

    <!-- Loading Skeleton Grid -->
    <div
      v-else-if="isLoading"
      class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 animate-pulse"
    >
      <div
        v-for="i in 6"
        :key="'skel-' + i"
        class="h-64 rounded-2xl bg-slate-900 border border-slate-800 p-6 space-y-4"
      >
        <div class="h-6 w-3/4 bg-slate-800 rounded"></div>
        <div class="h-4 w-1/3 bg-slate-800 rounded"></div>
        <div class="h-16 w-full bg-slate-800 rounded"></div>
        <div class="h-10 w-full bg-slate-800 rounded mt-4"></div>
      </div>
    </div>

    <!-- Empty State -->
    <div
      v-else-if="cinemas.length === 0"
      class="py-16 text-center text-slate-400 space-y-3"
    >
      <div class="w-16 h-16 rounded-2xl bg-slate-800 border border-slate-700 mx-auto flex items-center justify-center text-slate-400">
        <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" />
        </svg>
      </div>
      <p class="text-base font-bold text-white">{{ t('cinemas.emptyTitle') }}</p>
      <p class="text-xs sm:text-sm text-slate-400 max-w-sm mx-auto">
        {{ t('cinemas.emptyDesc') }}
      </p>
    </div>

    <!-- Cinema Cards Grid -->
    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <Card
        v-for="cinema in cinemas"
        :key="cinema.id"
        class="flex flex-col justify-between hover:border-indigo-500/50 transition-all duration-300 hover:shadow-xl hover:shadow-indigo-500/5 group"
      >
        <div class="space-y-4">
          <div class="flex items-start justify-between gap-3">
            <div>
              <span class="inline-block px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 mb-1.5">
                {{ cinema.city }}
              </span>
              <h3 class="text-lg font-bold text-white group-hover:text-indigo-400 transition-colors">
                {{ cinema.name }}
              </h3>
            </div>
            <Badge variant="success" size="sm">
              {{ t('status.ACTIVE') }}
            </Badge>
          </div>

          <div class="space-y-2 text-xs text-slate-300 pt-2 border-t border-slate-800">
            <div class="flex items-start gap-2">
              <svg class="w-4 h-4 text-slate-400 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
              <span class="line-clamp-2">{{ cinema.address }}</span>
            </div>

            <div v-if="cinema.openingTime && cinema.closingTime" class="flex items-center gap-2 text-slate-400">
              <svg class="w-4 h-4 text-slate-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>{{ t('cinemas.openingHours') }}: {{ cinema.openingTime }} - {{ cinema.closingTime }}</span>
            </div>

            <div v-if="cinema.auditoriumsCount" class="flex items-center gap-2 text-emerald-400 font-medium">
              <svg class="w-4 h-4 text-emerald-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 4v16M17 4v16M3 8h4m10 0h4M3 12h18M3 16h4m10 0h4M4 20h16a1 1 0 001-1V5a1 1 0 00-1-1H4a1 1 0 00-1 1v14a1 1 0 001 1z" />
              </svg>
              <span>{{ cinema.auditoriumsCount }} phòng chiếu hiện đại</span>
            </div>
          </div>
        </div>

        <template #footer>
          <div class="pt-4 border-t border-slate-800/80 w-full">
            <Button
              variant="primary"
              size="md"
              class="w-full justify-center shadow-lg shadow-indigo-600/20"
              @click="viewShowtimes(cinema.id)"
            >
              <template #prefix>
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z" />
                </svg>
              </template>
              {{ t('cinemas.viewShowtimesBtn') }}
            </Button>
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
  </div>
</template>
