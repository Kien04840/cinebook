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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriumServiceImpl implements AuditoriumService {

    private final AuditoriumRepository auditoriumRepository;
    private final CinemaRepository cinemaRepository;
    private final SeatTypeService seatTypeService;
    private final AuditoriumMapper auditoriumMapper;

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

        return auditoriumMapper.toAuditoriumDetailResponse(auditorium);
    }

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

        // Generate Seat Matrix
        Set<Seat> seats = new HashSet<>();
        short rows = request.getRowsCount();
        short cols = request.getColumnsCount();

        for (short r = 0; r < rows; r++) {
            String rowLabel = String.valueOf((char) ('A' + r));
            for (short c = 1; c <= cols; c++) {
                Seat seat = new Seat();
                seat.setAuditorium(auditorium);
                seat.setSeatType(defaultSeatType);
                seat.setRowLabel(rowLabel);
                seat.setSeatNumber(c);
                seat.setStatus(SeatStatus.ACTIVE);
                seats.add(seat);
            }
        }

        auditorium.setSeats(seats);

        Auditorium saved = auditoriumRepository.save(auditorium);
        log.info("Created auditorium id={} in cinema id={} with {} seats", saved.getId(), cinemaId, seats.size());

        return auditoriumMapper.toAuditoriumDetailResponse(saved);
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

        auditorium.setDeletedAt(LocalDateTime.now());
        auditorium.setStatus(AuditoriumStatus.DECOMMISSIONED);
        auditoriumRepository.save(auditorium);
        log.info("Soft-deleted auditorium: id={}", id);
    }
}