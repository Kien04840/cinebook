package com.cinebook.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchUpdateSeatTypePreviewResponse {
    private int seatCount;
    private boolean isProtected;
    private String targetSeatTypeName;
    private List<String> willDeleteSeatCodes;
    private boolean isValid;
    private String validationError;

    public int getSeatCount() {
        return seatCount;
    }

    public void setSeatCount(int seatCount) {
        this.seatCount = seatCount;
    }

    @JsonProperty("isProtected")
    public boolean isProtected() {
        return isProtected;
    }

    @JsonProperty("isProtected")
    public void setProtected(boolean aProtected) {
        isProtected = aProtected;
    }

    public String getTargetSeatTypeName() {
        return targetSeatTypeName;
    }

    public void setTargetSeatTypeName(String targetSeatTypeName) {
        this.targetSeatTypeName = targetSeatTypeName;
    }

    public List<String> getWillDeleteSeatCodes() {
        return willDeleteSeatCodes;
    }

    public void setWillDeleteSeatCodes(List<String> willDeleteSeatCodes) {
        this.willDeleteSeatCodes = willDeleteSeatCodes;
    }

    @JsonProperty("isValid")
    public boolean isValid() {
        return isValid;
    }

    @JsonProperty("isValid")
    public void setValid(boolean valid) {
        isValid = valid;
    }

    public String getValidationError() {
        return validationError;
    }

    public void setValidationError(String validationError) {
        this.validationError = validationError;
    }
}
