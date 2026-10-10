package com.medivault.user;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Bean Validation constraint backed by {@link PasswordPolicy}. A {@code null} value is invalid.
 */
@Documented
@Constraint(validatedBy = ValidPassword.Validator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

	String message() default PasswordPolicy.MESSAGE;

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<ValidPassword, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			String message = PasswordPolicy.violationMessage(value);
			if (message == null) {
				return true;
			}
			// Report the specific reason (general policy vs. the 72-byte limit).
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
			return false;
		}

	}

}
