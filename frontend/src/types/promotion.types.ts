export type PromotionDiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT'
export type PromotionStatus = 'ACTIVE' | 'INACTIVE' | 'EXPIRED'

export interface ValidatePromotionResponse {
  valid: boolean
  code: string
  name: string
  discountType: PromotionDiscountType
  discountValue: number
  grossAmount: number
  discountAmount: number
  finalAmount: number
  message: string
}

export interface PromotionResponse {
  id: string
  code: string
  name: string
  description?: string
  discountType: PromotionDiscountType
  discountValue: number
  minOrderAmount?: number
  maxDiscountAmount?: number
  startAt: string
  endAt: string
  usageLimit?: number
  usedCount: number
  remainingUses?: number
  status: PromotionStatus
  createdAt: string
  updatedAt: string
}

export interface CreatePromotionRequest {
  code: string
  name: string
  description?: string
  discountType: PromotionDiscountType
  discountValue: number
  minOrderAmount?: number
  maxDiscountAmount?: number
  startAt: string
  endAt: string
  usageLimit?: number
  status?: PromotionStatus
}

export interface UpdatePromotionRequest {
  name: string
  description?: string
  minOrderAmount?: number
  maxDiscountAmount?: number
  startAt: string
  endAt: string
  usageLimit?: number
}

export interface UpdatePromotionStatusRequest {
  status: PromotionStatus
}

