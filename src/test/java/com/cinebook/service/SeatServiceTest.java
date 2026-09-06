package com.cinebook.service;

import com.cinebook.dto.response.SeatResponse;
import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Seat;
import com.cinebook.entity.SeatType;
import com.cinebook.enums.SeatStatus;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.SeatMapper;
import com.cinebook.repository.AuditoriumRepository;
import com.cinebook.repository.SeatRepository;
import com.cinebook.repository.SeatTypeRepository;
import com.cinebook.service.impl.SeatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
    void updateSeatType_AssignCoupleOverlapsAnotherSeat_ThrowsConflict() {
        SeatType coupleType = new SeatType();
        coupleType.setId("st-couple");
        coupleType.setCode("COUPLE");
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

        when(seatRepository.findById("seat-e1")).thenReturn(Optional.of(e1));
        when(seatTypeRepository.findById("st-couple")).thenReturn(Optional.of(coupleType));
        when(seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc("aud-1", "E"))
                .thenReturn(List.of(e1, e2));

        assertThrows(com.cinebook.exception.ConflictException.class, () ->
                seatService.updateSeatType("seat-e1", "st-couple"));
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
        when(seatRepository.save(any(Seat.class))).thenAnswer(inv -> inv.getArgument(0));

        SeatResponse result = seatService.updateSeatStatus("seat-1", SeatStatus.BROKEN);

        assertNotNull(result);
        assertEquals(SeatStatus.BROKEN, result.getStatus());
    }
}