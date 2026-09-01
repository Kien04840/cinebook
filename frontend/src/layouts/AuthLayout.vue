<script setup lang="ts">
</script>

<template>
  <div class="min-h-screen flex flex-col justify-center py-12 sm:px-6 lg:px-8 bg-slate-950 text-slate-100 relative overflow-hidden">
    <!-- Subtle Background Glow -->
    <div class="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-indigo-600/10 rounded-full blur-3xl pointer-events-none" />

    <!-- Logo Header -->
    <div class="sm:mx-auto sm:w-full sm:max-w-md text-center z-10">
      <router-link to="/" class="inline-flex items-center gap-2.5 group">
        <div class="w-12 h-12 rounded-xl bg-gradient-to-tr from-indigo-600 to-indigo-400 flex items-center justify-center text-white shadow-lg shadow-indigo-500/25 group-hover:scale-105 transition-transform">
          <svg class="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 4v16M17 4v16M3 8h4m10 0h4M3 12h18M3 16h4m10 0h4M4 20h16a1 1 0 001-1V5a1 1 0 00-1-1H4a1 1 0 00-1 1v14a1 1 0 001 1z" />
          </svg>
        </div>
      </router-link>
      <h2 class="mt-4 text-2xl font-bold tracking-tight text-white">
        CINE<span class="text-indigo-500">BOOK</span>
      </h2>
      <p class="mt-1 text-xs text-slate-400">
        Hệ thống đặt vé xem phim trực tuyến hàng đầu
      </p>
    </div>

    <!-- Auth Card Box -->
    <div class="mt-8 sm:mx-auto sm:w-full sm:max-w-md z-10 px-4 sm:px-0">
      <div class="bg-slate-900 border border-slate-800 py-8 px-6 sm:px-10 shadow-2xl rounded-2xl">
        <router-view />
      </div>
    </div>
  </div>
</template>

