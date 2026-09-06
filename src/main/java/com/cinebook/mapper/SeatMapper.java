package com.cinebook.mapper;

import com.cinebook.dto.response.SeatResponse;
import com.cinebook.entity.Seat;
import org.springframework.stereotype.Component;

@Component
public class SeatMapper {

    public SeatResponse toSeatResponse(Seat seat) {
        if (seat == null) {
            return null;
        }

        String seatTypeId = seat.getSeatType() != null ? seat.getSeatType().getId() : null;
        String seatTypeName = seat.getSeatType() != null ? seat.getSeatType().getName() : null;
        String seatTypeCode = seat.getSeatType() != null ? seat.getSeatType().getCode() : null;
        Short capacity = seat.getSeatType() != null ? seat.getSeatType().getCapacity() : (short) 1;
        String colorToken = seat.getSeatType() != null ? seat.getSeatType().getColorToken() : null;
        String icon = seat.getSeatType() != null ? seat.getSeatType().getIcon() : null;
        java.math.BigDecimal priceModifier = seat.getSeatType() != null ? seat.getSeatType().getPriceModifier() : null;
        String auditoriumId = seat.getAuditorium() != null ? seat.getAuditorium().getId() : null;
        String seatCode = seat.getRowLabel() + seat.getSeatNumber();

        return SeatResponse.builder()
                .id(seat.getId())
                .auditoriumId(auditoriumId)
                .seatTypeId(seatTypeId)
                .seatTypeName(seatTypeName)
                .seatTypeCode(seatTypeCode)
                .capacity(capacity)
                .colorToken(colorToken)
                .icon(icon)
                .priceModifier(priceModifier)
                .rowLabel(seat.getRowLabel())
                .seatNumber(seat.getSeatNumber())
                .seatCode(seatCode)
                .status(seat.getStatus())
                .build();
    }
}