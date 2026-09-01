<script setup lang="ts">
import { formatCurrency, formatDateTime } from '@/utils/formatters'
import { useI18n } from '@/composables/useI18n'
import Modal from '@/components/common/Modal.vue'
import Button from '@/components/common/Button.vue'
import Badge from '@/components/common/Badge.vue'
import type { RefundResponse } from '@/types/refund.types'

defineProps<{
  isOpen: boolean
  refund: RefundResponse | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const { t } = useI18n()
</script>

<template>
  <Modal :model-value="isOpen" :title="t('myBookings.refundDetailBtn')" @close="emit('close')">
    <div v-if="refund" class="space-y-4 text-xs text-slate-300">
      <div class="flex items-center justify-between border-b border-slate-800 pb-3">
        <div>
          <span class="text-slate-400">{{ t('adminRefunds.colRefundCode') }}:</span>
          <strong class="font-mono text-sm text-indigo-400 ml-2 font-bold">{{ refund.refundCode }}</strong>
        </div>
        <Badge :variant="refund.refundStatus === 'SUCCESS' ? 'success' : 'warning'">
          {{ refund.refundStatus === 'SUCCESS' ? t('status.SUCCESS') : refund.refundStatus }}
        </Badge>
      </div>

      <div class="grid grid-cols-2 gap-3 bg-slate-850 p-3 rounded-lg border border-slate-800">
        <div>
          <p class="text-slate-400">{{ t('adminRefunds.colAmount') }}</p>
          <p class="font-mono font-black text-rose-400 text-sm mt-0.5">{{ formatCurrency(refund.amount) }}</p>
        </div>
        <div>
          <p class="text-slate-400">{{ t('adminRefunds.colProcessedAt') }}</p>
          <p class="font-mono text-slate-200 mt-0.5">{{ formatDateTime(refund.processedAt) }}</p>
        </div>
      </div>

      <div class="bg-slate-850 p-3 rounded-lg border border-slate-800">
        <p class="text-slate-400">{{ t('adminRefunds.colReason') }}</p>
        <p class="font-medium text-slate-200 mt-1">{{ refund.refundReason || 'Khách yêu cầu hoàn vé' }}</p>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end">
        <Button variant="secondary" size="md" @click="emit('close')">
          {{ t('common.close') }}
        </Button>
      </div>
    </template>
  </Modal>
</template>
