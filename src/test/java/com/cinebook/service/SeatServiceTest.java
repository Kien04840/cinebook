package com.cinebook.service;

import com.cinebook.dto.response.SeatResponse;
import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Seat;
import com.cinebook.entity.SeatType;
import com.cinebook.enums.SeatStatus;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.SeatMapper;
import com.cinebook.repository.AuditoriumRepository;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.SeatHoldRepository;
import com.cinebook.repository.SeatRepository;
import com.cinebook.repository.SeatTypeRepository;
import com.cinebook.repository.TicketRepository;
import com.cinebook.service.impl.SeatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private AuditoriumRepository auditoriumRepository;

    @Mock
    private SeatTypeRepository seatTypeRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private SeatHoldRepository seatHoldRepository;

    @Spy
    private SeatMapper seatMapper = new SeatMapper();

    @InjectMocks
    private SeatServiceImpl seatService;

    private Auditorium sampleAuditorium;
    private SeatType stdType;
    private SeatType vipType;
    private Seat sampleSeat;

    @BeforeEach
    void setUp() {
        sampleAuditorium = new Auditorium();
        sampleAuditorium.setId("aud-1");
        sampleAuditorium.setRowsCount((short) 5);
        sampleAuditorium.setColumnsCount((short) 8);

        stdType = new SeatType();
        stdType.setId("st-std");
        stdType.setCode("STANDARD");
        stdType.setName("STANDARD");
        stdType.setCapacity((short) 1);
        stdType.setPriceModifier(BigDecimal.ZERO);

        vipType = new SeatType();
        vipType.setId("st-vip");
        vipType.setCode("VIP");
        vipType.setName("VIP");
        vipType.setCapacity((short) 1);
        vipType.setPriceModifier(new BigDecimal("25000.00"));

        sampleSeat = new Seat();
        sampleSeat.setId("seat-1");
        sampleSeat.setAuditorium(sampleAuditorium);
        sampleSeat.setSeatType(stdType);
        sampleSeat.setRowLabel("A");
        sampleSeat.setSeatNumber((short) 1);
        sampleSeat.setStatus(SeatStatus.ACTIVE);
    }

    @Test
    void getSeatsByAuditorium_Success() {
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));
        when(seatRepository.findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc("aud-1")).thenReturn(List.of(sampleSeat));

        List<SeatResponse> result = seatService.getSeatsByAuditorium("aud-1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("A1", result.get(0).getSeatCode());
    }

    @Test
    void updateSeatType_Success() {
        when(seatRepository.findById("seat-1")).thenReturn(Optional.of(sampleSeat));
        when(seatTypeRepository.findById("st-vip")).thenReturn(Optional.of(vipType));
        when(seatRepository.save(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));

        SeatResponse result = seatService.updateSeatType("seat-1", "st-vip");

        assertNotNull(result);
        assertEquals("VIP", result.getSeatTypeName());
        assertEquals(new BigDecimal("25000.00"), result.getPriceModifier());
    }

    @Test
    void updateSeatType_AssignCoupleToLastRow_Success() {
        SeatType coupleType = new SeatType();
        coupleType.setId("st-couple");
        coupleType.setCode("COUPLE");
        coupleType.setName("Couple");
        coupleType.setCapacity((short) 2);

        Seat lastRowSeat = new Seat();
        lastRowSeat.setId("seat-e1");
        lastRowSeat.setAuditorium(sampleAuditorium);
        lastRowSeat.setSeatType(stdType);
        lastRowSeat.setRowLabel("E");
        lastRowSeat.setSeatNumber((short) 1);
        lastRowSeat.setStatus(SeatStatus.ACTIVE);

        when(seatRepository.findById("seat-e1")).thenReturn(Optional.of(lastRowSeat));
        when(seatTypeRepository.findById("st-couple")).thenReturn(Optional.of(coupleType));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "E"))
                .thenReturn(List.of(lastRowSeat));
        when(seatRepository.save(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));

        SeatResponse result = seatService.updateSeatType("seat-e1", "st-couple");

        assertNotNull(result);
        assertEquals("Couple", result.getSeatTypeName());
        assertEquals("COUPLE", result.getSeatTypeCode());
        assertEquals((short) 2, result.getCapacity());
    }

    @Test
    void updateSeatType_AssignCoupleNotLastRow_ThrowsConflict() {
        SeatType coupleType = new SeatType();
        coupleType.setId("st-couple");
        coupleType.setCode("COUPLE");
        coupleType.setCapacity((short) 2);

        when(seatRepository.findById("seat-1")).thenReturn(Optional.of(sampleSeat)); // Row A
        when(seatTypeRepository.findById("st-couple")).thenReturn(Optional.of(coupleType));

        assertThrows(com.cinebook.exception.ConflictException.class, () ->
                seatService.updateSeatType("seat-1", "st-couple"));
    }

    @Test
    void updateSeatType_AssignCoupleExceedsColumns_ThrowsConflict() {
        SeatType coupleType = new SeatType();
        coupleType.setId("st-couple");
        coupleType.setCode("COUPLE");
        coupleType.setCapacity((short) 2);

        Seat edgeSeat = new Seat();
        edgeSeat.setId("seat-e8");
        edgeSeat.setAuditorium(sampleAuditorium);
        edgeSeat.setRowLabel("E");
        edgeSeat.setSeatNumber((short) 8); // 8 + 2 - 1 = 9 > 8 columnsCount

        when(seatRepository.findById("seat-e8")).thenReturn(Optional.of(edgeSeat));
        when(seatTypeRepository.findById("st-couple")).thenReturn(Optional.of(coupleType));

        assertThrows(com.cinebook.exception.ConflictException.class, () ->
                seatService.updateSeatType("seat-e8", "st-couple"));
    }

    @Test
    void updateSeatType_AssignCoupleOverlapsAnotherSeat_AtomicallyDeletesUnreferencedOverlap() {
        SeatType coupleType = new SeatType();
        coupleType.setId("st-couple");
        coupleType.setCode("COUPLE");
        coupleType.setName("Couple");
        coupleType.setCapacity((short) 2);

        Seat e1 = new Seat();
        e1.setId("seat-e1");
        e1.setAuditorium(sampleAuditorium);
        e1.setRowLabel("E");
        e1.setSeatNumber((short) 1);
        e1.setStatus(SeatStatus.ACTIVE);

        Seat e2 = new Seat();
        e2.setId("seat-e2");
        e2.setAuditorium(sampleAuditorium);
        e2.setRowLabel("E");
        e2.setSeatNumber((short) 2);
        e2.setStatus(SeatStatus.ACTIVE);

        when(seatRepository.findById("seat-e1")).thenReturn(Optional.of(e1));
        when(seatTypeRepository.findById("st-couple")).thenReturn(Optional.of(coupleType));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "E"))
                .thenReturn(List.of(e1, e2));
        when(seatRepository.save(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));

        SeatResponse result = seatService.updateSeatType("seat-e1", "st-couple");

        assertNotNull(result);
        assertEquals("COUPLE", result.getSeatTypeCode());
        assertEquals((short) 2, result.getCapacity());
        verify(seatRepository).deleteAll(List.of(e2));
    }

    @Test
    void batchUpdateSeatType_AssignCouple_MultipleTargetSeatsCollide_ThrowsConflict() {
        SeatType coupleType = new SeatType();
        coupleType.setId("st-couple");
        coupleType.setCode("COUPLE");
        coupleType.setName("Couple");
        coupleType.setCapacity((short) 2);

        Seat e1 = new Seat();
        e1.setId("seat-e1");
        e1.setAuditorium(sampleAuditorium);
        e1.setRowLabel("E");
        e1.setSeatNumber((short) 1);

        Seat e2 = new Seat();
        e2.setId("seat-e2");
        e2.setAuditorium(sampleAuditorium);
        e2.setRowLabel("E");
        e2.setSeatNumber((short) 2);

        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));
        when(seatTypeRepository.findById("st-couple")).thenReturn(Optional.of(coupleType));
        when(seatRepository.findAllById(List.of("seat-e1", "seat-e2"))).thenReturn(List.of(e1, e2));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                seatService.batchUpdateSeatType("aud-1", List.of("seat-e1", "seat-e2"), "st-couple"));
        assertTrue(ex.getMessage().contains("cột lẻ") || ex.getMessage().contains("bị chiếm dụng"));
    }

    @Test
    void previewBatchUpdateSeatType_IdentifiesDeletedSeatCodes_Success() {
        SeatType coupleType = new SeatType();
        coupleType.setId("st-couple");
        coupleType.setCode("COUPLE");
        coupleType.setName("Couple");
        coupleType.setCapacity((short) 2);

        Seat e1 = new Seat();
        e1.setId("seat-e1");
        e1.setAuditorium(sampleAuditorium);
        e1.setRowLabel("E");
        e1.setSeatNumber((short) 1);

        Seat e2 = new Seat();
        e2.setId("seat-e2");
        e2.setAuditorium(sampleAuditorium);
        e2.setRowLabel("E");
        e2.setSeatNumber((short) 2);

        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));
        when(seatTypeRepository.findById("st-couple")).thenReturn(Optional.of(coupleType));
        when(seatRepository.findAllById(List.of("seat-e1"))).thenReturn(List.of(e1));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "E")).thenReturn(List.of(e1, e2));

        com.cinebook.dto.response.BatchUpdateSeatTypePreviewResponse preview =
                seatService.previewBatchUpdateSeatType("aud-1", List.of("seat-e1"), "st-couple");

        assertTrue(preview.isValid());
        assertEquals(List.of("E2"), preview.getWillDeleteSeatCodes());
        assertEquals(1, preview.getSeatCount());
        assertFalse(preview.isProtected());
    }

    @Test
    void batchUpdateSeatStatus_Success() {
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));
        when(seatRepository.findAllById(List.of("seat-1"))).thenReturn(List.of(sampleSeat));
        when(seatRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<SeatResponse> result = seatService.batchUpdateSeatStatus("aud-1", List.of("seat-1"), SeatStatus.BROKEN);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(SeatStatus.BROKEN, result.get(0).getStatus());
    }

    @Test
    void batchUpdateSeatType_Success() {
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));
        when(seatTypeRepository.findById("st-vip")).thenReturn(Optional.of(vipType));
        when(seatRepository.findAllById(List.of("seat-1"))).thenReturn(List.of(sampleSeat));
        when(seatRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<SeatResponse> result = seatService.batchUpdateSeatType("aud-1", List.of("seat-1"), "st-vip");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("VIP", result.get(0).getSeatTypeName());
    }

    @Test
    void updateSeatStatus_Success() {
        when(seatRepository.findById("seat-1")).thenReturn(Optional.of(sampleSeat));
        when(seatHoldRepository.existsActiveHoldBySeatId(eq("seat-1"), any(LocalDateTime.class))).thenReturn(false);
        when(ticketRepository.existsUpcomingValidTicketsBySeatId(eq("seat-1"), any(LocalDateTime.class))).thenReturn(false);
        when(seatRepository.save(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));

        SeatResponse result = seatService.updateSeatStatus("seat-1", SeatStatus.BROKEN);

        assertNotNull(result);
        assertEquals(SeatStatus.BROKEN, result.getStatus());
    }

    @Test
    void updateSeatType_WhenAuditoriumHasBookings_ThrowsConflict() {
        when(seatRepository.findById("seat-1")).thenReturn(Optional.of(sampleSeat));
        when(seatTypeRepository.findById("st-vip")).thenReturn(Optional.of(vipType));
        when(bookingRepository.existsByAuditoriumId("aud-1")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                seatService.updateSeatType("seat-1", "st-vip"));
        assertTrue(ex.getMessage().contains("Không thể thay đổi loại ghế"));
    }

    @Test
    void updateSeatType_WhenAuditoriumHasTickets_ThrowsConflict() {
        when(seatRepository.findById("seat-1")).thenReturn(Optional.of(sampleSeat));
        when(seatTypeRepository.findById("st-vip")).thenReturn(Optional.of(vipType));
        when(bookingRepository.existsByAuditoriumId("aud-1")).thenReturn(false);
        when(ticketRepository.existsByAuditoriumId("aud-1")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                seatService.updateSeatType("seat-1", "st-vip"));
        assertTrue(ex.getMessage().contains("Không thể thay đổi loại ghế"));
    }

    @Test
    void batchUpdateSeatType_WhenAuditoriumHasBookings_ThrowsConflict() {
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));
        when(bookingRepository.existsByAuditoriumId("aud-1")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                seatService.batchUpdateSeatType("aud-1", List.of("seat-1"), "st-vip"));
        assertTrue(ex.getMessage().contains("Không thể thay đổi loại ghế"));
    }

    @Test
    void updateSeatStatus_WhenSeatHasActiveHold_ThrowsConflict() {
        when(seatRepository.findById("seat-1")).thenReturn(Optional.of(sampleSeat));
        when(seatHoldRepository.existsActiveHoldBySeatId(eq("seat-1"), any(LocalDateTime.class))).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                seatService.updateSeatStatus("seat-1", SeatStatus.BROKEN));
        assertTrue(ex.getMessage().contains("đang được giữ chỗ"));
    }

    @Test
    void updateSeatStatus_WhenSeatHasUpcomingValidTicket_ThrowsConflict() {
        when(seatRepository.findById("seat-1")).thenReturn(Optional.of(sampleSeat));
        when(seatHoldRepository.existsActiveHoldBySeatId(eq("seat-1"), any(LocalDateTime.class))).thenReturn(false);
        when(ticketRepository.existsUpcomingValidTicketsBySeatId(eq("seat-1"), any(LocalDateTime.class))).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                seatService.updateSeatStatus("seat-1", SeatStatus.BROKEN));
        assertTrue(ex.getMessage().contains("đã có vé đặt cho suất chiếu sắp tới"));
    }

    @Test
    void updateSeatStatus_FromBrokenToActive_AlwaysSucceeds() {
        sampleSeat.setStatus(SeatStatus.BROKEN);
        when(seatRepository.findById("seat-1")).thenReturn(Optional.of(sampleSeat));
        when(seatRepository.save(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));

        SeatResponse result = seatService.updateSeatStatus("seat-1", SeatStatus.ACTIVE);

        assertNotNull(result);
        assertEquals(SeatStatus.ACTIVE, result.getStatus());
        verifyNoInteractions(seatHoldRepository);
        verifyNoInteractions(ticketRepository);
    }
}