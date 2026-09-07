package com.cinebook.service;

import com.cinebook.dto.request.CreateAuditoriumRequest;
import com.cinebook.dto.request.UpdateAuditoriumRequest;
import com.cinebook.dto.response.AuditoriumDetailResponse;
import com.cinebook.dto.response.AuditoriumResponse;
import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Cinema;
import com.cinebook.entity.SeatType;
import com.cinebook.enums.AuditoriumStatus;
import com.cinebook.enums.CinemaStatus;
import com.cinebook.enums.SeatTypeStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.dto.response.NormalizeEmptyLayoutsResponse;
import com.cinebook.mapper.AuditoriumMapper;
import com.cinebook.mapper.SeatMapper;
import com.cinebook.repository.AuditoriumRepository;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.CinemaRepository;
import com.cinebook.repository.SeatHoldRepository;
import com.cinebook.repository.SeatRepository;
import com.cinebook.repository.SeatTypeRepository;
import com.cinebook.repository.ShowtimeRepository;
import com.cinebook.repository.TicketRepository;
import com.cinebook.service.impl.AuditoriumServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditoriumServiceTest {

    @Mock
    private AuditoriumRepository auditoriumRepository;

    @Mock
    private CinemaRepository cinemaRepository;

    @Mock
    private SeatTypeService seatTypeService;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private ShowtimeRepository showtimeRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private SeatHoldRepository seatHoldRepository;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Spy
    private AuditoriumMapper auditoriumMapper = new AuditoriumMapper(new SeatMapper());

    @InjectMocks
    private AuditoriumServiceImpl auditoriumService;

    private Cinema sampleCinema;
    private Auditorium sampleAuditorium;
    private SeatType standardSeatType;
    private SeatType vipSeatType;
    private SeatType coupleSeatType;

    @BeforeEach
    void setUp() {
        sampleCinema = new Cinema();
        sampleCinema.setId("cin-1");
        sampleCinema.setName("CineBook Hanoi Center");
        sampleCinema.setStatus(CinemaStatus.ACTIVE);

        standardSeatType = new SeatType();
        standardSeatType.setId("st-std");
        standardSeatType.setCode("STANDARD");
        standardSeatType.setName("STANDARD");
        standardSeatType.setPriceModifier(BigDecimal.ZERO);
        standardSeatType.setStatus(SeatTypeStatus.ACTIVE);

        vipSeatType = new SeatType();
        vipSeatType.setId("st-vip");
        vipSeatType.setCode("VIP");
        vipSeatType.setName("VIP");
        vipSeatType.setPriceModifier(new BigDecimal("20000.00"));
        vipSeatType.setStatus(SeatTypeStatus.ACTIVE);

        coupleSeatType = new SeatType();
        coupleSeatType.setId("st-couple");
        coupleSeatType.setCode("COUPLE");
        coupleSeatType.setName("Couple");
        coupleSeatType.setCapacity((short) 2);
        coupleSeatType.setPriceModifier(new BigDecimal("50000.00"));
        coupleSeatType.setStatus(SeatTypeStatus.ACTIVE);

        sampleAuditorium = new Auditorium();
        sampleAuditorium.setId("aud-1");
        sampleAuditorium.setCinema(sampleCinema);
        sampleAuditorium.setName("Hall 1");
        sampleAuditorium.setType("STANDARD");
        sampleAuditorium.setRowsCount((short) 10);
        sampleAuditorium.setColumnsCount((short) 12);
        sampleAuditorium.setStatus(AuditoriumStatus.ACTIVE);
        sampleAuditorium.setTurnaroundMinutes((short) 15);
        sampleAuditorium.setSnapIntervalMinutes((short) 15);
        sampleAuditorium.setSeats(new HashSet<>());
    }

    @Test
    void getAuditoriumsByCinema_Success() {
        when(cinemaRepository.findByIdAndDeletedAtIsNull("cin-1")).thenReturn(Optional.of(sampleCinema));
        when(auditoriumRepository.findByCinemaIdAndDeletedAtIsNull("cin-1")).thenReturn(List.of(sampleAuditorium));

        List<AuditoriumResponse> result = auditoriumService.getAuditoriumsByCinema("cin-1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Hall 1", result.get(0).getName());
    }

    @Test
    void createAuditorium_Success_WithSeatMatrixGeneration() {
        CreateAuditoriumRequest request = CreateAuditoriumRequest.builder()
                .name("Hall 2")
                .type("VIP")
                .rowsCount((short) 5)
                .columnsCount((short) 8)
                .status(AuditoriumStatus.ACTIVE)
                .turnaroundMinutes((short) 20)
                .snapIntervalMinutes((short) 10)
                .build();

        when(cinemaRepository.findByIdAndDeletedAtIsNull("cin-1")).thenReturn(Optional.of(sampleCinema));
        when(auditoriumRepository.existsByCinemaIdAndNameAndDeletedAtIsNull("cin-1", "Hall 2")).thenReturn(false);
        when(seatTypeService.getOrCreateDefaultSeatType(null)).thenReturn(standardSeatType);
        when(seatTypeService.getOrCreateVipSeatType()).thenReturn(vipSeatType);
        when(seatTypeService.getOrCreateCoupleSeatType()).thenReturn(coupleSeatType);
        when(auditoriumRepository.save(any(Auditorium.class))).thenAnswer(inv -> {
            Auditorium a = inv.getArgument(0);
            a.setId("aud-new");
            return a;
        });

        AuditoriumDetailResponse result = auditoriumService.createAuditorium("cin-1", request);

        assertNotNull(result);
        assertEquals("Hall 2", result.getName());
        assertEquals(36, result.getTotalSeats()); // 4 rows * 8 (Standard/VIP) + 4 couple seats = 36 seats
        assertEquals(36, result.getSeats().size());
        assertEquals("A1", result.getSeats().get(0).getSeatCode());
        assertEquals((short) 20, result.getTurnaroundMinutes());
        assertEquals((short) 10, result.getSnapIntervalMinutes());
        verify(auditoriumRepository).save(any(Auditorium.class));
    }

    @Test
    void resetAuditoriumLayout_Success() {
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));
        when(bookingRepository.existsByAuditoriumId("aud-1")).thenReturn(false);
        when(ticketRepository.existsByAuditoriumId("aud-1")).thenReturn(false);
        when(seatHoldRepository.existsActiveHoldByAuditoriumId(eq("aud-1"), any(LocalDateTime.class))).thenReturn(false);
        when(seatTypeService.getOrCreateDefaultSeatType(null)).thenReturn(standardSeatType);
        when(seatTypeService.getOrCreateVipSeatType()).thenReturn(vipSeatType);
        when(seatTypeService.getOrCreateCoupleSeatType()).thenReturn(coupleSeatType);
        when(seatRepository.findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc("aud-1")).thenReturn(List.of());

        AuditoriumDetailResponse result = auditoriumService.resetAuditoriumLayout("aud-1");

        assertNotNull(result);
        verify(seatRepository).saveAll(anyCollection());
    }

    @Test
    void resetAuditoriumLayout_WhenHasBookings_ThrowsConflict() {
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));
        when(bookingRepository.existsByAuditoriumId("aud-1")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                auditoriumService.resetAuditoriumLayout("aud-1"));
        assertTrue(ex.getMessage().contains("giao dịch đặt vé"));
        verify(seatRepository, never()).deleteAll(any());
    }

    @Test
    void resetAuditoriumLayout_WhenHasTickets_ThrowsConflict() {
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));
        when(bookingRepository.existsByAuditoriumId("aud-1")).thenReturn(false);
        when(ticketRepository.existsByAuditoriumId("aud-1")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                auditoriumService.resetAuditoriumLayout("aud-1"));
        assertTrue(ex.getMessage().contains("giao dịch đặt vé"));
        verify(seatRepository, never()).deleteAll(any());
    }

    @Test
    void normalizeEmptyAuditoriumsLayout_SkipsAuditoriumsWithData() {
        Auditorium emptyAud = new Auditorium();
        emptyAud.setId("aud-empty");
        emptyAud.setName("Empty Hall");
        emptyAud.setRowsCount((short) 5);
        emptyAud.setColumnsCount((short) 6);
        emptyAud.setCinema(sampleCinema);

        Auditorium busyAud = new Auditorium();
        busyAud.setId("aud-busy");
        busyAud.setName("Busy Hall");
        busyAud.setRowsCount((short) 5);
        busyAud.setColumnsCount((short) 6);
        busyAud.setCinema(sampleCinema);

        when(auditoriumRepository.findByDeletedAtIsNull()).thenReturn(List.of(emptyAud, busyAud));
        when(bookingRepository.existsByAuditoriumId("aud-empty")).thenReturn(false);
        when(ticketRepository.existsByAuditoriumId("aud-empty")).thenReturn(false);
        when(seatHoldRepository.existsActiveHoldByAuditoriumId(eq("aud-empty"), any(LocalDateTime.class))).thenReturn(false);

        when(bookingRepository.existsByAuditoriumId("aud-busy")).thenReturn(true);

        when(transactionManager.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
        when(auditoriumRepository.findById("aud-empty")).thenReturn(Optional.of(emptyAud));
        when(seatTypeService.getOrCreateDefaultSeatType(null)).thenReturn(standardSeatType);
        when(seatTypeService.getOrCreateVipSeatType()).thenReturn(vipSeatType);
        when(seatTypeService.getOrCreateCoupleSeatType()).thenReturn(coupleSeatType);
        when(seatRepository.findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc("aud-empty")).thenReturn(List.of());

        NormalizeEmptyLayoutsResponse response = auditoriumService.normalizeEmptyAuditoriumsLayout();

        assertNotNull(response);
        assertEquals(2, response.getProcessedCount());
        assertEquals(1, response.getUpdatedCount());
        assertEquals(1, response.getSkippedCount());
        assertEquals(1, response.getSkippedBecauseBookings());
        assertEquals(List.of("aud-empty"), response.getUpdatedAuditoriumIds());
        assertEquals(1, response.getSkippedDetails().size());
        assertEquals("aud-busy", response.getSkippedDetails().get(0).getAuditoriumId());
    }

    @Test
    void createAuditorium_DuplicateName_ThrowsConflict() {
        CreateAuditoriumRequest request = CreateAuditoriumRequest.builder()
                .name("Hall 1")
                .type("STANDARD")
                .rowsCount((short) 5)
                .columnsCount((short) 5)
                .build();

        when(cinemaRepository.findByIdAndDeletedAtIsNull("cin-1")).thenReturn(Optional.of(sampleCinema));
        when(auditoriumRepository.existsByCinemaIdAndNameAndDeletedAtIsNull("cin-1", "Hall 1")).thenReturn(true);

        assertThrows(ConflictException.class, () -> auditoriumService.createAuditorium("cin-1", request));
        verify(auditoriumRepository, never()).save(any(Auditorium.class));
    }

    @Test
    void updateAuditorium_Success() {
        UpdateAuditoriumRequest request = UpdateAuditoriumRequest.builder()
                .name("Hall 1 Renovated")
                .type("VIP")
                .status(AuditoriumStatus.ACTIVE)
                .turnaroundMinutes((short) 25)
                .snapIntervalMinutes((short) 5)
                .build();

        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));
        when(auditoriumRepository.existsByCinemaIdAndNameAndIdNotAndDeletedAtIsNull("cin-1", "Hall 1 Renovated", "aud-1")).thenReturn(false);
        when(auditoriumRepository.save(any(Auditorium.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditoriumResponse result = auditoriumService.updateAuditorium("aud-1", request);

        assertNotNull(result);
        assertEquals("Hall 1 Renovated", result.getName());
        assertEquals("VIP", result.getType());
        assertEquals((short) 25, result.getTurnaroundMinutes());
        assertEquals((short) 5, result.getSnapIntervalMinutes());
    }

    @Test
    void deleteAuditorium_Success_MarksDecommissioned() {
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));

        auditoriumService.deleteAuditorium("aud-1");

        assertNotNull(sampleAuditorium.getDeletedAt());
        assertEquals(AuditoriumStatus.DECOMMISSIONED, sampleAuditorium.getStatus());
        verify(auditoriumRepository).save(sampleAuditorium);
    }

    @Test
    void updateAuditorium_StatusTransitions_ActiveToMaintenanceAndBack() {
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));
        when(auditoriumRepository.existsByCinemaIdAndNameAndIdNotAndDeletedAtIsNull("cin-1", "Hall 1", "aud-1")).thenReturn(false);
        when(auditoriumRepository.save(any(Auditorium.class))).thenAnswer(inv -> inv.getArgument(0));

        // ACTIVE -> MAINTENANCE
        UpdateAuditoriumRequest toMaintenance = UpdateAuditoriumRequest.builder()
                .name("Hall 1")
                .type("STANDARD")
                .status(AuditoriumStatus.MAINTENANCE)
                .build();
        AuditoriumResponse res1 = auditoriumService.updateAuditorium("aud-1", toMaintenance);
        assertEquals(AuditoriumStatus.MAINTENANCE, res1.getStatus());

        // MAINTENANCE -> ACTIVE
        UpdateAuditoriumRequest toActive = UpdateAuditoriumRequest.builder()
                .name("Hall 1")
                .type("STANDARD")
                .status(AuditoriumStatus.ACTIVE)
                .build();
        AuditoriumResponse res2 = auditoriumService.updateAuditorium("aud-1", toActive);
        assertEquals(AuditoriumStatus.ACTIVE, res2.getStatus());
    }

    @Test
    void updateAuditorium_Reject_WhenDecommissioned() {
        sampleAuditorium.setStatus(AuditoriumStatus.DECOMMISSIONED);
        when(auditoriumRepository.findByIdAndDeletedAtIsNull("aud-1")).thenReturn(Optional.of(sampleAuditorium));

        UpdateAuditoriumRequest toActive = UpdateAuditoriumRequest.builder()
                .name("Hall 1")
                .type("STANDARD")
                .status(AuditoriumStatus.ACTIVE)
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> auditoriumService.updateAuditorium("aud-1", toActive));
        assertTrue(ex.getMessage().contains("DECOMMISSIONED"));
        verify(auditoriumRepository, never()).save(any());
    }
}