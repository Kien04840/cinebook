package com.cinebook.dto.response;

import com.cinebook.enums.SeatAvailabilityStatus;
import com.cinebook.enums.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowtimeSeatStatusResponse {

    private String id;
    private String auditoriumId;
    private String seatTypeId;
    private String seatTypeName;
    private BigDecimal priceModifier;
    private String rowLabel;
    private Short seatNumber;
    private String seatCode;
    private SeatStatus seatStatus;
    private SeatAvailabilityStatus availabilityStatus;
}

