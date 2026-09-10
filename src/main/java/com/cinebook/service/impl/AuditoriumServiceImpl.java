package com.cinebook.service.impl;

import com.cinebook.dto.request.CreateAuditoriumRequest;
import com.cinebook.dto.request.UpdateAuditoriumRequest;
import com.cinebook.dto.response.AuditoriumDetailResponse;
import com.cinebook.dto.response.AuditoriumResponse;
import com.cinebook.entity.Auditorium;
import com.cinebook.entity.Cinema;
import com.cinebook.entity.Seat;
import com.cinebook.entity.SeatType;
import com.cinebook.enums.AuditoriumStatus;
import com.cinebook.enums.SeatStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.exception.ResourceNotFoundException;
import com.cinebook.mapper.AuditoriumMapper;
import com.cinebook.repository.AuditoriumRepository;
import com.cinebook.repository.CinemaRepository;
import com.cinebook.service.AuditoriumService;
import com.cinebook.service.SeatTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.cinebook.dto.response.NormalizeEmptyLayoutsResponse;
import com.cinebook.repository.BookingRepository;
import com.cinebook.repository.SeatHoldRepository;
import com.cinebook.repository.SeatRepository;
import com.cinebook.repository.ShowtimeRepository;
import com.cinebook.repository.TicketRepository;
import java.util.ArrayList;

