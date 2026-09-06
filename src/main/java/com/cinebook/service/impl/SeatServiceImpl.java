package com.cinebook.service.impl;

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
import com.cinebook.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final SeatMapper seatMapper;

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
        validateCoupleSeatAssignment(auditorium, List.of(seat), seatType);

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

        SeatType seatType = seatTypeRepository.findById(seatTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("SeatType not found with id: " + seatTypeId));

        List<Seat> seats = seatRepository.findAllById(seatIds);
        List<Seat> targetAuditoriumSeats = seats.stream()
                .filter(s -> s.getAuditorium().getId().equals(auditorium.getId()))
                .toList();

        validateCoupleSeatAssignment(auditorium, targetAuditoriumSeats, seatType);

        for (Seat seat : targetAuditoriumSeats) {
            seat.setSeatType(seatType);
        }

        List<Seat> saved = seatRepository.saveAll(targetAuditoriumSeats);
        log.info("Batch updated {} seats in auditorium {} to seat type {}", saved.size(), auditoriumId, seatType.getName());

        return saved.stream()
                .map(seatMapper::toSeatResponse)
                .toList();
    }

    private void validateCoupleSeatAssignment(Auditorium auditorium, List<Seat> seatsToUpdate, SeatType targetSeatType) {
        if (targetSeatType == null || targetSeatType.getCode() == null || !"COUPLE".equalsIgnoreCase(targetSeatType.getCode())) {
            return;
        }

        short rowsCount = auditorium.getRowsCount() != null ? auditorium.getRowsCount() : 1;
        String lastRowLabel = String.valueOf((char) ('A' + rowsCount - 1));
        short columnsCount = auditorium.getColumnsCount() != null ? auditorium.getColumnsCount() : 1;
        int coupleSpan = targetSeatType.getCapacity() != null ? targetSeatType.getCapacity() : 2;

        java.util.Set<String> updatingSeatIds = seatsToUpdate.stream().map(Seat::getId).collect(java.util.stream.Collectors.toSet());

        java.util.Map<String, List<Seat>> updatingSeatsByRow = seatsToUpdate.stream()
                .collect(java.util.stream.Collectors.groupingBy(Seat::getRowLabel));

        for (java.util.Map.Entry<String, List<Seat>> entry : updatingSeatsByRow.entrySet()) {
            String rowLabel = entry.getKey();
            List<Seat> updatingInRow = entry.getValue();

            // 1. Must be the auditorium's last row
            if (!rowLabel.equalsIgnoreCase(lastRowLabel)) {
                throw new com.cinebook.exception.ConflictException(
                        "Ghế đôi (COUPLE) chỉ được phép bố trí ở hàng ghế cuối cùng (" + lastRowLabel + ") của phòng chiếu."
                );
            }

            // 2. Check each updating seat's bounds
            for (Seat seat : updatingInRow) {
                int startCol = seat.getSeatNumber().intValue();
                int endCol = startCol + coupleSpan - 1;
                if (endCol > columnsCount) {
                    throw new com.cinebook.exception.ConflictException(
                            "Ghế đôi tại vị trí " + seat.getRowLabel() + startCol
                            + " (chiếm từ cột " + startCol + " đến " + endCol + ") vượt quá số cột tối đa ("
                            + columnsCount + ") của phòng chiếu."
                    );
                }
            }

            // 3. Check for overlaps with all seats in that row
            List<Seat> allSeatsInRow = seatRepository.findByAuditoriumIdAndRowLabelOrderBySeatNumberAsc(auditorium.getId(), rowLabel);

            List<OccupiedSpan> spans = new ArrayList<>();
            for (Seat s : allSeatsInRow) {
                int start = s.getSeatNumber().intValue();
                int span;
                if (updatingSeatIds.contains(s.getId())) {
                    span = coupleSpan;
                } else if (s.getSeatType() != null && "COUPLE".equalsIgnoreCase(s.getSeatType().getCode())) {
                    span = s.getSeatType().getCapacity() != null ? s.getSeatType().getCapacity() : 2;
                } else {
                    span = 1;
                }
                spans.add(new OccupiedSpan(s, start, start + span - 1));
            }

            spans.sort(java.util.Comparator.comparingInt(a -> a.start));
            for (int i = 0; i < spans.size() - 1; i++) {
                OccupiedSpan current = spans.get(i);
                OccupiedSpan next = spans.get(i + 1);
                if (next.start <= current.end) {
                    throw new com.cinebook.exception.ConflictException(
                            "Ghế đôi tại " + current.seat.getRowLabel() + current.start
                            + " (chiếm cột " + current.start + "–" + current.end + ") bị xung đột vị trí với ghế "
                            + next.seat.getRowLabel() + next.start + "."
                    );
                }
            }
        }
    }

    private record OccupiedSpan(Seat seat, int start, int end) {}

    @Override
    @Transactional
    public SeatResponse updateSeatStatus(String seatId, SeatStatus status) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with id: " + seatId));

        seat.setStatus(status);
        Seat updated = seatRepository.save(seat);
        log.info("Updated status for seat {}: status={}", seatId, status);
        return seatMapper.toSeatResponse(updated);
    }
}