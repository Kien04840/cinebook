import api from './api'
import type { TicketVerifyResponse, TicketCheckInResponse } from '@/types/ticket.types'

export const ticketService = {
  async verifyTicket(code: string): Promise<TicketVerifyResponse> {
    const response = await api.get<TicketVerifyResponse>('/api/v1/admin/tickets/verify', {
      params: { code: code.trim() },
    })
    return response.data
  },

  async checkInTicket(ticketId: string): Promise<TicketCheckInResponse> {
    const response = await api.post<TicketCheckInResponse>(`/api/v1/admin/tickets/${ticketId}/check-in`)
    return response.data
  },
}

export default ticketService


