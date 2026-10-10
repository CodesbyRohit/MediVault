package com.medivault.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.medivault.IntegrationTestSupport;
import com.medivault.admin.AdminAccountInitializer.Result;
import com.medivault.user.Role;
import com.medivault.user.User;

@ExtendWith(OutputCaptureExtension.class)
class AdminAccountInitializerTests extends IntegrationTestSupport {

	private static final String ADMIN_EMAIL = "Admin.Fictional@Example.TEST";

	private static final String ADMIN_PASSWORD = "AdminFictional42";

	@Autowired
	private AdminAccountInitializer initializer;

	@Test
	void nothingHappensWhenNotConfigured() {
		assertThat(initializer.bootstrap("", "")).isEqualTo(Result.NOT_CONFIGURED);
		assertThat(initializer.bootstrap(null, null)).isEqualTo(Result.NOT_CONFIGURED);
		assertThat(userRepository.count()).isZero();
	}

	@Test
	void bothValuesAreRequired() {
		assertThat(initializer.bootstrap(ADMIN_EMAIL, "")).isEqualTo(Result.INCOMPLETE);
		assertThat(initializer.bootstrap(" ", ADMIN_PASSWORD)).isEqualTo(Result.INCOMPLETE);
		assertThat(userRepository.count()).isZero();
	}

	@Test
	void invalidValuesAreRejectedWithoutLoggingThem(CapturedOutput output) {
		assertThat(initializer.bootstrap("not-an-email", ADMIN_PASSWORD)).isEqualTo(Result.INVALID);
		assertThat(initializer.bootstrap(ADMIN_EMAIL, "weakpass")).isEqualTo(Result.INVALID);
		assertThat(userRepository.count()).isZero();
		assertThat(output.getAll())
			.contains("MEDIVAULT_ADMIN_EMAIL is not a valid email address")
			.contains("MEDIVAULT_ADMIN_PASSWORD does not meet the password policy")
			.doesNotContain("not-an-email")
			.doesNotContain("weakpass")
			.doesNotContain(ADMIN_PASSWORD);
	}

	@Test
	void createsOneAdminWithAHashedPasswordAndLogsNoCredentials(CapturedOutput output) {
		assertThat(initializer.bootstrap(ADMIN_EMAIL, ADMIN_PASSWORD)).isEqualTo(Result.CREATED);

		User admin = userRepository.findByEmail("admin.fictional@example.test").orElseThrow();
		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(admin.isEnabled()).isTrue();
		assertThat(admin.getPasswordHash()).startsWith("$2").isNotEqualTo(ADMIN_PASSWORD);
		assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();

		assertThat(output.getAll())
			.contains("created administrator account id=" + admin.getId())
			.doesNotContain(ADMIN_PASSWORD)
			.doesNotContainIgnoringCase("admin.fictional@example.test")
			.doesNotContain(admin.getPasswordHash());
	}

	@Test
	void restartNeverOverwritesTheExistingAdmin() {
		initializer.bootstrap(ADMIN_EMAIL, ADMIN_PASSWORD);
		String originalHash = userRepository.findByEmail("admin.fictional@example.test").orElseThrow().getPasswordHash();

		assertThat(initializer.bootstrap(ADMIN_EMAIL, "ChangedPassword99")).isEqualTo(Result.ALREADY_EXISTS);

		assertThat(userRepository.count()).isEqualTo(1);
		assertThat(userRepository.findByEmail("admin.fictional@example.test").orElseThrow().getPasswordHash())
			.isEqualTo(originalHash);
	}

	@Test
	void anExistingPatientIsNeverElevatedToAdmin() {
		User patient = createPatient("Asha Fictional", "admin.fictional@example.test");

		assertThat(initializer.bootstrap(ADMIN_EMAIL, ADMIN_PASSWORD)).isEqualTo(Result.ALREADY_EXISTS);

		User reloaded = userRepository.findById(patient.getId()).orElseThrow();
		assertThat(reloaded.getRole()).isEqualTo(Role.PATIENT);
		assertThat(reloaded.getPasswordHash()).isEqualTo(patient.getPasswordHash());
	}

	@Test
	void bootstrappedAdminCanLogIn() throws Exception {
		initializer.bootstrap(ADMIN_EMAIL, ADMIN_PASSWORD);

		mockMvc
			.perform(formLogin("/login").userParameter("email").user("admin.fictional@example.test").password(ADMIN_PASSWORD))
			.andExpect(redirectedUrl("/admin"))
			.andExpect(authenticated().withRoles("ADMIN"));
	}

}
