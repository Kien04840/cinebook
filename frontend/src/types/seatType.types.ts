export type SeatTypeStatus = 'ACTIVE' | 'INACTIVE'

export interface SeatTypeResponse {
  id: string
  name: string
  priceModifier: number
  description?: string
  status: SeatTypeStatus
  createdAt: string
  updatedAt: string
}

export interface CreateSeatTypeRequest {
  name: string
  priceModifier: number
  description?: string
}

export interface UpdateSeatTypeRequest {
  name: string
  priceModifier: number
  description?: string
}

