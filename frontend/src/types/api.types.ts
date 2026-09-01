export interface FieldErrorDetail {
  field: string
  message: string
}

export interface ApiError {
  status: number
  error?: string
  message: string
  path?: string
  timestamp?: string
  details?: FieldErrorDetail[]
}

export interface MessageResponse {
  message: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last?: boolean
}
