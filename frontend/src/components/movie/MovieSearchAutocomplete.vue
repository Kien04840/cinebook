<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import movieService from '@/services/movie.service'
import type { MovieSuggestionResponse } from '@/types/movie.types'
import { formatDate } from '@/utils/formatters'
import ImageWithFallback from '@/components/common/ImageWithFallback.vue'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    placeholder?: string
    showSuggestions?: boolean
  }>(),
  {
    modelValue: '',
    placeholder: 'Tìm kiếm phim...',
    showSuggestions: true,
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'search', value: string): void
}>()

const router = useRouter()

const keyword = ref(props.modelValue)
const suggestions = ref<MovieSuggestionResponse[]>([])
const isLoading = ref(false)
const isOpen = ref(false)
const containerRef = ref<HTMLElement | null>(null)

let debounceTimer: any = null
let currentQueryId = 0

watch(
  () => props.modelValue,
  (val) => {
    if (val !== keyword.value) {
      keyword.value = val
    }
  }
)

function handleInput(e: Event) {
  const val = (e.target as HTMLInputElement).value
  keyword.value = val
  emit('update:modelValue', val)

  if (!props.showSuggestions) return

  clearTimeout(debounceTimer)
  const trimmed = val.trim()

  if (trimmed.length < 2) {
    suggestions.value = []
    isOpen.value = false
    isLoading.value = false
    return
  }

  isLoading.value = true
  isOpen.value = true

  debounceTimer = setTimeout(async () => {
    const queryId = ++currentQueryId
    try {
      const results = await movieService.getSuggestions(trimmed, 6)
      if (queryId === currentQueryId) {
        suggestions.value = results || []
      }
    } catch (err) {
      if (queryId === currentQueryId) {
        suggestions.value = []
      }
    } finally {
      if (queryId === currentQueryId) {
        isLoading.value = false
      }
    }
  }, 350)
}

function handleSelect(movie: MovieSuggestionResponse) {
  isOpen.value = false
  keyword.value = ''
  emit('update:modelValue', '')
  router.push(`/movies/${movie.id}`)
}

function handleSubmit() {
  isOpen.value = false
  emit('search', keyword.value)
}

function handleClear() {
  keyword.value = ''
  suggestions.value = []
  isOpen.value = false
  emit('update:modelValue', '')
  emit('search', '')
}

function handleClickOutside(event: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(event.target as Node)) {
    isOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  clearTimeout(debounceTimer)
})
</script>

<template>
  <div ref="containerRef" class="relative w-full">
    <!-- Search Input -->
    <div class="relative flex items-center">
      <span class="absolute left-3.5 text-slate-400 pointer-events-none">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
      </span>

      <input
        v-model="keyword"
        type="text"
        :placeholder="placeholder"
        class="w-full pl-10 pr-10 py-2 bg-slate-900 border border-slate-700/80 rounded-xl text-sm text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 transition-colors"
        @input="handleInput"
        @focus="keyword.trim().length >= 2 && suggestions.length > 0 && (isOpen = true)"
        @keydown.enter="handleSubmit"
        @keydown.esc="isOpen = false"
      />

      <!-- Clear / Loading Icon -->
      <span v-if="isLoading" class="absolute right-3.5 flex items-center">
        <svg class="animate-spin h-4 w-4 text-indigo-400" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"></path>
        </svg>
      </span>
      <button
        v-else-if="keyword"
        type="button"
        class="absolute right-3.5 text-slate-400 hover:text-white transition-colors"
        @click="handleClear"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>

    <!-- Suggestions Dropdown -->
    <transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="transform scale-95 opacity-0"
      enter-to-class="transform scale-100 opacity-100"
      leave-active-class="transition duration-100 ease-in"
      leave-from-class="transform scale-100 opacity-100"
      leave-to-class="transform scale-95 opacity-0"
    >
      <div
        v-if="isOpen && keyword.trim().length >= 2"
        class="absolute left-0 right-0 mt-2 z-50 bg-slate-900 border border-slate-700 rounded-2xl shadow-2xl overflow-hidden backdrop-blur-md divide-y divide-slate-800"
      >
        <!-- Loading State -->
        <div v-if="isLoading && suggestions.length === 0" class="p-6 flex items-center justify-center gap-2 text-xs text-slate-400">
          <svg class="animate-spin h-4 w-4 text-indigo-500" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"></path>
          </svg>
          <span>Đang tìm kiếm gợi ý...</span>
        </div>

        <!-- Empty State -->
        <div
          v-else-if="!isLoading && suggestions.length === 0"
          class="p-6 text-center text-xs text-slate-400"
        >
          <div class="text-xl mb-1">🎬</div>
          <span>Không tìm thấy phim phù hợp với "<strong>{{ keyword }}</strong>"</span>
        </div>

        <!-- Suggestions List -->
        <div v-else class="max-h-80 overflow-y-auto">
          <button
            v-for="movie in suggestions"
            :key="movie.id"
            type="button"
            class="w-full p-3 flex items-center gap-3.5 hover:bg-slate-800/80 transition-colors text-left group"
            @click="handleSelect(movie)"
          >
            <!-- Poster Thumbnail -->
            <div class="w-12 h-16 rounded-lg overflow-hidden bg-slate-800 shrink-0 border border-slate-700/60 shadow-sm">
              <ImageWithFallback
                :src="movie.posterUrl"
                :alt="movie.title"
                fallback-type="movie"
                class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-200"
              />
            </div>

            <!-- Movie Info -->
            <div class="flex-1 min-w-0">
              <h4 class="text-sm font-semibold text-white truncate group-hover:text-indigo-400 transition-colors">
                {{ movie.title }}
              </h4>
              <div class="flex flex-col sm:flex-row sm:items-center gap-1 sm:gap-3 mt-1 text-[11px] text-slate-400">
                <span v-if="movie.durationMinutes">
                  Thời lượng: <strong class="text-slate-300 font-mono">{{ movie.durationMinutes }} phút</strong>
                </span>
                <span v-if="movie.releaseDate">
                  Khởi chiếu: <strong class="text-slate-300 font-mono">{{ formatDate(movie.releaseDate) }}</strong>
                </span>
              </div>
            </div>

            <!-- Arrow Icon -->
            <span class="text-slate-500 group-hover:text-indigo-400 group-hover:translate-x-0.5 transition-all shrink-0">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
              </svg>
            </span>
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

