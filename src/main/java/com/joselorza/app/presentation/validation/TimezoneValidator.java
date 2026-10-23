package com.joselorza.app.presentation.validation;

import com.joselorza.app.domain.model.User;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.ZoneId;

public class TimezoneValidator implements ConstraintValidator<ValidTimezone, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return false;
        return ZoneId.getAvailableZoneIds().contains(value);
    }
}
