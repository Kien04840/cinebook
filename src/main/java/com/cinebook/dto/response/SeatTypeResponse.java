package com.cinebook.dto.response;

import com.cinebook.enums.SeatTypeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatTypeResponse {

    private String id;
    private String name;
    private BigDecimal priceModifier;
    private String description;
    private SeatTypeStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}