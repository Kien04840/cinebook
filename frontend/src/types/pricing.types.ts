export type DayOfWeekEnum = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface DayPricingRuleResponse {
  id: string
  dayOfWeek: DayOfWeekEnum
  dayOfWeekName?: string
  modifier: number
  updatedAt?: string
}

export interface UpdateDayPricingRuleRequest {
  modifier: number
}

export interface TimeSlotPricingRuleResponse {
  id: string
  name?: string
  startTime: string
  endTime: string
  modifier: number
  updatedAt?: string
}

export interface CreateTimeSlotPricingRuleRequest {
  startTime: string
  endTime: string
  modifier: number
  name?: string
}

export interface UpdateTimeSlotPricingRuleRequest {
  name?: string
  startTime?: string
  endTime?: string
  modifier?: number
}

export interface TicketPricingBreakdown {
  basePrice: number
  seatTypeModifier: number
  dayModifier: number
  dayOfWeek?: string
  timeSlotModifier: number
  timeSlotRange?: string
  finalPrice: number
}
