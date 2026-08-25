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
        List<Seat> updatedSeats = new ArrayList<>();

        for (Seat seat : seats) {
            if (seat.getAuditorium().getId().equals(auditorium.getId())) {
                seat.setSeatType(seatType);
                updatedSeats.add(seat);
            }
        }

        List<Seat> saved = seatRepository.saveAll(updatedSeats);
        log.info("Batch updated {} seats in auditorium {} to seat type {}", saved.size(), auditoriumId, seatType.getName());

        return saved.stream()
                .map(seatMapper::toSeatResponse)
                .toList();
    }

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