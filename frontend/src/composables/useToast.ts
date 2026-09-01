import { useToastStore } from '@/stores/toast'

export function useToast() {
  const store = useToastStore()

  return {
    toasts: store.toasts,
    show: store.show,
    remove: store.remove,
    success: store.success,
    error: store.error,
    warning: store.warning,
    info: store.info,
  }
}

