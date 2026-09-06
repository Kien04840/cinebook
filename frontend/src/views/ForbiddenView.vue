<script setup lang="ts">
import Button from '@/components/common/Button.vue'
import { useI18n } from '@/composables/useI18n'

const { t } = useI18n()
</script>

<template>
  <div class="min-h-[65vh] flex flex-col items-center justify-center text-center px-4 py-16 space-y-6 relative overflow-hidden">
    <div class="absolute inset-0 flex items-center justify-center pointer-events-none -z-10">
      <div class="w-96 h-96 rounded-full bg-rose-600/10 blur-3xl"></div>
    </div>

    <div class="w-24 h-24 rounded-3xl bg-rose-950/60 border border-rose-800 flex items-center justify-center text-rose-400 text-3xl font-black shadow-2xl shadow-rose-950/50">
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
        <Button variant="primary" size="md" class="shadow-lg shadow-indigo-600/20">
          {{ t('forbidden.loginAdmin') }}
        </Button>
      </router-link>
    </div>
  </div>
</template>
