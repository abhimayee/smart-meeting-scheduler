package com.example.scheduler.user.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;

@Converter
public class DayOfWeekIsoConverter implements AttributeConverter<DayOfWeek, Short> {
    @Override public Short convertToDatabaseColumn(DayOfWeek value) {
        return value == null ? null : (short) value.getValue();
    }
    @Override public DayOfWeek convertToEntityAttribute(Short value) {
        return value == null ? null : DayOfWeek.of(value.intValue());
    }
}
