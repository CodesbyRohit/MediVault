package com.medivault.user;

import java.nio.charset.StandardCharsets;

/**
 * The single password policy shared by registration and the admin bootstrap:
 * 8–64 characters, at least one letter and one digit, and at most 72 UTF-8 bytes
 * (BCrypt cannot safely hash more than 72 bytes of input).
 */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 8;

	public static final int MAX_LENGTH = 64;

	public static final int MAX_BYTES = 72;

	public static final String MESSAGE = "Password must be 8–64 characters and include at least one letter and one number.";

	public static final String BYTE_LIMIT_MESSAGE = "Password is too long for secure storage: it must fit in 72 bytes. "
			+ "Accented letters, non-Latin characters and emoji use 2–4 bytes each, so use a shorter password.";

	private PasswordPolicy() {
	}

	public static boolean isSatisfiedBy(String password) {
		return violationMessage(password) == null;
	}

	/**
	 * @return {@code null} if the password is acceptable, otherwise the message to show
	 * ({@link #BYTE_LIMIT_MESSAGE} only when the byte limit is the sole problem)
	 */
	public static String violationMessage(String password) {
		if (password == null) {
			return MESSAGE;
		}
		int length = password.codePointCount(0, password.length());
		boolean hasLetter = password.codePoints().anyMatch(Character::isLetter);
		boolean hasDigit = password.codePoints().anyMatch(Character::isDigit);
		if (length < MIN_LENGTH || length > MAX_LENGTH || !hasLetter || !hasDigit) {
			return MESSAGE;
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
			return BYTE_LIMIT_MESSAGE;
		}
		return null;
	}

}
