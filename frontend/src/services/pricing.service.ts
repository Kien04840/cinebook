import apiClient from './api'
import type {
  DayPricingRuleResponse,
  UpdateDayPricingRuleRequest,
  TimeSlotPricingRuleResponse,
  CreateTimeSlotPricingRuleRequest,
  UpdateTimeSlotPricingRuleRequest,
} from '@/types/pricing.types'

export const pricingService = {
  async getDayPricingRules(): Promise<DayPricingRuleResponse[]> {
    const response = await apiClient.get<DayPricingRuleResponse[]>('/api/v1/admin/pricing/day-rules')
    return response.data
  },

  async updateDayPricingRule(id: string, payload: UpdateDayPricingRuleRequest): Promise<DayPricingRuleResponse> {
    const response = await apiClient.put<DayPricingRuleResponse>('/api/v1/admin/pricing/day-rules/' + id, payload)
    return response.data
  },

  async getTimeSlotPricingRules(): Promise<TimeSlotPricingRuleResponse[]> {
    const response = await apiClient.get<TimeSlotPricingRuleResponse[]>('/api/v1/admin/pricing/time-slots')
    return response.data
  },

  async createTimeSlotPricingRule(payload: CreateTimeSlotPricingRuleRequest): Promise<TimeSlotPricingRuleResponse> {
    const response = await apiClient.post<TimeSlotPricingRuleResponse>('/api/v1/admin/pricing/time-slots', payload)
    return response.data
  },

  async updateTimeSlotPricingRule(id: string, payload: UpdateTimeSlotPricingRuleRequest): Promise<TimeSlotPricingRuleResponse> {
    const response = await apiClient.put<TimeSlotPricingRuleResponse>('/api/v1/admin/pricing/time-slots/' + id, payload)
    return response.data
  },

  async deleteTimeSlotPricingRule(id: string): Promise<void> {
    await apiClient.delete('/api/v1/admin/pricing/time-slots/' + id)
  },
}

export default pricingService
