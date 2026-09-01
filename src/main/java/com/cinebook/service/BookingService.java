package com.cinebook.service;

import com.cinebook.dto.request.CancelBookingRequest;
import com.cinebook.dto.request.CreateBookingRequest;
import com.cinebook.dto.response.BookingDetailResponse;
import com.cinebook.dto.response.BookingSummaryResponse;
import com.cinebook.dto.response.PageResponse;
import com.cinebook.dto.response.ShowtimeSeatStatusResponse;
import com.cinebook.enums.BookingStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BookingService {

    BookingDetailResponse createBooking(CreateBookingRequest request);

    BookingDetailResponse getBookingDetail(String bookingId);

    PageResponse<BookingSummaryResponse> getMyBookings(BookingStatus status, Pageable pageable);

    PageResponse<BookingSummaryResponse> getAdminBookings(String q, BookingStatus status, String showtimeId, Pageable pageable);

    BookingDetailResponse cancelBooking(String bookingId, CancelBookingRequest request);

    BookingDetailResponse confirmPaidBooking(String bookingId, String paymentId);

    BookingDetailResponse processBookingRefund(String bookingId, String reason, String userId);

    List<ShowtimeSeatStatusResponse> getShowtimeSeatAvailability(String showtimeId);
    BookingDetailResponse getActiveBookingForShowtime(String showtimeId);
}



