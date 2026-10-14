package com.medivault.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.medivault.record.MedicalRecordRepository;
import com.medivault.user.UserRepository;

/**
 * Demo seeding must exist only for "demo" + the disposable in-memory "local-h2", so fictional
 * records can never be written to the persistent "local-file" database or to MySQL. Every other
 * combination that asks for "demo" logs a warning instead. Uses a lightweight context per profile
 * combination.
 */
class DemoProfileIsolationTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withBean(UserRepository.class, () -> mock(UserRepository.class))
		.withBean(MedicalRecordRepository.class, () -> mock(MedicalRecordRepository.class))
		.withUserConfiguration(DemoRecordSeeder.class, DemoProfileIgnoredWarning.class);

	@ParameterizedTest(name = "profiles [{0}] -> seeder: {1}, ignored-warning: {2}")
	@CsvSource(delimiter = '|', value = {
			"''              | false | false",
			"test            | false | false",
			"local-h2        | false | false",
			"demo            | false | true",
			"test,demo       | false | true",
			"demo,local-h2   | true  | false",
			"local-h2,demo   | true  | false",
			"local-file      | false | false",
			"demo,local-file | false | true",
			"local-file,demo | false | true",
			"mysql           | false | false",
			"mysql,demo      | false | true" })
	void seederExistsOnlyWithDemoAndTheInMemoryLocalH2Profile(String profiles, boolean seederExpected, boolean warningExpected) {
		runner.withPropertyValues("spring.profiles.active=" + profiles).run(context -> {
			assertThat(context.getBeanNamesForType(DemoRecordSeeder.class)).hasSize(seederExpected ? 1 : 0);
			assertThat(context.getBeanNamesForType(DemoProfileIgnoredWarning.class)).hasSize(warningExpected ? 1 : 0);
		});
	}

}
