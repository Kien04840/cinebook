import { useI18n } from '@/composables/useI18n'

/**
 * Resolves a human-friendly, localized error message from an API error or unknown error.
 *
 * Flow:
 * 1. If error.response?.data?.code exists and has a translation in errors.<CODE>, return that translation.
 * 2. Else fallback to error.response?.data?.message (safe error message from Backend).
 * 3. Else fallback to error.message if string.
 * 4. Else fallback to provided fallback string or default localized error message.
 */
export function getErrorMessage(error: any, fallback?: string): string {
  const { t } = useI18n()
  const defaultFallback =
    fallback ||
    (t('common.errorTitle') !== 'common.errorTitle' ? t('common.errorTitle') : 'Đã có lỗi xảy ra')

  if (!error) return defaultFallback

  // Check machine-readable code
  const code = error?.response?.data?.code || error?.code
  if (code && typeof code === 'string') {
    const errorKey = `errors.${code}`
    const translated = t(errorKey)
    if (translated && translated !== errorKey) {
      return translated
    }
  }

  // Fallback to backend message
  const backendMsg =
    error?.response?.data?.message || (typeof error?.message === 'string' ? error.message : null)
  if (backendMsg && typeof backendMsg === 'string') {
    return backendMsg
  }

  return defaultFallback
}

export const getApiErrorMessage = getErrorMessage
export default getErrorMessage
