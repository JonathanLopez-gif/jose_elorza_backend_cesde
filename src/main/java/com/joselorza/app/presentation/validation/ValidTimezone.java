package com.joselorza.app.presentation.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD,  ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimezoneValidator.class)
public @interface ValidTimezone {
    String message() default "Invalid Timezone (it must be a valid IANA ID)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
