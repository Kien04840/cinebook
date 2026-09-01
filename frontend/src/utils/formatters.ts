/**
 * Standard formatters for CineBook Frontend
 */

export function formatCurrency(amount: number | string | null | undefined): string {
  if (amount === null || amount === undefined || isNaN(Number(amount))) {
    return '0 ₫'
  }
  const numeric = typeof amount === 'string' ? parseFloat(amount) : amount
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(numeric)
}

export function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr)
    return new Intl.DateTimeFormat('vi-VN', {
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

export function formatTime(timeStr: string | null | undefined): string {
  if (!timeStr) return ''
  try {
    if (timeStr.includes('T')) {
      const d = new Date(timeStr)
      return new Intl.DateTimeFormat('vi-VN', {
        hour: '2-digit',
        minute: '2-digit',
      }).format(d)
    }
    return timeStr.substring(0, 5)
  } catch {
    return timeStr
  }
}

export function formatDuration(minutes: number | null | undefined, locale: 'vi' | 'en' | string = 'vi'): string {
  const isEn = locale === 'en'
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

export function formatStatus(status: string | null | undefined, locale: 'vi' | 'en' | string = 'vi'): string {
  if (!status) return ''
  const isEn = locale === 'en'
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
    SUCCESS: 'Thành công',
    FAILED: 'Thất bại',
    PENDING: 'Đang xử lý',
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
    SUCCESS: 'Success',
    FAILED: 'Failed',
    PENDING: 'Pending',
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
  return `${(num * 100).toFixed(1)}%`
}


