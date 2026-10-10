package com.medivault.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

import com.medivault.IntegrationTestSupport;
import com.medivault.user.Role;
import com.medivault.user.User;

/**
 * Exercises the real lifecycle path: configuration properties (the same ones that
 * MEDIVAULT_ADMIN_EMAIL / MEDIVAULT_ADMIN_PASSWORD feed) -> Spring bean -> {@code run()}.
 * These inlined values override the blank ones from {@link IntegrationTestSupport}.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = { "medivault.admin.email=Run.Admin@Example.TEST",
		"medivault.admin.password=RunAdminFictional7" })
class AdminAccountInitializerRunTests extends IntegrationTestSupport {

	private static final String EMAIL = "run.admin@example.test";

	private static final String PASSWORD_VALUE = "RunAdminFictional7";

	@Autowired
	private AdminAccountInitializer initializer;

	@Test
	void runCreatesTheConfiguredAdminWithoutLoggingCredentials(CapturedOutput output) throws Exception {
		// The database is emptied before each test, so this run() starts from a clean slate.
		initializer.run(new DefaultApplicationArguments());

		User admin = userRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(passwordEncoder.matches(PASSWORD_VALUE, admin.getPasswordHash())).isTrue();
		assertThat(output.getAll()).contains("created administrator account id=" + admin.getId())
			.doesNotContain(PASSWORD_VALUE)
			.doesNotContainIgnoringCase(EMAIL)
			.doesNotContain(admin.getPasswordHash());
	}

	@Test
	void repeatedRunsNeverOverwriteTheAccount() throws Exception {
		initializer.run(new DefaultApplicationArguments());
		User first = userRepository.findByEmail(EMAIL).orElseThrow();

		initializer.run(new DefaultApplicationArguments());

		assertThat(userRepository.count()).isEqualTo(1);
		User again = userRepository.findByEmail(EMAIL).orElseThrow();
		assertThat(again.getId()).isEqualTo(first.getId());
		assertThat(again.getPasswordHash()).isEqualTo(first.getPasswordHash());
	}

	@Test
	void runNeverPromotesAnExistingPatientWithTheConfiguredEmail() throws Exception {
		User patient = createPatient("Asha Fictional", EMAIL);

		initializer.run(new DefaultApplicationArguments());

		User reloaded = userRepository.findById(patient.getId()).orElseThrow();
		assertThat(reloaded.getRole()).isEqualTo(Role.PATIENT);
		assertThat(reloaded.getPasswordHash()).isEqualTo(patient.getPasswordHash());
	}

}
