package com.cinebook.enums;

public enum ShowtimeFormat {

    TWO_D("2D"),
    THREE_D("3D"),
    IMAX("IMAX");

    private final String value;

    ShowtimeFormat(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ShowtimeFormat fromValue(String value) {
        for (ShowtimeFormat format : values()) {
            if (format.value.equals(value)) {
                return format;
            }
        }

        throw new IllegalArgumentException(
            "Unknown showtime format: " + value
        );
    }
}