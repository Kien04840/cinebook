<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { PromotionResponse } from '@/types/promotion.types'
import promotionService from '@/services/promotion.service'
import { formatCurrency, formatDate } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Card from '@/components/common/Card.vue'
import Button from '@/components/common/Button.vue'
import Badge from '@/components/common/Badge.vue'
import Pagination from '@/components/common/Pagination.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import { useToast } from '@/composables/useToast'

const { t } = useI18n()
const router = useRouter()
const toast = useToast()

const promotions = ref<PromotionResponse[]>([])
const isLoading = ref(true)
const errorMessage = ref('')
const currentPage = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const pageSize = ref(9)

async function fetchPromotions() {
  isLoading.value = true
  errorMessage.value = ''

  try {
    const res = await promotionService.getPublicPromotions({
      page: currentPage.value,
      size: pageSize.value,
    })

    promotions.value = res.content || []
    totalPages.value = res.totalPages || 0
    totalElements.value = res.totalElements || 0
  } catch (err: any) {
    errorMessage.value = err.response?.data?.message || t('promotions.emptyDesc')
  } finally {
    isLoading.value = false
  }
}

function copyPromoCode(code: string) {
  navigator.clipboard.writeText(code)
  toast.success(t('promotions.codeCopied', { code }))
}

function goToBooking() {
  router.push('/movies')
}

watch(currentPage, () => {
  fetchPromotions()
  window.scrollTo({ top: 0, behavior: 'smooth' })
})

onMounted(() => {
  fetchPromotions()
})
</script>

<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
    <div class="text-center max-w-2xl mx-auto space-y-3">
      <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-amber-500/10 text-amber-400 border border-amber-500/20 text-xs font-bold uppercase tracking-wider">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v13m0-13V6a2 2 0 112 2h-2zm0 0V5.5A2.5 2.5 0 109.5 8H12zm-7 4h14M5 12a2 2 0 110-4h14a2 2 0 110 4M5 12v7a2 2 0 002 2h10a2 2 0 002-2v-7" />
        </svg>
        {{ t('promotions.title') }}
      </div>
      <h1 class="text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
        {{ t('promotions.title') }}
      </h1>
      <p class="text-sm sm:text-base text-slate-300">
        {{ t('promotions.subtitle') }}
      </p>
    </div>

    <ErrorAlert v-if="errorMessage" :message="errorMessage" @retry="fetchPromotions" />

    <div v-if="isLoading" class="py-20 text-center text-slate-400">
      <div class="inline-flex items-center gap-3">
        <div class="w-6 h-6 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin"></div>
        <span class="text-sm font-medium">{{ t('promotions.searchPlaceholder') }}...</span>
      </div>
    </div>

    <div v-else-if="promotions.length === 0" class="py-20 text-center space-y-3">
      <div class="w-16 h-16 rounded-2xl bg-slate-800 border border-slate-700 mx-auto flex items-center justify-center text-slate-400">
        <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z" />
        </svg>
      </div>
      <p class="text-base font-bold text-white">{{ t('promotions.emptyTitle') }}</p>
      <p class="text-xs sm:text-sm text-slate-400 max-w-sm mx-auto">
        {{ t('promotions.emptyDesc') }}
      </p>
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <Card
        v-for="p in promotions"
        :key="p.id"
        class="flex flex-col justify-between border-amber-500/20 hover:border-amber-500/50 transition-all duration-300 hover:shadow-xl hover:shadow-amber-500/5 relative overflow-hidden group"
      >
        <div class="space-y-4">
          <div class="flex items-start justify-between gap-3">
            <div>
              <div class="flex items-center gap-2 mb-1">
                <span class="inline-flex items-center gap-1 font-mono text-sm font-black text-amber-400 px-2.5 py-1 rounded bg-amber-500/10 border border-amber-500/30">
                  <span>🏷️</span> {{ p.code }}
                </span>
                <Badge variant="success" size="sm">
                  {{ p.discountType === 'PERCENTAGE' ? `${p.discountValue}%` : formatCurrency(p.discountValue) }}
                </Badge>
              </div>
              <h3 class="text-base font-bold text-white group-hover:text-amber-400 transition-colors">
                {{ p.name }}
              </h3>
            </div>
          </div>

          <p v-if="p.description" class="text-xs text-slate-300 line-clamp-2">
            {{ p.description }}
          </p>

          <div class="space-y-1.5 text-xs text-slate-400 pt-3 border-t border-slate-800 font-mono">
            <div v-if="p.minOrderAmount" class="flex justify-between">
              <span>{{ t('promotions.minSpend', { amount: '' }) }}:</span>
              <span class="text-slate-200 font-semibold">{{ formatCurrency(p.minOrderAmount) }}</span>
            </div>
            <div v-if="p.maxDiscountAmount" class="flex justify-between">
              <span>{{ t('promotions.maxDiscount', { amount: '' }) }}:</span>
              <span class="text-slate-200 font-semibold">{{ formatCurrency(p.maxDiscountAmount) }}</span>
            </div>
            <div class="flex justify-between text-slate-400">
              <span>{{ t('promotions.expiresOn', { date: '' }) }}:</span>
              <span class="text-amber-300">{{ formatDate(p.endAt) }}</span>
            </div>
          </div>
        </div>

        <template #footer>
          <div class="pt-4 border-t border-slate-800/80 grid grid-cols-2 gap-2 w-full">
            <Button
              variant="secondary"
              size="sm"
              class="w-full justify-center font-mono text-xs"
              @click="copyPromoCode(p.code)"
            >
              {{ t('promotions.copyCodeBtn') }}
            </Button>
            <Button
              variant="primary"
              size="sm"
              class="w-full justify-center text-xs shadow-lg shadow-indigo-600/20"
              @click="goToBooking"
            >
              {{ t('promotions.bookNowBtn') }}
            </Button>
          </div>
        </template>
      </Card>
    </div>

    <Pagination
      v-if="totalPages > 1"
      v-model:currentPage="currentPage"
      :totalPages="totalPages"
      :totalElements="totalElements"
      :pageSize="pageSize"
    />
  </div>
</template>
