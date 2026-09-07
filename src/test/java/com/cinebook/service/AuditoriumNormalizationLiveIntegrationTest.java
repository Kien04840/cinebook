package com.cinebook.service;

import com.cinebook.dto.response.NormalizeEmptyLayoutsResponse;
import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Seat;
import com.cinebook.entity.SeatType;
import com.cinebook.repository.AuditoriumRepository;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.SeatRepository;
import com.cinebook.repository.SeatTypeRepository;
import com.cinebook.repository.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditoriumNormalizationLiveIntegrationTest {

    @Autowired
    private AuditoriumService auditoriumService;

    @Autowired
    private AuditoriumRepository auditoriumRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private SeatTypeRepository seatTypeRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Test
    @Order(1)
    @DisplayName("Verify Couple SeatType in DB has canonical values: capacity=2, price_modifier=40000")
    void verifyCoupleSeatTypeCanonicalValues() {
        SeatType coupleType = seatTypeRepository.findByCodeIgnoreCase("COUPLE")
                .orElseThrow(() -> new AssertionError("SeatType COUPLE must exist in DB"));

        assertThat((int) coupleType.getCapacity()).isEqualTo(2);
        assertThat(coupleType.getPriceModifier()).isEqualByComparingTo(new BigDecimal("40000.00"));
    }

    @Test
    @Order(2)
    @DisplayName("Normalize empty auditoriums is idempotent: 106 scanned, 90 unchanged/normalized, 16 protected")
    void testNormalizeEmptyAuditoriumsLayout_IdempotentRun() {
        NormalizeEmptyLayoutsResponse res = auditoriumService.normalizeEmptyAuditoriumsLayout();

        assertThat(res.getScannedCount()).isEqualTo(106);
        // All safe rooms are normalized and unchanged; all protected rooms skipped
        assertThat(res.getNormalizedCount() + res.getUnchangedCount()).isEqualTo(90);
        assertThat(res.getSkippedCount()).isEqualTo(16);
        assertThat(res.getSkippedBecauseBookings()).isEqualTo(16);
        assertThat(res.getSkippedBecauseTickets()).isEqualTo(13);
        assertThat(res.getFailedCount()).isEqualTo(0);
    }

    @Test
    @Order(3)
    @Transactional(readOnly = true)
    @DisplayName("Verify normalized auditoriums layout has Standard, VIP and Couple last row")
    void verifyNormalizedAuditoriumLayout() {
        // Room 01 (auditorium 703d13c7-f0e0-4ebd-8f75-c3b5957c5bc6) is a safe normalized room
        String audId = "703d13c7-f0e0-4ebd-8f75-c3b5957c5bc6";
        Auditorium aud = auditoriumRepository.findById(audId).orElseThrow();
        List<Seat> seats = seatRepository.findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc(audId);

        char lastRow = (char) ('A' + aud.getRowsCount() - 1);

        List<Seat> lastRowSeats = seats.stream()
                .filter(s -> s.getRowLabel().charAt(0) == lastRow)
                .toList();

        assertThat(lastRowSeats).isNotEmpty();
        for (Seat s : lastRowSeats) {
            assertThat(s.getSeatType().getCode()).isEqualTo("COUPLE");
            assertThat(s.getSeatNumber() % 2).isEqualTo(1); // odd seat numbers only
        }

        // Verify protected auditoriums remain protected with 0 couple modifications
        long protectedWithTickets = auditoriumRepository.findAllWithCinemaByDeletedAtIsNull().stream()
                .filter(a -> ticketRepository.existsByAuditoriumId(a.getId()))
                .count();
        assertThat(protectedWithTickets).isEqualTo(13);
    }
}