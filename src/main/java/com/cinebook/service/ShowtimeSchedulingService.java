package com.cinebook.service;

import com.cinebook.dto.request.CopyScheduleRequest;
import com.cinebook.dto.request.ShowtimeGenerationRequest;
import com.cinebook.dto.request.SuggestShowtimeSlotRequest;
import com.cinebook.dto.request.ValidateShowtimeSlotRequest;
import com.cinebook.dto.response.*;

import java.time.LocalDate;

public interface ShowtimeSchedulingService {

    ShowtimeGenerationPreviewResponse previewGeneration(ShowtimeGenerationRequest request);

    ShowtimeGenerationResultResponse generateShowtimes(ShowtimeGenerationRequest request);

    CopyScheduleResultResponse copySchedule(CopyScheduleRequest request);

    CalendarScheduleResponse getCalendarSchedule(String cinemaId, LocalDate from, LocalDate to);

    ValidateShowtimeSlotResponse validateSingleSlot(ValidateShowtimeSlotRequest request);

    SuggestShowtimeSlotResponse suggestNextSlot(SuggestShowtimeSlotRequest request);

    CinemaSchedulingConfigResponse getCinemaSchedulingConfig(String cinemaId);

    AuditoriumAvailabilityResponse getAuditoriumAvailability(String auditoriumId, LocalDate date);
}
