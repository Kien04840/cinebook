<script setup lang="ts">
import { computed } from 'vue'
import Modal from '@/components/common/Modal.vue'
import { useI18n } from '@/composables/useI18n'

const { t } = useI18n()

interface Props {
  show: boolean
  trailerUrl?: string
  movieTitle?: string
}

interface Emits {
  (e: 'close'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const embedUrl = computed<string>(() => {
  if (!props.trailerUrl) return ''

  const url = props.trailerUrl.trim()

  // Match youtube.com/watch?v=ID or youtu.be/ID
  const regExp = /^.*(youtu.be\/|v\/|u\/\w\/|embed\/|watch\?v=|&v=)([^#&?]*).*/
  const match = url.match(regExp)

  if (match && match[2].length === 11) {
    return `https://www.youtube-nocookie.com/embed/${match[2]}?autoplay=1&rel=0`
  }

  // If already an embed URL
  if (url.includes('/embed/')) {
    return url
  }

  return url
})
</script>

<template>
  <Modal
    :model-value="show"
    :title="movieTitle ? `Trailer — ${movieTitle}` : t('movieDetail.trailerModalTitle')"
    size="xl"
    @update:model-value="!$event && emit('close')"
    @close="emit('close')"
  >
    <div class="relative w-full aspect-video bg-black rounded-xl overflow-hidden shadow-2xl">
      <iframe
        v-if="embedUrl"
        :src="embedUrl"
        class="w-full h-full border-0"
        title="Movie Trailer"
        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
        allowfullscreen
      ></iframe>
      <div v-else class="w-full h-full flex items-center justify-center text-slate-500 text-sm">
        {{ t('movieDetail.trailerUnavailable') }}
      </div>
    </div>
  </Modal>
</template>

