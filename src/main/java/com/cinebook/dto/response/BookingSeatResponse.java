package com.cinebook.dto.response;

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
public class BookingSeatResponse {

    private String seatId;
    private String rowLabel;
    private Short seatNumber;
    private String seatCode;
    private String seatTypeId;
    private String seatTypeName;
    private BigDecimal price;
}

