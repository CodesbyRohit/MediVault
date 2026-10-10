package com.medivault.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.medivault.IntegrationTestSupport;

class RegistrationTests extends IntegrationTestSupport {

	private static MockHttpServletRequestBuilder registration(String name, String email, String password,
			String confirm) {
		return post("/register").with(csrf())
			.param("fullName", name)
			.param("email", email)
			.param("password", password)
			.param("confirmPassword", confirm);
	}

	@Test
	void validRegistrationCreatesExactlyOneEnabledPatient() throws Exception {
		mockMvc.perform(registration("  Asha Fictional ", " Asha@Example.TEST ", PASSWORD, PASSWORD))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login?registered"));

		assertThat(userRepository.count()).isEqualTo(1);
		User saved = userRepository.findByEmail("asha@example.test").orElseThrow();
		assertThat(saved.getFullName()).isEqualTo("Asha Fictional");
		assertThat(saved.getRole()).isEqualTo(Role.PATIENT);
		assertThat(saved.isEnabled()).isTrue();
		assertThat(saved.getCreatedAt()).isNotNull();
	}

	@Test
	void passwordIsStoredOnlyAsABcryptHash() throws Exception {
		mockMvc.perform(registration("Asha Fictional", "asha@example.test", PASSWORD, PASSWORD));

		String hash = userRepository.findByEmail("asha@example.test").orElseThrow().getPasswordHash();
		assertThat(hash).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD).startsWith("$2");
		assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
	}

	@Test
	void submittedAdminRoleIsIgnored() throws Exception {
		mockMvc.perform(registration("Mallory Fictional", "mallory@example.test", PASSWORD, PASSWORD)
			.param("role", "ADMIN")
			.param("enabled", "false")
			.param("id", "999"))
			.andExpect(redirectedUrl("/login?registered"));

		User saved = userRepository.findByEmail("mallory@example.test").orElseThrow();
		assertThat(saved.getRole()).isEqualTo(Role.PATIENT);
		assertThat(saved.isEnabled()).isTrue();
		assertThat(saved.getId()).isNotEqualTo(999L);
	}

	@Test
	void invalidFieldsShowSpecificErrorsAndSaveNothing() throws Exception {
		mockMvc.perform(registration("A", "not-an-email", "short", "different"))
			.andExpect(status().isOk())
			.andExpect(view().name("auth/register"))
			.andExpect(model().attributeHasFieldErrors("registrationForm", "fullName", "email", "password"))
			.andExpect(content().string(allOf(
					containsString("Full name must be between 2 and 100 characters."),
					containsString("Enter a valid email address"),
					containsString(PasswordPolicy.MESSAGE),
					containsString("aria-invalid=\"true\""),
					containsString("Please correct the following"))));

		assertThat(userRepository.count()).isZero();
	}

	@Test
	void blankFormShowsRequiredFieldMessages() throws Exception {
		mockMvc.perform(registration("", "", "", ""))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("registrationForm", "fullName", "email", "password",
					"confirmPassword"))
			.andExpect(content().string(allOf(containsString("Enter your email address."),
					containsString("Confirm your password."))));

		assertThat(userRepository.count()).isZero();
	}

	@Test
	void passwordOverTheBcryptByteLimitGetsAnAccurateMessage() throws Exception {
		String tooManyBytes = "a1" + "é".repeat(36); // 38 characters, 74 bytes
		mockMvc.perform(registration("Asha Fictional", "asha@example.test", tooManyBytes, tooManyBytes))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("registrationForm", "password"))
			.andExpect(content().string(allOf(containsString("must fit in 72 bytes"),
					not(containsString(PasswordPolicy.MESSAGE)))));

		assertThat(userRepository.count()).isZero();
	}

	@Test
	void mismatchedConfirmationIsRejected() throws Exception {
		mockMvc.perform(registration("Asha Fictional", "asha@example.test", PASSWORD, "Fictional999"))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("registrationForm", "confirmPassword"))
			.andExpect(content().string(containsString("Passwords do not match.")));

		assertThat(userRepository.count()).isZero();
	}

	@Test
	void reDisplayedFormKeepsNameAndEmailButNeverThePassword() throws Exception {
		mockMvc.perform(registration("Asha Fictional", "asha@example.test", PASSWORD, "Mismatch999"))
			.andExpect(content().string(allOf(
					containsString("value=\"Asha Fictional\""),
					containsString("value=\"asha@example.test\""),
					not(containsString(PASSWORD)),
					not(containsString("Mismatch999")))));
	}

	@Test
	void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
		createPatient("Existing Fictional", "taken@example.test");

		mockMvc.perform(registration("Another Fictional", "TAKEN@example.test", PASSWORD, PASSWORD))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrorCode("registrationForm", "email", "duplicate"))
			.andExpect(content().string(containsString("An account with this email already exists.")));

		assertThat(userRepository.count()).isEqualTo(1);
		assertThat(userRepository.findByEmail("taken@example.test").orElseThrow().getFullName())
			.isEqualTo("Existing Fictional");
	}

	@Test
	void registrationWithoutCsrfTokenIsForbidden() throws Exception {
		mockMvc.perform(post("/register")
			.param("fullName", "Asha Fictional")
			.param("email", "asha@example.test")
			.param("password", PASSWORD)
			.param("confirmPassword", PASSWORD))
			.andExpect(status().isForbidden());

		assertThat(userRepository.count()).isZero();
	}

}
