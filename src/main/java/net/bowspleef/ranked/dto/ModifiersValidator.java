package net.bowspleef.ranked.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;

/** Applies {@link ValidModifiers}. */
public class ModifiersValidator implements ConstraintValidator<ValidModifiers, Map<String, Object>> {

    static final int MAX_ENTRIES = 20;
    static final int MAX_KEY_LENGTH = 50;

    @Override
    public boolean isValid(Map<String, Object> modifiers, ConstraintValidatorContext context) {
        if (modifiers == null) {
            return true;
        }
        if (modifiers.size() > MAX_ENTRIES) {
            return false;
        }
        for (Map.Entry<String, Object> entry : modifiers.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
                return false;
            }
            Object value = entry.getValue();
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                return false;
            }
        }
        return true;
    }
}
