<script setup lang="ts">
/**
 * FoodSelection.vue — Lựa chọn Bắp nước & Combo cho khách đặt vé
 * 
 * Hiển thị danh mục món ăn / thức uống đang mở bán (ACTIVE).
 * Cho phép tăng/giảm số lượng từ 0 đến 20 món mỗi loại.
 */
import { ref, computed, watch, onMounted } from 'vue'
import type { FoodItemResponse, BookingFoodItemRequest } from '@/types/food.types'
import foodService from '@/services/food.service'
import { formatCurrency, getErrorMessage } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'

const { t } = useI18n()

interface Props {
  modelValue: Record<string, number>
  disabled?: boolean
}

interface Emits {
  (e: 'update:modelValue', value: Record<string, number>): void
  (e: 'change', items: BookingFoodItemRequest[]): void
  (e: 'update:totalPrice', total: number): void
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
})

const emit = defineEmits<Emits>()

const foods = ref<FoodItemResponse[]>([])
const isLoading = ref<boolean>(true)
const loadError = ref<string>('')
const isExpanded = ref<boolean>(true)

/**
 * Tải danh sách bắp nước đang hoạt động (Public API)
 */
async function loadFoods() {
  isLoading.value = true
  loadError.value = ''
  try {
    const data = await foodService.getActiveFoods()
    foods.value = data || []
  } catch (err: any) {
    loadError.value = getErrorMessage(err, 'Không thể tải danh sách bắp nước.')
  } finally {
    isLoading.value = false
  }
}

function getQuantity(foodId: string): number {
  return props.modelValue[foodId] || 0
}

function updateQuantity(foodId: string, delta: number) {
  if (props.disabled) return

  const current = getQuantity(foodId)
  const next = Math.max(0, Math.min(20, current + delta))
  if (next === current) return

  const updated = { ...props.modelValue }
  if (next === 0) {
    delete updated[foodId]
  } else {
    updated[foodId] = next
  }

  emit('update:modelValue', updated)

  // Emit formatted request array
  const requestArray: BookingFoodItemRequest[] = Object.entries(updated)
    .filter(([_, qty]) => qty > 0)
    .map(([id, qty]) => ({ foodItemId: id, quantity: qty }))
  emit('change', requestArray)
}

/**
 * Tổng tiền bắp nước đã chọn
 */
const foodSubtotal = computed<number>(() => {
  return foods.value.reduce((sum, item) => {
    const qty = props.modelValue[item.id] || 0
    return sum + (Number(item.price) || 0) * qty
  }, 0)
})

/**
 * Tổng số lượng món đã chọn
 */
const totalQuantity = computed<number>(() => {
  return Object.values(props.modelValue).reduce((sum, qty) => sum + qty, 0)
})

watch(foodSubtotal, (newTotal) => {
  emit('update:totalPrice', newTotal)
}, { immediate: true })

onMounted(() => {
  loadFoods()
})
</script>

