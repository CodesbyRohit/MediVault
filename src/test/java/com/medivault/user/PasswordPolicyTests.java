package com.medivault.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTests {

	@ParameterizedTest
	@ValueSource(strings = { "Fictional1", "abcdefg1", "12345678a", "pass word 9" })
	void acceptsPasswordsThatMeetThePolicy(String password) {
		assertThat(PasswordPolicy.isSatisfiedBy(password)).isTrue();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "short1", "onlyletters", "12345678", "        " })
	void rejectsPasswordsThatBreakThePolicy(String password) {
		assertThat(PasswordPolicy.isSatisfiedBy(password)).isFalse();
	}

	@Test
	void enforcesTheLengthBoundaries() {
		assertThat(PasswordPolicy.isSatisfiedBy("a1" + "x".repeat(5))).isFalse(); // 7
		assertThat(PasswordPolicy.isSatisfiedBy("a1" + "x".repeat(6))).isTrue(); // 8
		assertThat(PasswordPolicy.isSatisfiedBy("a1" + "x".repeat(62))).isTrue(); // 64
		assertThat(PasswordPolicy.isSatisfiedBy("a1" + "x".repeat(63))).isFalse(); // 65
	}

	@Test
	void rejectsPasswordsLongerThanBcryptsByteLimit() {
		// Under 64 characters, but each "é" is 2 bytes in UTF-8: 2 + 2*35 = 72 bytes is fine, 74 is not.
		assertThat(PasswordPolicy.isSatisfiedBy("a1" + "é".repeat(35))).isTrue();
		assertThat(PasswordPolicy.isSatisfiedBy("a1" + "é".repeat(36))).isFalse();
	}

	@Test
	void explainsTheByteLimitOnlyWhenItIsTheActualProblem() {
		assertThat(PasswordPolicy.violationMessage("Fictional123")).isNull();
		assertThat(PasswordPolicy.violationMessage("a1" + "é".repeat(36))).isEqualTo(PasswordPolicy.BYTE_LIMIT_MESSAGE);
		assertThat(PasswordPolicy.violationMessage("é".repeat(40))).as("no digit").isEqualTo(PasswordPolicy.MESSAGE);
		assertThat(PasswordPolicy.violationMessage("short1")).isEqualTo(PasswordPolicy.MESSAGE);
		assertThat(PasswordPolicy.BYTE_LIMIT_MESSAGE).contains("72 bytes");
	}

}
