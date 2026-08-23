package com.cinebook.converter;

import com.cinebook.enums.ShowtimeFormat;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ShowtimeFormatConverter
        implements AttributeConverter<ShowtimeFormat, String> {

    @Override
    public String convertToDatabaseColumn(ShowtimeFormat attribute) {
        if (attribute == null) {
            return null;
        }

        return attribute.getValue();
    }

    @Override
    public ShowtimeFormat convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }

        return ShowtimeFormat.fromValue(value);
    }
}