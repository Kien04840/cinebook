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

export interface AgeRatingOption {
  value: string
  labelVi: string
  labelEn: string
}

export const CANONICAL_AGE_RATINGS: AgeRatingOption[] = [
  // Tiêu chuẩn Việt Nam (Thông tư 05/2023/TT-BVHTTDL)
  { value: 'P', labelVi: 'P — Phổ biến cho mọi độ tuổi', labelEn: 'P — General Audiences' },
  { value: 'K', labelVi: 'K — Dưới 13 tuổi có người giám hộ', labelEn: 'K — Under 13 with Guardian' },
  { value: 'T13', labelVi: 'T13 — Từ 13 tuổi trở lên (13+)', labelEn: 'T13 — 13 Years and Older' },
  { value: 'T16', labelVi: 'T16 — Từ 16 tuổi trở lên (16+)', labelEn: 'T16 — 16 Years and Older' },
  { value: 'T18', labelVi: 'T18 — Từ 18 tuổi trở lên (18+)', labelEn: 'T18 — 18 Years and Older' },
  { value: 'C', labelVi: 'C — Cấm phổ biến', labelEn: 'C — Restricted / Prohibited' },

  // Chuẩn quốc tế TMDB (US / MPAA)
  { value: 'G', labelVi: 'G — Phù hợp mọi độ tuổi (General)', labelEn: 'G — General Audiences' },
  { value: 'PG', labelVi: 'PG — Trẻ em có hướng dẫn (Parental Guidance)', labelEn: 'PG — Parental Guidance Suggested' },
  { value: 'PG-13', labelVi: 'PG-13 — Cảnh báo dưới 13 tuổi (Parents Strongly Cautioned)', labelEn: 'PG-13 — Parents Strongly Cautioned' },
  { value: 'R', labelVi: 'R — Hạn chế dưới 17 tuổi (Restricted)', labelEn: 'R — Restricted' },
  { value: 'NC-17', labelVi: 'NC-17 — Cấm dưới 17 tuổi (Adults Only)', labelEn: 'NC-17 — Adults Only' },
  { value: 'NR', labelVi: 'NR — Chưa phân loại (Not Rated)', labelEn: 'NR — Not Rated' },
]

export function getAgeRatingLabel(val?: string, locale: 'vi' | 'en' = 'vi'): string {
  if (!val) return ''
  const trimmed = val.trim().toUpperCase()
  const found = CANONICAL_AGE_RATINGS.find((r) => r.value.toUpperCase() === trimmed)
  if (found) {
    return locale === 'en' ? found.labelEn : found.labelVi
  }
  return val
}

