package com.medivault.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import com.medivault.MedivaultApplication;

class H2DatabaseGuardTests {

	private static final String MYSQL_URL = "jdbc:mysql://localhost:3306/medivault";

	private static final String H2_URL = "jdbc:h2:mem:anything";

	private static MockEnvironment environment(String url, String... profiles) {
		MockEnvironment environment = new MockEnvironment();
		if (url != null) {
			environment.setProperty("spring.datasource.url", url);
		}
		environment.setActiveProfiles(profiles);
		return environment;
	}

	@Test
	void mysqlIsAllowedWithoutAnyProfile() {
		assertThatCode(() -> H2DatabaseGuard.verify(environment(MYSQL_URL))).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = { "local-h2", "test" })
	void h2IsAllowedForLocalDemoAndTests(String profile) {
		assertThatCode(() -> H2DatabaseGuard.verify(environment(H2_URL, profile))).doesNotThrowAnyException();
	}

	@Test
	void h2WithoutAnAllowedProfileIsRefused() {
		assertThatThrownBy(() -> H2DatabaseGuard.verify(environment(H2_URL)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Refusing to start");
		assertThatThrownBy(() -> H2DatabaseGuard.verify(environment(" JDBC:H2:file:./data", "prod", "demo")))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void emptyOrMissingUrlIsTreatedAsEmbeddedH2AndRefused() {
		// Spring Boot falls back to an embedded database when the URL is empty.
		assertThatThrownBy(() -> H2DatabaseGuard.verify(environment(""))).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> H2DatabaseGuard.verify(environment(null))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void failureMessageNeverContainsTheJdbcUrl() {
		String urlWithSecret = "jdbc:h2:mem:x;PASSWORD=not-a-real-secret";
		assertThatThrownBy(() -> H2DatabaseGuard.verify(environment(urlWithSecret)))
			.message()
			.doesNotContain("not-a-real-secret");
	}

	@Test
	void localH2WithAMysqlOverrideIsRefusedWithoutRevealingTheUrl() {
		String overriddenUrl = "jdbc:mysql://db.example.test:3306/medivault?password=not-a-real-secret";
		assertThatThrownBy(() -> H2DatabaseGuard.verify(environment(overriddenUrl, "local-h2")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage(H2DatabaseGuard.LOCAL_H2_MISMATCH_MESSAGE)
			.message()
			.doesNotContain("not-a-real-secret")
			.doesNotContain("db.example.test");
	}

	@Test
	void localH2AndDemoWithAMysqlOverrideIsRefused() {
		// Demo seeding needs "local-h2", so this is the path that could otherwise write demo records to MySQL.
		assertThatThrownBy(() -> H2DatabaseGuard.verify(environment(MYSQL_URL, "local-h2", "demo")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage(H2DatabaseGuard.LOCAL_H2_MISMATCH_MESSAGE);
	}

	@Test
	void localH2WithItsInMemoryDatabaseIsAllowed() {
		String localUrl = "jdbc:h2:mem:medivault_local;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
		assertThatCode(() -> H2DatabaseGuard.verify(environment(localUrl, "local-h2"))).doesNotThrowAnyException();
		assertThatCode(() -> H2DatabaseGuard.verify(environment(localUrl, "local-h2", "demo")))
			.doesNotThrowAnyException();
	}

	@Test
	void guardIsRegisteredAndStopsARealStartupBeforeAnyContextExists() {
		SpringApplicationBuilder app = new SpringApplicationBuilder(MedivaultApplication.class)
			.web(WebApplicationType.NONE);
		assertThatThrownBy(() -> {
			try (ConfigurableApplicationContext context = app.run("--spring.datasource.url=jdbc:h2:mem:guard_check",
					"--spring.profiles.active=guard-check", "--spring.main.banner-mode=off")) {
				assertThat(context).as("context must not start").isNull();
			}
		}).isInstanceOf(IllegalStateException.class).hasMessageContaining("Refusing to start");
	}

}
