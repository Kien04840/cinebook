<script setup lang="ts">
import { computed } from 'vue'

interface Option {
  value: string | number
  label: string
  disabled?: boolean
}

interface Props {
  modelValue?: string | number | null
  options: Option[]
  label?: string
  id?: string
  name?: string
  placeholder?: string
  disabled?: boolean
  required?: boolean
  error?: string
  hint?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  label: '',
  id: '',
  name: '',
  placeholder: '-- Chọn một mục --',
  disabled: false,
  required: false,
  error: '',
  hint: '',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number): void
  (e: 'change', value: string | number): void
}>()

const selectId = computed(() => props.id || `select-${Math.random().toString(36).substring(2, 9)}`)
const errorId = computed(() => `${selectId.value}-error`)

function onChange(event: Event) {
  const target = event.target as HTMLSelectElement
  emit('update:modelValue', target.value)
  emit('change', target.value)
}
</script>

<template>
  <div class="w-full space-y-1.5">
    <label
      v-if="label"
      :for="selectId"
      class="block text-xs sm:text-sm font-medium text-slate-200"
    >
      {{ label }}
      <span v-if="required" class="text-rose-400 font-bold ml-0.5">*</span>
    </label>

    <div class="relative">
      <select
        :id="selectId"
        :name="name"
        :value="modelValue"
        :disabled="disabled"
        :required="required"
        :aria-invalid="!!error"
        :aria-describedby="error ? errorId : undefined"
        :class="[
          'w-full rounded-lg bg-slate-800 border text-slate-100 text-sm px-3.5 py-2 appearance-none transition-colors duration-150 focus:outline-none focus:ring-2 focus:ring-offset-1 focus:ring-offset-slate-900',
          error
            ? 'border-rose-500 text-rose-100 focus:border-rose-500 focus:ring-rose-500/50'
            : 'border-slate-700 focus:border-indigo-500 focus:ring-indigo-500/50',
          disabled ? 'opacity-50 cursor-not-allowed bg-slate-900' : 'cursor-pointer',
        ]"
        @change="onChange"
      >
        <option v-if="placeholder" value="" disabled selected class="bg-slate-800 text-slate-400">
          {{ placeholder }}
        </option>
        <option
          v-for="opt in options"
          :key="opt.value"
          :value="opt.value"
          :disabled="opt.disabled"
          class="bg-slate-800 text-slate-100"
        >
          {{ opt.label }}
        </option>
      </select>

      <div class="absolute inset-y-0 right-0 flex items-center pr-3 pointer-events-none text-slate-400">
        <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
        </svg>
      </div>
    </div>

    <p v-if="error" :id="errorId" class="text-xs text-rose-400 font-medium">
      {{ error }}
    </p>
    <p v-else-if="hint" class="text-xs text-slate-400">
      {{ hint }}
    </p>
  </div>
</template>

