export interface SeatColorConfig {
  token: string
  label: string
  hex: string
  seatClass: string
  hoverClass: string
  legendClass: string
  adminSeatClass: string
}

export const ALLOWED_COLOR_TOKENS: SeatColorConfig[] = [
  {
    token: 'slate',
    label: 'Xám chuẩn (Standard)',
    hex: '#64748b',
    seatClass: 'bg-slate-750 border-slate-600/80 text-slate-200',
    hoverClass: 'hover:border-indigo-400 hover:bg-slate-700 hover:text-white',
    legendClass: 'bg-slate-750 border-slate-600 text-slate-200',
    adminSeatClass: 'bg-slate-800 border-slate-600 text-slate-200 hover:bg-slate-700',
  },
  {
    token: 'amber',
    label: 'Vàng ánh kim (VIP)',
    hex: '#f59e0b',
    seatClass: 'bg-amber-950/60 border-amber-500/70 text-amber-200 shadow-amber-500/10',
    hoverClass: 'hover:border-amber-400 hover:bg-amber-900/80',
    legendClass: 'bg-amber-950/70 border-amber-500/80 text-amber-300',
    adminSeatClass: 'bg-amber-500/20 border-amber-500 text-amber-300 hover:bg-amber-500/30',
  },
  {
    token: 'rose',
    label: 'Hồng đỏ (Couple)',
    hex: '#f43f5e',
    seatClass: 'bg-rose-950/60 border-rose-500/70 text-rose-200 shadow-rose-500/10',
    hoverClass: 'hover:border-rose-400 hover:bg-rose-900/80',
    legendClass: 'bg-rose-950/70 border-rose-500/80 text-rose-300',
    adminSeatClass: 'bg-rose-950/60 border-rose-500/80 text-rose-300 hover:bg-rose-900/80',
  },
  {
    token: 'indigo',
    label: 'Xanh chàm (Premium)',
    hex: '#6366f1',
    seatClass: 'bg-indigo-950/60 border-indigo-500/70 text-indigo-200 shadow-indigo-500/10',
    hoverClass: 'hover:border-indigo-400 hover:bg-indigo-900/80',
    legendClass: 'bg-indigo-950/70 border-indigo-500/80 text-indigo-300',
    adminSeatClass: 'bg-indigo-950/60 border-indigo-500/80 text-indigo-300 hover:bg-indigo-900/80',
  },
  {
    token: 'emerald',
    label: 'Xanh ngọc (Deluxe)',
    hex: '#10b981',
    seatClass: 'bg-emerald-950/60 border-emerald-500/70 text-emerald-200 shadow-emerald-500/10',
    hoverClass: 'hover:border-emerald-400 hover:bg-emerald-900/80',
    legendClass: 'bg-emerald-950/70 border-emerald-500/80 text-emerald-300',
    adminSeatClass: 'bg-emerald-950/60 border-emerald-500/80 text-emerald-300 hover:bg-emerald-900/80',
  },
  {
    token: 'purple',
    label: 'Tím hoàng gia (Luxury)',
    hex: '#a855f7',
    seatClass: 'bg-purple-950/60 border-purple-500/70 text-purple-200 shadow-purple-500/10',
    hoverClass: 'hover:border-purple-400 hover:bg-purple-900/80',
    legendClass: 'bg-purple-950/70 border-purple-500/80 text-purple-300',
    adminSeatClass: 'bg-purple-950/60 border-purple-500/80 text-purple-300 hover:bg-purple-900/80',
  },
  {
    token: 'cyan',
    label: 'Xanh lơ (Tech/IMAX)',
    hex: '#06b6d4',
    seatClass: 'bg-cyan-950/60 border-cyan-500/70 text-cyan-200 shadow-cyan-500/10',
    hoverClass: 'hover:border-cyan-400 hover:bg-cyan-900/80',
    legendClass: 'bg-cyan-950/70 border-cyan-500/80 text-cyan-300',
    adminSeatClass: 'bg-cyan-950/60 border-cyan-500/80 text-cyan-300 hover:bg-cyan-900/80',
  },
  {
    token: 'pink',
    label: 'Hồng phấn (Sweetbox)',
    hex: '#ec4899',
    seatClass: 'bg-pink-950/60 border-pink-500/70 text-pink-200 shadow-pink-500/10',
    hoverClass: 'hover:border-pink-400 hover:bg-pink-900/80',
    legendClass: 'bg-pink-950/70 border-pink-500/80 text-pink-300',
    adminSeatClass: 'bg-pink-950/60 border-pink-500/80 text-pink-300 hover:bg-pink-900/80',
  },
]

export interface SeatIconOption {
  identifier: string
  label: string
}

export const ALLOWED_ICONS: SeatIconOption[] = [
  { identifier: 'armchair', label: 'Ghế bành (Armchair)' },
  { identifier: 'crown', label: 'Vương miện (Crown)' },
  { identifier: 'heart', label: 'Trái tim (Heart)' },
  { identifier: 'star', label: 'Ngôi sao (Star)' },
  { identifier: 'sofa', label: 'Sofa' },
  { identifier: 'sparkles', label: 'Lấp lánh (Sparkles)' },
  { identifier: 'shield', label: 'Khiên bảo vệ (Shield)' },
  { identifier: 'gem', label: 'Kim cương (Gem)' },
]

const COLOR_MAP = new Map<string, SeatColorConfig>()
for (const item of ALLOWED_COLOR_TOKENS) {
  COLOR_MAP.set(item.token.toLowerCase(), item)
}

const DEFAULT_CONFIG = ALLOWED_COLOR_TOKENS[0] // slate

export function getSeatColorConfig(token?: string | null): SeatColorConfig {
  if (!token) return DEFAULT_CONFIG
  return COLOR_MAP.get(token.trim().toLowerCase()) || DEFAULT_CONFIG
}

export function getSeatColorClass(token?: string | null): string {
  const cfg = getSeatColorConfig(token)
  return `${cfg.seatClass} ${cfg.hoverClass}`
}

export function getSeatLegendClass(token?: string | null): string {
  return getSeatColorConfig(token).legendClass
}

export function getAdminSeatClass(token?: string | null): string {
  return getSeatColorConfig(token).adminSeatClass
}
