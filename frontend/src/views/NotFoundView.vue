<script setup lang="ts">
import Button from '@/components/common/Button.vue'
import { useI18n } from '@/composables/useI18n'

const { t } = useI18n()
</script>

<template>
  <div class="min-h-[60vh] flex flex-col items-center justify-center text-center px-4 py-16 space-y-6">
    <div class="w-20 h-20 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-indigo-400 text-3xl font-black shadow-lg">
      404
    </div>

    <div class="space-y-2 max-w-md">
      <h1 class="text-2xl sm:text-3xl font-bold text-white tracking-tight">
        {{ t('notFound.title') }}
      </h1>
      <p class="text-sm text-slate-400 leading-relaxed">
        {{ t('notFound.description') }}
      </p>
    </div>

    <router-link to="/">
      <Button variant="primary" size="md">
        {{ t('notFound.backHome') }}
      </Button>
    </router-link>
  </div>
</template>
