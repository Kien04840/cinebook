<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  modelValue?: string | number | null
  label?: string
  id?: string
  name?: string
  type?: string
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
  type: 'text',
  placeholder: '',
  disabled: false,
  required: false,
  error: '',
  hint: '',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number): void
  (e: 'blur', event: FocusEvent): void
  (e: 'focus', event: FocusEvent): void
}>()

const inputId = computed(() => props.id || `input-${Math.random().toString(36).substring(2, 9)}`)
const errorId = computed(() => `${inputId.value}-error`)
const hintId = computed(() => `${inputId.value}-hint`)

function onInput(event: Event) {
  const target = event.target as HTMLInputElement
  emit('update:modelValue', props.type === 'number' ? Number(target.value) : target.value)
}
</script>

<template>
  <div class="w-full space-y-1.5">
    <label
      v-if="label"
      :for="inputId"
      class="block text-xs sm:text-sm font-medium text-slate-200"
    >
      {{ label }}
      <span v-if="required" class="text-rose-400 font-bold ml-0.5">*</span>
    </label>

    <div class="relative flex items-center">
      <div v-if="$slots.prefix" class="absolute left-3.5 flex items-center pointer-events-none text-slate-400">
        <slot name="prefix" />
      </div>

      <input
        :id="inputId"
        :name="name"
        :type="type"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled"
        :required="required"
        :aria-invalid="!!error"
        :aria-describedby="error ? errorId : (hint ? hintId : undefined)"
        :class="[
          'w-full rounded-lg bg-slate-800 border text-slate-100 placeholder-slate-400 text-sm transition-colors duration-150 focus:outline-none focus:ring-2 focus:ring-offset-1 focus:ring-offset-slate-900',
          $slots.prefix ? 'pl-10' : 'pl-3.5',
          $slots.suffix ? 'pr-10' : 'pr-3.5',
          'py-2',
          error
            ? 'border-rose-500 text-rose-100 focus:border-rose-500 focus:ring-rose-500/50'
            : 'border-slate-700 focus:border-indigo-500 focus:ring-indigo-500/50',
          disabled ? 'opacity-50 cursor-not-allowed bg-slate-900' : '',
        ]"
        @input="onInput"
        @blur="emit('blur', $event)"
        @focus="emit('focus', $event)"
      />

      <div v-if="$slots.suffix" class="absolute right-3.5 flex items-center pointer-events-none text-slate-400">
        <slot name="suffix" />
      </div>
    </div>

    <p v-if="error" :id="errorId" class="text-xs text-rose-400 font-medium">
      {{ error }}
    </p>
    <p v-else-if="hint" :id="hintId" class="text-xs text-slate-400">
      {{ hint }}
    </p>
  </div>
</template>

