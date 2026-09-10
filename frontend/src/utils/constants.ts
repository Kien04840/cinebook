export interface CityOption {
  value: string
  labelVi: string
  labelEn: string
}

export const CANONICAL_CITIES: CityOption[] = [
  { value: 'Hanoi', labelVi: 'Hà Nội', labelEn: 'Hanoi' },
  { value: 'Da Nang', labelVi: 'Đà Nẵng', labelEn: 'Da Nang' },
  { value: 'Ho Chi Minh City', labelVi: 'TP. Hồ Chí Minh', labelEn: 'Ho Chi Minh City' },
]

export function getCityLabel(cityValue?: string, locale: 'vi' | 'en' = 'vi'): string {
  if (!cityValue) return ''
  const found = CANONICAL_CITIES.find(
    (c) => c.value.toLowerCase() === cityValue.trim().toLowerCase()
  )
  if (!found) return cityValue
  return locale === 'en' ? found.labelEn : found.labelVi
}

