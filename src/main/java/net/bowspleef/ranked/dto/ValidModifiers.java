package net.bowspleef.ranked.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The modifiers of a match (spec 010): a JSON object of at most {@value ModifiersValidator#MAX_ENTRIES} entries,
 * with keys of 1 to {@value ModifiersValidator#MAX_KEY_LENGTH} characters and values that are strings, numbers or
 * booleans. The API stores them as they come and does not know what a modifier means. Null is valid (no information).
 */
@Documented
@Constraint(validatedBy = ModifiersValidator.class)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidModifiers {

    String message() default "modifiers must be an object of at most 20 entries with short keys and scalar values";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
