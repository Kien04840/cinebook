package com.cinebook.controller;

import com.cinebook.dto.response.TicketCheckInResponse;
import com.cinebook.dto.response.TicketVerifyResponse;
import com.cinebook.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Box Office & Tickets", description = "Box Office ticket verification and check-in endpoints")
@RestController
@RequestMapping("/api/v1/admin/tickets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTicketController {

    private final TicketService ticketService;

    @Operation(
            summary = "Verify ticket details and check-in eligibility by Ticket ID or QR Code",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/verify")
    public ResponseEntity<TicketVerifyResponse> verifyTicket(
            @RequestParam(name = "code") String code
    ) {
        TicketVerifyResponse response = ticketService.verifyTicket(code);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Perform atomic check-in for a ticket (VALID -> USED)",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/{ticketId}/check-in")
    public ResponseEntity<TicketCheckInResponse> checkInTicket(
            @PathVariable String ticketId
    ) {
        TicketCheckInResponse response = ticketService.checkInTicket(ticketId);
        return ResponseEntity.ok(response);
    }
}

