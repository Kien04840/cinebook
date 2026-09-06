package com.cinebook.mapper;

import com.cinebook.dto.response.SeatTypeResponse;
import com.cinebook.entity.SeatType;
import org.springframework.stereotype.Component;

@Component
public class SeatTypeMapper {

    public SeatTypeResponse toSeatTypeResponse(SeatType seatType) {
        if (seatType == null) {
            return null;
        }

        return SeatTypeResponse.builder()
                .id(seatType.getId())
                .code(seatType.getCode())
                .name(seatType.getName())
                .priceModifier(seatType.getPriceModifier())
                .capacity(seatType.getCapacity())
                .colorToken(seatType.getColorToken())
                .icon(seatType.getIcon())
                .description(seatType.getDescription())
                .status(seatType.getStatus())
                .createdAt(seatType.getCreatedAt())
                .updatedAt(seatType.getUpdatedAt())
                .build();
    }
}