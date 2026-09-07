package com.cinebook.service.impl;

import com.cinebook.dto.response.BatchUpdateSeatTypePreviewResponse;
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
import com.cinebook.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final SeatMapper seatMapper;
    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final SeatHoldRepository seatHoldRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SeatResponse> getSeatsByAuditorium(String auditoriumId) {
        Auditorium auditorium = auditoriumRepository.findByIdAndDeletedAtIsNull(auditoriumId)
                .orElseThrow(() -> new ResourceNotFoundException("Auditorium not found with id: " + auditoriumId));

        return seatRepository.findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc(auditorium.getId()).stream()
                .map(seatMapper::toSeatResponse)
                .toList();
    }

    @Override
    @Transactional
    public SeatResponse updateSeatType(String seatId, String seatTypeId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with id: " + seatId));

        SeatType seatType = seatTypeRepository.findById(seatTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("SeatType not found with id: " + seatTypeId));

        Auditorium auditorium = seat.getAuditorium();
        LocalDateTime now = LocalDateTime.now();

        if (bookingRepository.existsByAuditoriumId(auditorium.getId()) || ticketRepository.existsByAuditoriumId(auditorium.getId())) {
            throw new ConflictException("Không thể thay đổi loại ghế của phòng chiếu đã phát sinh giao dịch đặt vé.");
        }

        if (seatHoldRepository.existsActiveHoldByAuditoriumId(auditorium.getId(), now)) {
            throw new ConflictException("Không thể thay đổi loại ghế vì phòng chiếu đang có ghế được giữ chỗ.");
        }

        List<Seat> targetSeats = new ArrayList<>(List.of(seat));
        handleCoupleSeatAssignmentAndRemoveOverlaps(auditorium, targetSeats, seatType);

        seat.setSeatType(seatType);
        Seat updated = seatRepository.save(seat);
        log.info("Updated seat type for seat {}: type={}", seatId, seatType.getName());
        return seatMapper.toSeatResponse(updated);
    }

    @Override
    @Transactional
    public List<SeatResponse> batchUpdateSeatType(String auditoriumId, List<String> seatIds, String seatTypeId) {
        Auditorium auditorium = auditoriumRepository.findByIdAndDeletedAtIsNull(auditoriumId)
                .orElseThrow(() -> new ResourceNotFoundException("Auditorium not found with id: " + auditoriumId));

        LocalDateTime now = LocalDateTime.now();

        if (bookingRepository.existsByAuditoriumId(auditorium.getId()) || ticketRepository.existsByAuditoriumId(auditorium.getId())) {
            throw new ConflictException("Không thể thay đổi loại ghế của phòng chiếu đã phát sinh giao dịch đặt vé.");
        }

        if (seatHoldRepository.existsActiveHoldByAuditoriumId(auditorium.getId(), now)) {
            throw new ConflictException("Không thể thay đổi loại ghế vì phòng chiếu đang có ghế được giữ chỗ.");
        }

        SeatType seatType = seatTypeRepository.findById(seatTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("SeatType not found with id: " + seatTypeId));

        List<Seat> seats = seatRepository.findAllById(seatIds);
        List<Seat> targetAuditoriumSeats = new ArrayList<>(seats.stream()
                .filter(s -> s.getAuditorium().getId().equals(auditorium.getId()))
                .toList());

        handleCoupleSeatAssignmentAndRemoveOverlaps(auditorium, targetAuditoriumSeats, seatType);

        for (Seat seat : targetAuditoriumSeats) {
            seat.setSeatType(seatType);
        }

        List<Seat> saved = seatRepository.saveAll(targetAuditoriumSeats);
        log.info("Batch updated {} seats in auditorium {} to seat type {}", saved.size(), auditoriumId, seatType.getName());

        return saved.stream()
                .map(seatMapper::toSeatResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BatchUpdateSeatTypePreviewResponse previewBatchUpdateSeatType(String auditoriumId, List<String> seatIds, String seatTypeId) {
        Auditorium auditorium = auditoriumRepository.findByIdAndDeletedAtIsNull(auditoriumId)
                .orElseThrow(() -> new ResourceNotFoundException("Auditorium not found with id: " + auditoriumId));

        boolean hasBookings = bookingRepository.existsByAuditoriumId(auditorium.getId());
        boolean hasTickets = ticketRepository.existsByAuditoriumId(auditorium.getId());
        boolean isProtected = hasBookings || hasTickets;

        SeatType seatType = seatTypeRepository.findById(seatTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("SeatType not found with id: " + seatTypeId));

        List<Seat> seats = seatRepository.findAllById(seatIds);
        List<Seat> targetSeats = new ArrayList<>(seats.stream()
                .filter(s -> s.getAuditorium().getId().equals(auditorium.getId()))
                .toList());

        if (isProtected) {
            return BatchUpdateSeatTypePreviewResponse.builder()
                    .seatCount(targetSeats.size())
                    .isProtected(true)
                    .targetSeatTypeName(seatType.getName())
                    .willDeleteSeatCodes(List.of())
                    .isValid(false)
                    .validationError("Không thể thay đổi loại ghế của phòng chiếu đã phát sinh giao dịch đặt vé.")
                    .build();
        }

        if (seatType.getCode() == null || !"COUPLE".equalsIgnoreCase(seatType.getCode())) {
            return BatchUpdateSeatTypePreviewResponse.builder()
                    .seatCount(targetSeats.size())
                    .isProtected(false)
                    .targetSeatTypeName(seatType.getName())
                    .willDeleteSeatCodes(List.of())
                    .isValid(true)
                    .validationError(null)
                    .build();
        }

        short rowsCount = auditorium.getRowsCount() != null ? auditorium.getRowsCount() : 1;
        String lastRowLabel = String.valueOf((char) ('A' + rowsCount - 1));
        short columnsCount = auditorium.getColumnsCount() != null ? auditorium.getColumnsCount() : 1;
        int coupleSpan = seatType.getCapacity() != null ? seatType.getCapacity() : 2;

        targetSeats.sort(Comparator.comparing(Seat::getRowLabel).thenComparing(Seat::getSeatNumber));

        for (Seat seat : targetSeats) {
            if (!seat.getRowLabel().equalsIgnoreCase(lastRowLabel)) {
                return BatchUpdateSeatTypePreviewResponse.builder()
                        .seatCount(targetSeats.size())
                        .isProtected(false)
                        .targetSeatTypeName(seatType.getName())
                        .willDeleteSeatCodes(List.of())
                        .isValid(false)
                        .validationError("Ghế đôi (COUPLE) chỉ được phép bố trí ở hàng ghế cuối cùng (" + lastRowLabel + ") của phòng chiếu.")
                        .build();
            }
            int startCol = seat.getSeatNumber().intValue();
            if (startCol % 2 == 0) {
                return BatchUpdateSeatTypePreviewResponse.builder()
                        .seatCount(targetSeats.size())
                        .isProtected(false)
                        .targetSeatTypeName(seatType.getName())
                        .willDeleteSeatCodes(List.of())
                        .isValid(false)
                        .validationError("Ghế đôi (COUPLE) phải bắt đầu từ cột lẻ (1, 3, 5...) để đảm bảo quy cách hàng ghế đôi (ví dụ: " + seat.getRowLabel() + (startCol - 1) + ").")
                        .build();
            }
            if (startCol + coupleSpan - 1 > columnsCount) {
                return BatchUpdateSeatTypePreviewResponse.builder()
                        .seatCount(targetSeats.size())
                        .isProtected(false)
                        .targetSeatTypeName(seatType.getName())
                        .willDeleteSeatCodes(List.of())
                        .isValid(false)
                        .validationError("Ghế đôi tại vị trí " + seat.getRowLabel() + startCol + " vượt quá số cột tối đa (" + columnsCount + ") của phòng chiếu.")
                        .build();
            }
        }

        for (int i = 0; i < targetSeats.size() - 1; i++) {
            Seat current = targetSeats.get(i);
            Seat next = targetSeats.get(i + 1);
            if (current.getSeatNumber() + coupleSpan - 1 >= next.getSeatNumber()) {
                return BatchUpdateSeatTypePreviewResponse.builder()
                        .seatCount(targetSeats.size())
                        .isProtected(false)
                        .targetSeatTypeName(seatType.getName())
                        .willDeleteSeatCodes(List.of())
                        .isValid(false)
                        .validationError("Ghế " + next.getSeatCode() + " bị chiếm dụng bởi khoảng ghế đôi của " + current.getSeatCode() + ".")
                        .build();
            }
        }

        List<Seat> allRowSeats = seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc(auditorium.getId(), lastRowLabel);
        Set<String> targetSeatIds = targetSeats.stream().map(Seat::getId).collect(Collectors.toSet());
        Map<Short, Seat> existingSeatsByCol = allRowSeats.stream()
                .collect(Collectors.toMap(Seat::getSeatNumber, s -> s, (s1, s2) -> s1));

        List<String> willDeleteCodes = new ArrayList<>();
        for (Seat targetSeat : targetSeats) {
            int startCol = targetSeat.getSeatNumber().intValue();
            for (int col = startCol + 1; col < startCol + coupleSpan; col++) {
                Seat overlapping = existingSeatsByCol.get((short) col);
                if (overlapping != null && !targetSeatIds.contains(overlapping.getId())) {
                    willDeleteCodes.add(overlapping.getSeatCode());
                }
            }
        }

        return BatchUpdateSeatTypePreviewResponse.builder()
                .seatCount(targetSeats.size())
                .isProtected(false)
                .targetSeatTypeName(seatType.getName())
                .willDeleteSeatCodes(willDeleteCodes)
                .isValid(true)
                .validationError(null)
                .build();
    }

    private void handleCoupleSeatAssignmentAndRemoveOverlaps(Auditorium auditorium, List<Seat> targetAuditoriumSeats, SeatType targetSeatType) {
        if (targetSeatType == null || targetSeatType.getCode() == null || !"COUPLE".equalsIgnoreCase(targetSeatType.getCode())) {
            return;
        }

        short rowsCount = auditorium.getRowsCount() != null ? auditorium.getRowsCount() : 1;
        String lastRowLabel = String.valueOf((char) ('A' + rowsCount - 1));
        short columnsCount = auditorium.getColumnsCount() != null ? auditorium.getColumnsCount() : 1;
        int coupleSpan = targetSeatType.getCapacity() != null ? targetSeatType.getCapacity() : 2;

        targetAuditoriumSeats.sort(Comparator.comparing(Seat::getRowLabel).thenComparing(Seat::getSeatNumber));

        // 1. Must be the auditorium's last row
        for (Seat seat : targetAuditoriumSeats) {
            if (!seat.getRowLabel().equalsIgnoreCase(lastRowLabel)) {
                throw new ConflictException(
                        "Ghế đôi (COUPLE) chỉ được phép bố trí ở hàng ghế cuối cùng (" + lastRowLabel + ") của phòng chiếu."
                );
            }
        }

        // 2. Odd column constraint and boundary check
        for (Seat seat : targetAuditoriumSeats) {
            int startCol = seat.getSeatNumber().intValue();
            if (startCol % 2 == 0) {
                throw new ConflictException(
                        "Ghế đôi (COUPLE) phải bắt đầu từ cột lẻ (1, 3, 5...) để đảm bảo quy cách hàng ghế đôi (ví dụ: "
                                + seat.getRowLabel() + (startCol - 1) + ")."
                );
            }
            int endCol = startCol + coupleSpan - 1;
            if (endCol > columnsCount) {
                throw new ConflictException(
                        "Ghế đôi tại vị trí " + seat.getRowLabel() + startCol
                                + " (chiếm từ cột " + startCol + " đến " + endCol + ") vượt quá số cột tối đa ("
                                + columnsCount + ") của phòng chiếu."
                );
            }
        }

        // 3. Collision check among target seats themselves
        for (int i = 0; i < targetAuditoriumSeats.size() - 1; i++) {
            Seat current = targetAuditoriumSeats.get(i);
            Seat next = targetAuditoriumSeats.get(i + 1);
            if (current.getSeatNumber() + coupleSpan - 1 >= next.getSeatNumber()) {
                throw new ConflictException(
                        "Ghế " + next.getSeatCode() + " bị chiếm dụng bởi khoảng ghế đôi của " + current.getSeatCode() + "."
                );
            }
        }

        // 4. Find all existing seats in that row and locate overlapping seats to delete
        List<Seat> allRowSeats = seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc(auditorium.getId(), lastRowLabel);
        Set<String> targetSeatIds = targetAuditoriumSeats.stream().map(Seat::getId).collect(Collectors.toSet());
        Map<Short, Seat> existingSeatsByCol = allRowSeats.stream()
                .collect(Collectors.toMap(Seat::getSeatNumber, s -> s, (s1, s2) -> s1));

        LocalDateTime now = LocalDateTime.now();
        List<Seat> seatsToDelete = new ArrayList<>();

        for (Seat targetSeat : targetAuditoriumSeats) {
            int startCol = targetSeat.getSeatNumber().intValue();
            for (int col = startCol + 1; col < startCol + coupleSpan; col++) {
                Seat overlapping = existingSeatsByCol.get((short) col);
                if (overlapping != null && !targetSeatIds.contains(overlapping.getId())) {
                    if (seatHoldRepository.existsActiveHoldBySeatId(overlapping.getId(), now)) {
                        throw new ConflictException("Không thể chuyển đổi ghế đôi vì ghế " + overlapping.getSeatCode() + " đang được giữ chỗ.");
                    }
                    if (ticketRepository.existsUpcomingValidTicketsBySeatId(overlapping.getId(), now)) {
                        throw new ConflictException("Không thể xóa ghế " + overlapping.getSeatCode() + " vì đã có vé lịch sử.");
                    }
                    seatsToDelete.add(overlapping);
                }
            }
        }

        // Check if other non-updating seats in row are Couple and collide
        for (Seat other : allRowSeats) {
            if (targetSeatIds.contains(other.getId()) || seatsToDelete.contains(other)) {
                continue;
            }
            if (other.getSeatType() != null && "COUPLE".equalsIgnoreCase(other.getSeatType().getCode())) {
                int otherStart = other.getSeatNumber().intValue();
                int otherEnd = otherStart + (other.getSeatType().getCapacity() != null ? other.getSeatType().getCapacity() : 2) - 1;
                for (Seat target : targetAuditoriumSeats) {
                    int tStart = target.getSeatNumber().intValue();
                    int tEnd = tStart + coupleSpan - 1;
                    if (tStart <= otherEnd && otherStart <= tEnd) {
                        throw new ConflictException("Ghế " + target.getSeatCode() + " bị xung đột vị trí với ghế đôi " + other.getSeatCode() + ".");
                    }
                }
            }
        }

        if (!seatsToDelete.isEmpty()) {
            seatRepository.deleteAll(seatsToDelete);
            seatRepository.flush();
            log.info("Atomically deleted {} overlapping seats ({}) to allocate Couple span in auditorium {}",
                    seatsToDelete.size(),
                    seatsToDelete.stream().map(Seat::getSeatCode).toList(),
                    auditorium.getId());
        }
    }

    @Override
    @Transactional
    public SeatResponse updateSeatStatus(String seatId, SeatStatus status) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with id: " + seatId));

        if (status == SeatStatus.BROKEN) {
            LocalDateTime now = LocalDateTime.now();
            if (seatHoldRepository.existsActiveHoldBySeatId(seatId, now)) {
                throw new ConflictException(
                        "Không thể đánh dấu ghế hỏng vì ghế đang được giữ chỗ trong một đơn đặt vé chưa hoàn tất."
                );
            }
            if (ticketRepository.existsUpcomingValidTicketsBySeatId(seatId, now)) {
                throw new ConflictException(
                        "Không thể đánh dấu ghế hỏng vì ghế đã có vé đặt cho suất chiếu sắp tới."
                );
            }
        }

        seat.setStatus(status);
        Seat updated = seatRepository.save(seat);
        log.info("Updated status for seat {}: status={}", seatId, status);
        return seatMapper.toSeatResponse(updated);
    }

    @Override
    @Transactional
    public List<SeatResponse> batchUpdateSeatStatus(String auditoriumId, List<String> seatIds, SeatStatus status) {
        Auditorium auditorium = auditoriumRepository.findByIdAndDeletedAtIsNull(auditoriumId)
                .orElseThrow(() -> new ResourceNotFoundException("Auditorium not found with id: " + auditoriumId));

        List<Seat> seats = seatRepository.findAllById(seatIds);
        List<Seat> targetSeats = seats.stream()
                .filter(s -> s.getAuditorium().getId().equals(auditorium.getId()))
                .toList();

        if (status == SeatStatus.BROKEN) {
            LocalDateTime now = LocalDateTime.now();
            for (Seat seat : targetSeats) {
                if (seatHoldRepository.existsActiveHoldBySeatId(seat.getId(), now)) {
                    throw new ConflictException(
                            "Không thể đánh dấu ghế " + seat.getSeatCode() + " hỏng vì ghế đang được giữ chỗ trong một đơn đặt vé chưa hoàn tất."
                    );
                }
                if (ticketRepository.existsUpcomingValidTicketsBySeatId(seat.getId(), now)) {
                    throw new ConflictException(
                            "Không thể đánh dấu ghế " + seat.getSeatCode() + " hỏng vì ghế đã có vé đặt cho suất chiếu sắp tới."
                    );
                }
            }
        }

        for (Seat seat : targetSeats) {
            seat.setStatus(status);
        }

        List<Seat> saved = seatRepository.saveAll(targetSeats);
        log.info("Batch updated {} seats in auditorium {} to status {}", saved.size(), auditoriumId, status);

        return saved.stream()
                .map(seatMapper::toSeatResponse)
                .toList();
    }
}