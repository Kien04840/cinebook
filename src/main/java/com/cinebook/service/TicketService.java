package com.cinebook.service;

import com.cinebook.dto.response.TicketCheckInResponse;
import com.cinebook.dto.response.TicketVerifyResponse;

public interface TicketService {

    TicketVerifyResponse verifyTicket(String codeOrId);

    TicketCheckInResponse checkInTicket(String ticketId);
}

