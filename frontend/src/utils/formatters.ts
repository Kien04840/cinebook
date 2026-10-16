/**
 * Standard formatters for CineBook Frontend
 */

export function formatCurrency(amount: number | string | null | undefined): string {
  if (amount === null || amount === undefined || amount === '') {
    return '0 ₫'
  }
  const numeric = typeof amount === 'string' ? parseFloat(amount.replace(/,/g, '')) : amount
  if (isNaN(numeric)) {
    return '0 ₫'
  }
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(numeric)
}

export function formatCompactCurrency(amount: number | string | null | undefined): string {
  if (amount === null || amount === undefined || amount === '') {
    return '0 ₫'
  }
  const numeric = typeof amount === 'string' ? parseFloat(amount.replace(/,/g, '')) : amount
  if (isNaN(numeric) || numeric === 0) {
    return '0 ₫'
  }
  const sign = numeric < 0 ? '-' : ''
  const abs = Math.abs(numeric)

  if (abs >= 1_000_000_000) {
    const val = abs / 1_000_000_000
    const formatted = val >= 10 ? val.toFixed(1).replace(/\.0$/, '') : val.toFixed(2).replace(/\.?0+$/, '')
    return `${sign}${formatted.replace('.', ',')} tỷ ₫`
  }
  if (abs >= 1_000_000) {
    const val = abs / 1_000_000
    const formatted = val >= 10 ? val.toFixed(1).replace(/\.0$/, '') : val.toFixed(2).replace(/\.?0+$/, '')
    return `${sign}${formatted.replace('.', ',')} tr ₫`
  }
  if (abs >= 1_000) {
    const val = abs / 1_000
    const formatted = val >= 10 ? val.toFixed(0) : val.toFixed(1).replace(/\.0$/, '')
    return `${sign}${formatted.replace('.', ',')} k ₫`
  }
  return `${sign}${Math.round(abs)} ₫`
}

export function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr)
    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: 'Asia/Ho_Chi_Minh',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(d)
  } catch {
    return dateStr
  }
}

export function formatDateTime(dateStr: string | null | undefined): string {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr)
    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: 'Asia/Ho_Chi_Minh',
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(d)
  } catch {
    return dateStr
  }
}

/**
 * Formats a date string into standard system format: dd/MM/yyyy HH:mm
 * Uses Asia/Ho_Chi_Minh timezone consistently.
 */
export function formatStandardDateTime(dateStr: string | null | undefined): string {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr)
    if (isNaN(d.getTime())) return dateStr
    const parts = new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Asia/Ho_Chi_Minh',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).formatToParts(d)
    const map: Record<string, string> = {}
    parts.forEach((p) => {
      map[p.type] = p.value
    })
    return `${map.day}/${map.month}/${map.year} ${map.hour}:${map.minute}`
  } catch {
    return dateStr
  }
}

