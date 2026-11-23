package com.cocos.challenge.infrastructure.adapter.in.rest.format;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = DecimalStringValidator.class)
public @interface DecimalString {

    String message() default "must be a decimal string with up to 16 integer digits and at most 2 decimal places";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
