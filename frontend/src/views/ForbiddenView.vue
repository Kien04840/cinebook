<script setup lang="ts">
import Button from '@/components/common/Button.vue'
import { useI18n } from '@/composables/useI18n'

const { t } = useI18n()
</script>

<template>
  <div class="min-h-[60vh] flex flex-col items-center justify-center text-center px-4 py-16 space-y-6">
    <div class="w-20 h-20 rounded-full bg-rose-950/60 border border-rose-800 flex items-center justify-center text-rose-400 text-3xl font-black shadow-lg">
      403
    </div>

    <div class="space-y-2 max-w-md">
      <h1 class="text-2xl sm:text-3xl font-bold text-white tracking-tight">
        {{ t('forbidden.title') }}
      </h1>
      <p class="text-sm text-slate-400 leading-relaxed">
        {{ t('forbidden.description') }}
      </p>
    </div>

    <div class="flex items-center gap-3">
      <router-link to="/">
        <Button variant="secondary" size="md">
          {{ t('forbidden.backHome') }}
        </Button>
      </router-link>
      <router-link to="/login">
        <Button variant="primary" size="md">
          {{ t('forbidden.loginAdmin') }}
        </Button>
      </router-link>
    </div>
  </div>
</template>
