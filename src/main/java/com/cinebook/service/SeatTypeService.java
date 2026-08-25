package com.cinebook.service;

import com.cinebook.dto.request.CreateSeatTypeRequest;
import com.cinebook.dto.request.UpdateSeatTypeRequest;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.SeatTypeResponse;
import com.cinebook.entity.SeatType;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SeatTypeService {

    List<SeatTypeResponse> getAllActiveSeatTypes();

    PageResponse<SeatTypeResponse> getAdminSeatTypes(Pageable pageable);

    SeatTypeResponse getSeatTypeDetail(String id);

    SeatTypeResponse createSeatType(CreateSeatTypeRequest request);

    SeatTypeResponse updateSeatType(String id, UpdateSeatTypeRequest request);

    SeatType getOrCreateDefaultSeatType(String preferredId);
}