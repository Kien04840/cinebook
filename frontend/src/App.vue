<script setup lang="ts">
import ToastContainer from '@/components/common/ToastContainer.vue'
</script>

<template>
  <div id="cinebook-root" class="min-h-screen bg-slate-900 text-slate-100 antialiased">
    <router-view />
    <ToastContainer />
  </div>
</template>

