package com.cocos.challenge.infrastructure.adapter.in.rest.format;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DecimalStringValidator implements ConstraintValidator<DecimalString, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.matches("^-?\\d{1,16}(\\.\\d{1,2})?$");
    }
}
