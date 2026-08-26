package com.cinebook.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ShowtimeFormat {

    TWO_D("2D"),
    THREE_D("3D"),
    IMAX("IMAX");

    private final String value;

    ShowtimeFormat(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ShowtimeFormat fromValue(String value) {
        if (value == null) {
            return null;
        }

        for (ShowtimeFormat format : values()) {
            if (format.value.equalsIgnoreCase(value.trim()) || format.name().equalsIgnoreCase(value.trim())) {
                return format;
            }
        }

        throw new IllegalArgumentException(
            "Unknown showtime format: " + value
        );
    }
}