export function formatTime(timeStr: string | null | undefined): string {
  if (!timeStr) return ''
  try {
    if (timeStr.includes('T')) {
      const d = new Date(timeStr)
      return new Intl.DateTimeFormat('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      }).format(d)
    }
    return timeStr.substring(0, 5)
  } catch {
    return timeStr
  }
}

function getActiveLocale(): string {
  try {
    return localStorage.getItem('cinebook_lang') || 'vi'
  } catch {
    return 'vi'
  }
}

export function formatDuration(minutes: number | null | undefined, locale?: 'vi' | 'en' | string): string {
  const effectiveLocale = locale || getActiveLocale()
  const isEn = effectiveLocale === 'en'
  const hourUnit = isEn ? 'h' : 'g'
  const minUnit = isEn ? 'm' : 'p'

  if (!minutes || minutes <= 0) {
    return `0${minUnit}`
  }

  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60

  if (hours === 0) {
    return `${mins}${minUnit}`
  }
  if (mins === 0) {
    return `${hours}${hourUnit}`
  }
  return `${hours}${hourUnit} ${mins}${minUnit}`
}

export const LANGUAGE_NAME_MAP: Record<string, { vi: string; en: string }> = {
  vi: { vi: 'Tiếng Việt', en: 'Vietnamese' },
  en: { vi: 'Tiếng Anh', en: 'English' },
  ja: { vi: 'Tiếng Nhật', en: 'Japanese' },
  ko: { vi: 'Tiếng Hàn', en: 'Korean' },
  zh: { vi: 'Tiếng Trung', en: 'Chinese' },
  fr: { vi: 'Tiếng Pháp', en: 'French' },
  es: { vi: 'Tiếng Tây Ban Nha', en: 'Spanish' },
  th: { vi: 'Tiếng Thái', en: 'Thai' },
  de: { vi: 'Tiếng Đức', en: 'German' },
  ru: { vi: 'Tiếng Nga', en: 'Russian' },
  it: { vi: 'Tiếng Ý', en: 'Italian' },
  hi: { vi: 'Tiếng Hindi', en: 'Hindi' },
}

export function formatLanguage(lang?: string | null, locale?: 'vi' | 'en' | string): string {
  if (!lang) return ''
  const trimmed = lang.trim()
  const lower = trimmed.toLowerCase()
  const isEn = (locale || getActiveLocale()) === 'en'
  if (LANGUAGE_NAME_MAP[lower]) {
    return isEn ? LANGUAGE_NAME_MAP[lower].en : LANGUAGE_NAME_MAP[lower].vi
  }
  return trimmed
}

export function formatStatus(status: string | null | undefined, locale?: 'vi' | 'en' | string): string {
  if (!status) return ''
  const effectiveLocale = locale || getActiveLocale()
  const isEn = effectiveLocale === 'en'
  const mapVi: Record<string, string> = {
    ACTIVE: 'Đang hoạt động',
    INACTIVE: 'Không hoạt động',
    BLOCKED: 'Đã khóa',
    NOW_SHOWING: 'Đang chiếu',
    COMING_SOON: 'Sắp chiếu',
    ENDED: 'Đã kết thúc',
    HIDDEN: 'Tạm ẩn',
    SCHEDULED: 'Đã lên lịch',
    HOLDING: 'Đang giữ chỗ',
    PENDING_PAYMENT: 'Chờ thanh toán',
    PAID: 'Đã thanh toán',
    CANCELLED: 'Đã hủy',
    REFUNDED: 'Đã hoàn tiền',
    EXPIRED: 'Hết hạn',
    VALID: 'Hợp lệ',
    USED: 'Đã sử dụng',
    REDEEMED: 'Đã soát vé',
    UNREDEEMED: 'Chưa soát',
    SUCCESS: 'Thành công',
    FAILED: 'Thất bại',
    PENDING: 'Đang xử lý',
    DRAFT: 'Bản nháp',
    PAUSED: 'Tạm dừng',
  }
  const mapEn: Record<string, string> = {
    ACTIVE: 'Active',
    INACTIVE: 'Inactive',
    BLOCKED: 'Blocked',
    NOW_SHOWING: 'Now Showing',
    COMING_SOON: 'Coming Soon',
    ENDED: 'Ended',
    HIDDEN: 'Hidden',
    SCHEDULED: 'Scheduled',
    HOLDING: 'Holding',
    PENDING_PAYMENT: 'Pending Payment',
    PAID: 'Paid',
    CANCELLED: 'Cancelled',
    REFUNDED: 'Refunded',
    EXPIRED: 'Expired',
    VALID: 'Valid',
    USED: 'Used',
    REDEEMED: 'Redeemed',
    UNREDEEMED: 'Unredeemed',
    SUCCESS: 'Success',
    FAILED: 'Failed',
    PENDING: 'Pending',
    DRAFT: 'Draft',
    PAUSED: 'Paused',
  }
  const map = isEn ? mapEn : mapVi
  return map[status] || status
}

export function formatNumber(val: number | string | null | undefined): string {
  if (val === null || val === undefined || isNaN(Number(val))) return '0'
  return new Intl.NumberFormat('vi-VN').format(Number(val))
}

export function formatPercent(val: number | string | null | undefined): string {
  if (val === null || val === undefined || isNaN(Number(val))) return '0%'
  const num = Number(val)
  if (num === 0) return '0%'
  return `${Number(num.toFixed(1))}%`
}

export function formatRole(role: string | null | undefined, locale?: 'vi' | 'en' | string): string {
  if (!role) return ''
  const effectiveLocale = locale || getActiveLocale()
  const isEn = effectiveLocale === 'en'
  const normalized = role.replace(/^ROLE_/, '').toUpperCase()
  if (normalized === 'ADMIN') {
    return isEn ? 'Administrator' : 'Quản trị viên'
  }
  if (normalized === 'CUSTOMER') {
    return isEn ? 'Customer' : 'Khách hàng'
  }
  return role
}

export { getErrorMessage, getApiErrorMessage } from './error'


