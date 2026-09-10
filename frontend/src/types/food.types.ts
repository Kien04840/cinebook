export type FoodItemStatus = 'ACTIVE' | 'INACTIVE'

export interface FoodItemResponse {
  id: string
  name: string
  description?: string
  price: number
  imageUrl?: string
  status: FoodItemStatus
  createdAt: string
  updatedAt: string
}

export interface CreateFoodItemRequest {
  name: string
  description?: string
  price: number
  imageUrl?: string
  status?: FoodItemStatus
}

export interface UpdateFoodItemRequest {
  name?: string
  description?: string
  price?: number
  imageUrl?: string
  status?: FoodItemStatus
}

export interface BookingFoodItemRequest {
  foodItemId: string
  quantity: number
}

export interface BookingFoodResponse {
  id: string
  foodItemId: string
  foodName: string
  unitPrice: number
  quantity: number
  subtotal: number
}

