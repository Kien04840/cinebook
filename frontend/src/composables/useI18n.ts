import { ref, computed } from 'vue'
import vi from '@/locales/vi'
import en from '@/locales/en'

export type Locale = 'vi' | 'en'

const STORAGE_KEY = 'cinebook_lang'

const currentLocale = ref<Locale>((localStorage.getItem(STORAGE_KEY) as Locale) || 'vi')

const translations: Record<Locale, any> = {
  vi,
  en,
}

export function useI18n() {
  const locale = computed(() => currentLocale.value)

  function setLocale(newLocale: Locale) {
    if (translations[newLocale]) {
      currentLocale.value = newLocale
      localStorage.setItem(STORAGE_KEY, newLocale)
      document.documentElement.lang = newLocale
    }
  }

  function t(path: string, params?: Record<string, any>): string {
    const keys = path.split('.')
    let current: any = translations[currentLocale.value]

    for (const key of keys) {
      if (current && typeof current === 'object' && key in current) {
        current = current[key]
      } else {
        // Fallback to Vietnamese
        let fallback: any = translations['vi']
        for (const fbKey of keys) {
          if (fallback && typeof fallback === 'object' && fbKey in fallback) {
            fallback = fallback[fbKey]
          } else {
            return path
          }
        }
        current = fallback
        break
      }
    }

    if (typeof current !== 'string') {
      return path
    }

    if (params) {
      return current.replace(/\{(\w+)\}/g, (_, k) => {
        return params[k] !== undefined ? String(params[k]) : `{${k}}`
      })
    }

    return current
  }

  return {
    locale,
    setLocale,
    t,
    availableLocales: ['vi', 'en'] as const,
  }
}

export default useI18n

