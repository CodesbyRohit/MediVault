package com.medivault.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import com.medivault.IntegrationTestSupport;
import com.medivault.record.MedicalRecord;
import com.medivault.user.User;

/**
 * Runs with profiles "test" + "demo" + "local-h2" (profiles are inherited and merged). The
 * test H2 database still wins because {@code @TestPropertySource} outranks profile files.
 */
@ActiveProfiles({ "demo", "local-h2" })
class DemoRecordSeederTests extends IntegrationTestSupport {

	@Autowired
	private DemoRecordSeeder seeder;

	@Autowired
	private Environment environment;

	@Autowired
	private DataSource dataSource;

	private void register(String email) throws Exception {
		mockMvc.perform(post("/register").with(csrf())
			.param("fullName", "Demo Fictional")
			.param("email", email)
			.param("password", PASSWORD)
			.param("confirmPassword", PASSWORD))
			.andExpect(redirectedUrl("/login?registered"));
	}

	@Test
	void demoIsActiveOnlyBecauseThisTestAskedForDemoAndLocalH2() {
		assertThat(environment.getActiveProfiles()).containsExactlyInAnyOrder("test", "demo", "local-h2");
	}

	@Test
	void testDatabaseStillWinsOverTheLocalH2ProfileDatabase() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:medivault_test");
		}
	}

	@Test
	void registrationCreatesThreeClearlyFictionalRecords() throws Exception {
		register("demo@example.test");

		User patient = userRepository.findByEmail("demo@example.test").orElseThrow();
		var records = recordRepository.findAllByOwnerIdOrderByRecordDateDescIdDesc(patient.getId());
		assertThat(records).hasSize(3);
		assertThat(records).extracting(MedicalRecord::getTitle).allMatch(title -> title.contains("(fictional)"));
		assertThat(records).extracting(MedicalRecord::getProviderName)
			.containsOnly(DemoRecordSeeder.DEMO_PROVIDER);
	}

	@Test
	void seedingHappensAtMostOncePerAccount() throws Exception {
		register("demo@example.test");
		Long id = userRepository.findByEmail("demo@example.test").orElseThrow().getId();

		assertThat(seeder.seedFor(id)).isZero();
		assertThat(seeder.seedFor(id)).isZero();

		assertThat(recordRepository.countByOwnerId(id)).isEqualTo(3);
	}

	@Test
	void eachPatientGetsTheirOwnSamples() throws Exception {
		register("first@example.test");
		register("second@example.test");

		assertThat(recordRepository.count()).isEqualTo(6);
	}

	@Test
	void adminsAreNotEligibleForSampleRecords() {
		User admin = createAdmin("Admin Fictional", "admin@example.test");

		assertThat(seeder.seedFor(admin.getId())).isZero();
		assertThat(recordRepository.count()).isZero();
	}

}
