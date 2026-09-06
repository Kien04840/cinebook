export type SeatTypeStatus = 'ACTIVE' | 'INACTIVE'

export interface SeatTypeResponse {
  id: string
  code: string
  name: string
  priceModifier: number
  capacity: number
  colorToken?: string
  icon?: string
  description?: string
  status: SeatTypeStatus
  createdAt: string
  updatedAt: string
}

export interface CreateSeatTypeRequest {
  code: string
  name: string
  priceModifier: number
  capacity: number
  colorToken?: string
  icon?: string
  description?: string
  status?: SeatTypeStatus
}

export interface UpdateSeatTypeRequest {
  code: string
  name: string
  priceModifier: number
  capacity: number
  colorToken?: string
  icon?: string
  description?: string
  status?: SeatTypeStatus
}

