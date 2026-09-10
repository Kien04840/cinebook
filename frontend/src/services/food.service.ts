import apiClient from './api'
import type {
  FoodItemResponse,
  CreateFoodItemRequest,
  UpdateFoodItemRequest,
  FoodItemStatus,
} from '@/types/food.types'

export const foodService = {
  /**
   * Lấy danh sách món ăn / thức uống đang hoạt động (Public endpoint cho khách đặt vé)
   */
  async getActiveFoods(): Promise<FoodItemResponse[]> {
    const response = await apiClient.get<FoodItemResponse[]>('/api/v1/foods')
    return response.data
  },

  /**
   * Lấy danh sách toàn bộ món ăn / thức uống dành cho quản trị viên (Admin endpoint)
   * Hỗ trợ tìm kiếm theo tên và lọc theo trạng thái
   */
  async getAdminFoods(params?: {
    search?: string
    status?: FoodItemStatus
  }): Promise<FoodItemResponse[]> {
    const response = await apiClient.get<FoodItemResponse[]>('/api/v1/admin/foods', { params })
    return response.data
  },

  /**
   * Thêm mới món ăn / thức uống (Admin)
   */
  async createFood(payload: CreateFoodItemRequest): Promise<FoodItemResponse> {
    const response = await apiClient.post<FoodItemResponse>('/api/v1/admin/foods', payload)
    return response.data
  },

  /**
   * Cập nhật thông tin món ăn / thức uống (Admin)
   */
  async updateFood(id: string, payload: UpdateFoodItemRequest): Promise<FoodItemResponse> {
    const response = await apiClient.put<FoodItemResponse>(`/api/v1/admin/foods/${id}`, payload)
    return response.data
  },

  /**
   * Xóa mềm món ăn / thức uống (Admin)
   */
  async deleteFood(id: string): Promise<void> {
    await apiClient.delete(`/api/v1/admin/foods/${id}`)
  },
}

export default foodService

