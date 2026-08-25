package com.cinebook.service;

import com.cinebook.dto.response.SeatResponse;
import com.cinebook.enums.SeatStatus;

import java.util.List;

public interface SeatService {

    List<SeatResponse> getSeatsByAuditorium(String auditoriumId);

    SeatResponse updateSeatType(String seatId, String seatTypeId);

    List<SeatResponse> batchUpdateSeatType(String auditoriumId, List<String> seatIds, String seatTypeId);

    SeatResponse updateSeatStatus(String seatId, SeatStatus status);
}