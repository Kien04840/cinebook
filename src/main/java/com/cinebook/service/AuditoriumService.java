package com.cinebook.service;

import com.cinebook.dto.request.CreateAuditoriumRequest;
import com.cinebook.dto.request.UpdateAuditoriumRequest;
import com.cinebook.dto.response.AuditoriumDetailResponse;
import com.cinebook.dto.response.AuditoriumResponse;

import java.util.List;

public interface AuditoriumService {

    List<AuditoriumResponse> getAuditoriumsByCinema(String cinemaId);

    AuditoriumDetailResponse getAuditoriumDetail(String id);

    AuditoriumDetailResponse createAuditorium(String cinemaId, CreateAuditoriumRequest request);

    AuditoriumResponse updateAuditorium(String id, UpdateAuditoriumRequest request);

    void deleteAuditorium(String id);
}