/**
 * Dịch vụ quản lý phòng chiếu và tự động khởi tạo/chuẩn hóa sơ đồ ghế thực tế (Auditorium & Seat Layout Engine).
 * 
 * Kiến trúc & Cơ chế hoạt động:
 * 1. Khởi tạo sơ đồ ghế thực tế (Realistic Seat Layout Generation):
 *    - Hàng đầu (Standard): Ghế phổ thông gần màn hình chiếu.
 *    - Hàng giữa (VIP): Vị trí trung tâm có góc nhìn đẹp nhất, gán loại ghế VIP.
 *    - Hàng cuối (Couple): Dành riêng cho ghế đôi. Mỗi ghế đôi nằm ở cột lẻ (cột 1, 3, 5,...) và chiếm span 2 cột vật lý.
 * 2. Bảo vệ phòng chiếu đã phát sinh dữ liệu (Auditorium Protection Rule):
 *    - Nếu phòng chiếu đã có lịch chiếu có người đặt (hasBookings), đã phát hành vé (hasTickets) hoặc đang có người giữ chỗ (hasActiveHolds),
 *      hệ thống nghiêm cấm reset sơ đồ ghế hoặc sửa đổi cấu trúc vật lý để bảo vệ toàn vẹn dữ liệu tài chính lịch sử.
 * 3. Chuẩn hóa hàng loạt tại chỗ (In-Place Batch Normalization):
 *    - Thực thi trong các Transaction độc lập (TransactionDefinition.PROPAGATION_REQUIRES_NEW) cho từng phòng chiếu,
 *      đảm bảo một phòng chiếu gặp lỗi sẽ không làm rollback tiến trình của toàn bộ các phòng chiếu khác.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriumServiceImpl implements AuditoriumService {

    private final AuditoriumRepository auditoriumRepository;
    private final CinemaRepository cinemaRepository;
    private final SeatTypeService seatTypeService;
    private final AuditoriumMapper auditoriumMapper;
    private final SeatRepository seatRepository;
    private final ShowtimeRepository showtimeRepository;
    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Override
    @Transactional(readOnly = true)
    public List<AuditoriumResponse> getAuditoriumsByCinema(String cinemaId) {
        Cinema cinema = cinemaRepository.findByIdAndDeletedAtIsNull(cinemaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema not found with id: " + cinemaId));

        return auditoriumRepository.findByCinemaIdAndDeletedAtIsNull(cinema.getId()).stream()
                .map(auditoriumMapper::toAuditoriumResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AuditoriumDetailResponse getAuditoriumDetail(String id) {
        Auditorium auditorium = auditoriumRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Auditorium not found with id: " + id));

        boolean hasShowtimes = showtimeRepository.existsByAuditoriumId(id);
        boolean hasBookings = bookingRepository.existsByAuditoriumId(id);
        boolean hasTickets = ticketRepository.existsByAuditoriumId(id);

        return auditoriumMapper.toAuditoriumDetailResponse(auditorium, hasShowtimes, hasBookings, hasTickets);
    }

    /**
     * Tạo mới phòng chiếu và tự động sinh ma trận ghế thực tế dựa trên rowsCount và columnsCount.
     */
    @Override
    @Transactional
    public AuditoriumDetailResponse createAuditorium(String cinemaId, CreateAuditoriumRequest request) {
        Cinema cinema = cinemaRepository.findByIdAndDeletedAtIsNull(cinemaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema not found with id: " + cinemaId));

        if (auditoriumRepository.existsByCinemaIdAndNameAndDeletedAtIsNull(cinema.getId(), request.getName().trim())) {
            throw new ConflictException("Auditorium with name '" + request.getName() + "' already exists in this cinema");
        }

        SeatType defaultSeatType = seatTypeService.getOrCreateDefaultSeatType(request.getDefaultSeatTypeId());

        Auditorium auditorium = new Auditorium();
        auditorium.setCinema(cinema);
        auditorium.setName(request.getName().trim());
        auditorium.setType(request.getType().trim().toUpperCase());
        auditorium.setRowsCount(request.getRowsCount());
        auditorium.setColumnsCount(request.getColumnsCount());
        auditorium.setStatus(request.getStatus() != null ? request.getStatus() : AuditoriumStatus.ACTIVE);
        if (request.getTurnaroundMinutes() != null) {
            auditorium.setTurnaroundMinutes(request.getTurnaroundMinutes());
        }
        if (request.getSnapIntervalMinutes() != null) {
            auditorium.setSnapIntervalMinutes(request.getSnapIntervalMinutes());
        }

        Set<Seat> seats = generateRealisticSeatLayout(auditorium, request.getRowsCount(), request.getColumnsCount(), defaultSeatType);
        auditorium.setSeats(seats);

        Auditorium saved = auditoriumRepository.save(auditorium);
        log.info("Created auditorium id={} in cinema id={} with {} seats", saved.getId(), cinemaId, seats.size());

        return auditoriumMapper.toAuditoriumDetailResponse(saved, false, false, false);
    }

    @Override
    @Transactional
    public AuditoriumResponse updateAuditorium(String id, UpdateAuditoriumRequest request) {
        Auditorium auditorium = auditoriumRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Auditorium not found with id: " + id));

        if (auditorium.getStatus() == AuditoriumStatus.DECOMMISSIONED && request.getStatus() != AuditoriumStatus.DECOMMISSIONED) {
            throw new BadRequestException("Không thể chuyển phòng chiếu đã ngừng hoạt động (DECOMMISSIONED) về trạng thái hoạt động!");
        }

        if (auditoriumRepository.existsByCinemaIdAndNameAndIdNotAndDeletedAtIsNull(
                auditorium.getCinema().getId(), request.getName().trim(), id)) {
            throw new ConflictException("Auditorium with name '" + request.getName() + "' already exists in this cinema");
        }

        auditorium.setName(request.getName().trim());
        auditorium.setType(request.getType().trim().toUpperCase());
        auditorium.setStatus(request.getStatus());
        if (request.getTurnaroundMinutes() != null) {
            auditorium.setTurnaroundMinutes(request.getTurnaroundMinutes());
        }
        if (request.getSnapIntervalMinutes() != null) {
            auditorium.setSnapIntervalMinutes(request.getSnapIntervalMinutes());
        }

        Auditorium updated = auditoriumRepository.save(auditorium);
        log.info("Updated auditorium: id={}, name={}, status={}", updated.getId(), updated.getName(), updated.getStatus());
        return auditoriumMapper.toAuditoriumResponse(updated);
    }

    @Override
    @Transactional
    public void deleteAuditorium(String id) {
        Auditorium auditorium = auditoriumRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Auditorium not found with id: " + id));

        if (showtimeRepository.existsByAuditoriumId(id)) {
            throw new ConflictException("Cannot delete auditorium with existing showtimes");
        }

        auditorium.setDeletedAt(LocalDateTime.now());
        auditorium.setStatus(AuditoriumStatus.DECOMMISSIONED);
        auditoriumRepository.save(auditorium);
        log.info("Soft-deleted auditorium: id={}", id);
    }

    /**
     * Thiết lập lại sơ đồ ghế thực tế tại chỗ (In-Place Reset) cho một phòng chiếu cụ thể.
     * Áp dụng quy tắc bảo vệ nghiêm ngặt: Từ chối nếu phòng chiếu đã có bất kỳ booking, vé, hoặc ghế đang giữ nào.
     */
    @Override
    @Transactional
    public AuditoriumDetailResponse resetAuditoriumLayout(String id) {
        Auditorium auditorium = auditoriumRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Auditorium not found with id: " + id));

        LocalDateTime now = LocalDateTime.now();
        boolean hasBookings = bookingRepository.existsByAuditoriumId(id);
        boolean hasTickets = ticketRepository.existsByAuditoriumId(id);
        boolean hasActiveHolds = seatHoldRepository.existsActiveHoldByAuditoriumId(id, now);

        // Quy tắc bảo vệ: Không cho phép thay đổi cấu trúc ghế nếu đã có dữ liệu giao dịch
        if (hasBookings || hasTickets || hasActiveHolds) {
            throw new ConflictException("Không thể thiết lập lại sơ đồ ghế của phòng chiếu đã phát sinh giao dịch đặt vé hoặc đang có người giữ chỗ.");
        }

        applyRealisticLayoutInPlace(auditorium);
        List<Seat> currentSeats = seatRepository.findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc(auditorium.getId());
        auditorium.setSeats(new HashSet<>(currentSeats));

        log.info("Reset auditorium layout in-place for id={}: current seats={}", id, currentSeats.size());
        boolean hasShowtimes = showtimeRepository.existsByAuditoriumId(id);
        return auditoriumMapper.toAuditoriumDetailResponse(auditorium, hasShowtimes, false, false);
    }

    /**
     * Quét và chuẩn hóa hàng loạt sơ đồ ghế thực tế cho toàn bộ các phòng chiếu chưa có giao dịch (Empty Auditoriums).
     * Bỏ qua an toàn các phòng chiếu đã có booking, vé hoặc ghế đang giữ.
     */
    @Override
    public NormalizeEmptyLayoutsResponse normalizeEmptyAuditoriumsLayout() {
        List<Auditorium> auditoriums = auditoriumRepository.findAllWithCinemaByDeletedAtIsNull();

        int scannedCount = 0;
        int normalizedCount = 0;
        int unchangedCount = 0;
        int skippedCount = 0;
        int skippedBecauseBookings = 0;
        int skippedBecauseTickets = 0;
        int skippedBecauseActiveHolds = 0;
        int failedCount = 0;
        List<String> updatedAuditoriumIds = new ArrayList<>();
        List<NormalizeEmptyLayoutsResponse.SkippedItem> skippedDetails = new ArrayList<>();

        for (Auditorium a : auditoriums) {
            scannedCount++;
            LocalDateTime now = LocalDateTime.now();
            boolean hasBookings = bookingRepository.existsByAuditoriumId(a.getId());
            boolean hasTickets = ticketRepository.existsByAuditoriumId(a.getId());
            boolean hasActiveHolds = seatHoldRepository.existsActiveHoldByAuditoriumId(a.getId(), now);

            if (hasBookings || hasTickets || hasActiveHolds) {
                skippedCount++;
                StringBuilder reason = new StringBuilder("Phòng chiếu được bảo vệ (");
                if (hasBookings) {
                    skippedBecauseBookings++;
                    reason.append("Bookings, ");
                }
                if (hasTickets) {
                    skippedBecauseTickets++;
                    reason.append("Tickets, ");
                }
                if (hasActiveHolds) {
                    skippedBecauseActiveHolds++;
                    reason.append("ActiveHolds, ");
                }
                if (reason.toString().endsWith(", ")) {
                    reason.setLength(reason.length() - 2);
                }
                reason.append(")");

                skippedDetails.add(NormalizeEmptyLayoutsResponse.SkippedItem.builder()
                        .auditoriumId(a.getId())
                        .auditoriumName(a.getName())
                        .cinemaName(a.getCinema() != null ? a.getCinema().getName() : "")
                        .reason(reason.toString())
                        .build());
                continue;
            }

            try {
                org.springframework.transaction.support.TransactionTemplate txTemplate =
                        new org.springframework.transaction.support.TransactionTemplate(transactionManager);
                txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

                NormalizationResult result = txTemplate.execute(status -> {
                    Auditorium aud = auditoriumRepository.findById(a.getId()).orElse(a);
                    return applyRealisticLayoutInPlace(aud);
                });

                if (result == NormalizationResult.UPDATED) {
                    normalizedCount++;
                    updatedAuditoriumIds.add(a.getId());
                } else {
                    unchangedCount++;
                }
            } catch (Exception ex) {
                log.error("Failed to normalize auditorium {} ({}): {}", a.getId(), a.getName(), ex.getMessage(), ex);
                failedCount++;
            }
        }

        log.info("Normalized auditorium layouts: scanned={}, normalized={}, unchanged={}, skipped={} (bookings={}, tickets={}, holds={}), failed={}",
                scannedCount, normalizedCount, unchangedCount, skippedCount, skippedBecauseBookings, skippedBecauseTickets, skippedBecauseActiveHolds, failedCount);

        return NormalizeEmptyLayoutsResponse.builder()
                .processedCount(scannedCount)
                .scannedCount(scannedCount)
                .updatedCount(normalizedCount)
                .normalizedCount(normalizedCount)
                .unchangedCount(unchangedCount)
                .skippedCount(skippedCount)
                .skippedBecauseBookings(skippedBecauseBookings)
                .skippedBecauseTickets(skippedBecauseTickets)
                .skippedBecauseActiveHolds(skippedBecauseActiveHolds)
                .failedCount(failedCount)
                .updatedAuditoriumIds(updatedAuditoriumIds)
                .skippedDetails(skippedDetails)
                .build();
    }

    private enum NormalizationResult {
        UNCHANGED, UPDATED
    }

    private NormalizationResult applyRealisticLayoutInPlace(Auditorium auditorium) {
        short rows = auditorium.getRowsCount() != null ? auditorium.getRowsCount() : 0;
        short cols = auditorium.getColumnsCount() != null ? auditorium.getColumnsCount() : 0;
        if (rows <= 0 || cols <= 0) {
            return NormalizationResult.UNCHANGED;
        }

        SeatType standardType = seatTypeService.getOrCreateDefaultSeatType(null);
        SeatType vipType = seatTypeService.getOrCreateVipSeatType();
        SeatType coupleType = seatTypeService.getOrCreateCoupleSeatType();

        java.util.Map<String, TargetSeatInfo> targetMap = new java.util.HashMap<>();

        if (rows == 1) {
            String rowLabel = "A";
            for (short c = 1; c <= cols; c++) {
                targetMap.put(rowLabel + "_" + c, new TargetSeatInfo(rowLabel, c, standardType));
            }
        } else if (rows == 2) {
            for (short c = 1; c <= cols; c++) {
                targetMap.put("A_" + c, new TargetSeatInfo("A", c, standardType));
                targetMap.put("B_" + c, new TargetSeatInfo("B", c, vipType));
            }
        } else {
            short lastRowIndex = (short) (rows - 1);
            String lastRowLabel = String.valueOf((char) ('A' + lastRowIndex));

            for (short c = 1; c + 1 <= cols; c += 2) {
                targetMap.put(lastRowLabel + "_" + c, new TargetSeatInfo(lastRowLabel, c, coupleType));
            }

            int nonCoupleRows = rows - 1;
            int standardRowsCount = Math.max(1, Math.round(nonCoupleRows * 0.45f));

            for (short r = 0; r < lastRowIndex; r++) {
                String rowLabel = String.valueOf((char) ('A' + r));
                SeatType rowSeatType = (r < standardRowsCount) ? standardType : vipType;
                for (short c = 1; c <= cols; c++) {
                    targetMap.put(rowLabel + "_" + c, new TargetSeatInfo(rowLabel, c, rowSeatType));
                }
            }
        }

        List<Seat> existingSeats = seatRepository.findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc(auditorium.getId());
        java.util.Map<String, Seat> existingMap = existingSeats.stream()
                .collect(java.util.stream.Collectors.toMap(s -> s.getRowLabel() + "_" + s.getSeatNumber(), s -> s, (s1, s2) -> s1));

        List<Seat> seatsToUpdate = new ArrayList<>();
        List<Seat> seatsToCreate = new ArrayList<>();
        List<Seat> seatsToDelete = new ArrayList<>();

        for (java.util.Map.Entry<String, TargetSeatInfo> entry : targetMap.entrySet()) {
            String posKey = entry.getKey();
            TargetSeatInfo target = entry.getValue();
            Seat existing = existingMap.get(posKey);

            if (existing != null) {
                boolean modified = false;
                if (existing.getSeatType() == null || !existing.getSeatType().getId().equals(target.seatType.getId())) {
                    existing.setSeatType(target.seatType);
                    modified = true;
                }
                if (existing.getStatus() != SeatStatus.ACTIVE) {
                    existing.setStatus(SeatStatus.ACTIVE);
                    modified = true;
                }
                if (modified) {
                    seatsToUpdate.add(existing);
                }
            } else {
                seatsToCreate.add(buildSeat(auditorium, target.seatType, target.rowLabel, target.seatNumber));
            }
        }

        for (Seat existing : existingSeats) {
            String posKey = existing.getRowLabel() + "_" + existing.getSeatNumber();
            if (!targetMap.containsKey(posKey)) {
                seatsToDelete.add(existing);
            }
        }

        if (seatsToUpdate.isEmpty() && seatsToCreate.isEmpty() && seatsToDelete.isEmpty()) {
            return NormalizationResult.UNCHANGED;
        }

        if (!seatsToDelete.isEmpty()) {
            seatRepository.deleteAll(seatsToDelete);
        }
        if (!seatsToUpdate.isEmpty()) {
            seatRepository.saveAll(seatsToUpdate);
        }
        if (!seatsToCreate.isEmpty()) {
            seatRepository.saveAll(seatsToCreate);
        }
        seatRepository.flush();

        log.info("Auditorium {}: Normalized layout in-place (updated={}, created={}, deleted={})",
                auditorium.getId(), seatsToUpdate.size(), seatsToCreate.size(), seatsToDelete.size());
        return NormalizationResult.UPDATED;
    }

    private record TargetSeatInfo(String rowLabel, short seatNumber, SeatType seatType) {}

    private Set<Seat> generateRealisticSeatLayout(Auditorium auditorium, short rows, short cols, SeatType preferredStandardType) {
        Set<Seat> seats = new java.util.HashSet<>();

        SeatType standardType = preferredStandardType != null
                ? preferredStandardType
                : seatTypeService.getOrCreateDefaultSeatType(null);
        SeatType vipType = seatTypeService.getOrCreateVipSeatType();
        SeatType coupleType = seatTypeService.getOrCreateCoupleSeatType();

        if (rows <= 0 || cols <= 0) {
            return seats;
        }

        if (rows == 1) {
            String rowLabel = "A";
            for (short c = 1; c <= cols; c++) {
                seats.add(buildSeat(auditorium, standardType, rowLabel, c));
            }
            return seats;
        }

        if (rows == 2) {
            for (short c = 1; c <= cols; c++) {
                seats.add(buildSeat(auditorium, standardType, "A", c));
                seats.add(buildSeat(auditorium, vipType, "B", c));
            }
            return seats;
        }

        // rows >= 3: Front rows are Standard, Middle rows are VIP, Last row is Couple
        short lastRowIndex = (short) (rows - 1);
        String lastRowLabel = String.valueOf((char) ('A' + lastRowIndex));

        // Couple row: spans = 2, starting at odd columns: 1, 3, 5, ...
        // Constraint: c + 1 <= cols
        for (short c = 1; c + 1 <= cols; c += 2) {
            seats.add(buildSeat(auditorium, coupleType, lastRowLabel, c));
        }

        // Non-couple rows: 0 to rows - 2 (total = rows - 1 rows)
        int nonCoupleRows = rows - 1;
        int standardRowsCount = Math.max(1, Math.round(nonCoupleRows * 0.45f));

        for (short r = 0; r < lastRowIndex; r++) {
            String rowLabel = String.valueOf((char) ('A' + r));
            SeatType rowSeatType = (r < standardRowsCount) ? standardType : vipType;

            for (short c = 1; c <= cols; c++) {
                seats.add(buildSeat(auditorium, rowSeatType, rowLabel, c));
            }
        }

        return seats;
    }

    private Seat buildSeat(Auditorium auditorium, SeatType seatType, String rowLabel, short seatNumber) {
        Seat seat = new Seat();
        seat.setAuditorium(auditorium);
        seat.setSeatType(seatType);
        seat.setRowLabel(rowLabel);
        seat.setSeatNumber(seatNumber);
        seat.setStatus(SeatStatus.ACTIVE);
        return seat;
    }
}