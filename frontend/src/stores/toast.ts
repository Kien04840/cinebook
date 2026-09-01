import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface ToastItem {
  id: string
  type: 'success' | 'error' | 'warning' | 'info'
  title?: string
  message: string
  duration?: number
}

export const useToastStore = defineStore('toast', () => {
  const toasts = ref<ToastItem[]>([])

  function show(toast: Omit<ToastItem, 'id'>) {
    const id = Date.now().toString() + Math.random().toString(36).substring(2, 7)
    const duration = toast.duration ?? 4000
    const newToast: ToastItem = { ...toast, id, duration }

    toasts.value.push(newToast)

    if (duration > 0) {
      setTimeout(() => {
        remove(id)
      }, duration)
    }

    return id
  }

  function remove(id: string) {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  function success(message: string, title: string = 'Thành công') {
    return show({ type: 'success', title, message })
  }

  function error(message: string, title: string = 'Đã có lỗi xảy ra') {
    return show({ type: 'error', title, message })
  }

  function warning(message: string, title: string = 'Cảnh báo') {
    return show({ type: 'warning', title, message })
  }

  function info(message: string, title: string = 'Thông báo') {
    return show({ type: 'info', title, message })
  }

  return {
    toasts,
    show,
    remove,
    success,
    error,
    warning,
    info,
  }
})