<template>
  <div class="rounded-2xl bg-slate-800/80 border border-slate-700/80 p-5 sm:p-6 space-y-4 shadow-xl transition-all">
    <!-- Header with interactive collapse/expand toggle -->
    <div
      class="flex items-center justify-between cursor-pointer select-none pb-2 border-b border-slate-700/60"
      @click="isExpanded = !isExpanded"
    >
      <div class="flex items-center gap-3 min-w-0">
        <span class="text-2xl sm:text-3xl shrink-0">🍿</span>
        <div class="min-w-0">
          <div class="flex items-center gap-2 flex-wrap">
            <h3 class="text-base sm:text-lg font-bold text-white tracking-tight">
              {{ t('food.title') }}
            </h3>
            <span class="px-2 py-0.5 text-[11px] font-medium rounded-full bg-slate-700/80 text-slate-300 border border-slate-600/60">
              {{ t('food.optional') }}
            </span>
          </div>
          <p class="text-xs text-slate-400 mt-0.5 line-clamp-1">
            {{ t('food.subtitle') }}
          </p>
        </div>
      </div>

      <div class="flex items-center gap-3 shrink-0">
        <!-- Selected badge summary -->
        <div v-if="totalQuantity > 0" class="text-right hidden sm:block">
          <span class="text-xs font-bold text-amber-400 block font-mono">
            {{ totalQuantity }} món • {{ formatCurrency(foodSubtotal) }}
          </span>
          <span class="text-[11px] text-slate-400">{{ t('food.selectedSummary', { count: totalQuantity }) }}</span>
        </div>

        <!-- Chevron Toggle Button -->
        <button
          type="button"
          class="p-2 rounded-xl bg-slate-700/60 hover:bg-slate-700 text-slate-300 hover:text-white transition-all flex items-center gap-1.5 text-xs font-semibold"
          :aria-expanded="isExpanded"
          :title="isExpanded ? t('food.collapse') : t('food.expand')"
          @click.stop="isExpanded = !isExpanded"
        >
          <span class="hidden md:inline text-xs text-slate-400">{{ isExpanded ? t('food.collapse') : t('food.expand') }}</span>
          <svg
            :class="['w-4 h-4 transition-transform duration-200', isExpanded ? 'rotate-180' : '']"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
          </svg>
        </button>
      </div>
    </div>

    <!-- Mobile compact summary when collapsed -->
    <div
      v-if="!isExpanded && totalQuantity > 0"
      class="pt-1 flex items-center justify-between text-xs sm:hidden border-t border-slate-700/40"
      @click="isExpanded = true"
    >
      <span class="text-slate-400">Đã chọn:</span>
      <span class="font-bold text-amber-400 font-mono">{{ totalQuantity }} món • {{ formatCurrency(foodSubtotal) }}</span>
    </div>

    <!-- Collapsible Body Section -->
    <div v-show="isExpanded" class="space-y-4">
      <!-- Loading Skeleton -->
      <div v-if="isLoading" class="grid grid-cols-1 sm:grid-cols-2 gap-3.5 pt-1">
        <div v-for="i in 4" :key="i" class="h-24 rounded-xl bg-slate-850 border border-slate-700/50 animate-pulse"></div>
      </div>

      <!-- Error State -->
      <div v-else-if="loadError" class="p-4 rounded-xl bg-rose-950/40 border border-rose-800/60 text-rose-200 text-xs text-center">
        {{ loadError }}
      </div>

      <!-- Empty State -->
      <div v-else-if="foods.length === 0" class="p-6 text-center text-xs text-slate-500">
        {{ t('food.empty') }}
      </div>

    <!-- Food Items Grid -->
    <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-3.5 pt-1">
      <div
        v-for="item in foods"
        :key="item.id"
        :class="[
          'flex items-center justify-between p-3 rounded-xl border transition-all duration-200 gap-3',
          getQuantity(item.id) > 0
            ? 'bg-indigo-950/30 border-indigo-500/60 shadow-md shadow-indigo-950/20'
            : 'bg-slate-900/80 border-slate-700/60 hover:border-slate-600',
          disabled ? 'opacity-70 pointer-events-none' : ''
        ]"
      >
        <!-- Left: Image & Info -->
        <div class="flex items-center gap-3 min-w-0 flex-1">
          <!-- Thumbnail -->
          <div class="w-14 h-14 rounded-xl bg-slate-800 border border-slate-700 overflow-hidden flex items-center justify-center shrink-0">
            <img
              v-if="item.imageUrl"
              :src="item.imageUrl"
              :alt="item.name"
              class="w-full h-full object-cover"
              @error="($event.target as HTMLElement).style.display = 'none'"
            />
            <span v-else class="text-2xl">🍿</span>
          </div>

          <!-- Info -->
          <div class="min-w-0 flex-1 space-y-0.5">
            <h4 class="text-xs sm:text-sm font-bold text-white tracking-tight truncate" :title="item.name">
              {{ item.name }}
            </h4>
            <p v-if="item.description" class="text-[11px] text-slate-400 line-clamp-1" :title="item.description">
              {{ item.description }}
            </p>
            <p class="text-xs font-mono font-bold text-emerald-400 pt-0.5">
              {{ formatCurrency(item.price) }}
            </p>
          </div>
        </div>

        <!-- Right: Quantity Selector -->
        <div class="flex items-center gap-1.5 shrink-0 bg-slate-800/90 rounded-xl p-1 border border-slate-700">
          <button
            type="button"
            class="w-7 h-7 rounded-lg bg-slate-700 hover:bg-slate-600 text-white font-bold flex items-center justify-center text-sm disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            :disabled="disabled || getQuantity(item.id) <= 0"
            title="Giảm số lượng"
            @click="updateQuantity(item.id, -1)"
          >
            -
          </button>

          <span class="w-6 text-center font-mono font-bold text-xs text-white">
            {{ getQuantity(item.id) }}
          </span>

          <button
            type="button"
            class="w-7 h-7 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white font-bold flex items-center justify-center text-sm disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            :disabled="disabled || getQuantity(item.id) >= 20"
            title="Tăng số lượng"
            @click="updateQuantity(item.id, 1)"
          >
            +
          </button>
        </div>
      </div>
    </div>
    </div>
  </div>
</template>
