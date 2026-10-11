package com.medivault.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import com.medivault.MedivaultApplication;

/**
 * Pure rule tests for {@link DatabaseSafetyGuard} (no Spring context, no network). Proof that
 * rejected configurations never attempt a connection is in {@link DatabaseGuardNoConnectionTests}.
 */
class DatabaseSafetyGuardTests {

	private static final String LEGACY_MYSQL_URL = "jdbc:mysql://localhost:3306/medivault";

	private static final String H2_URL = "jdbc:h2:mem:anything";

	private static final String DEV_URL = "jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA";

	private static final String IT_URL = "jdbc:mysql://127.0.0.1:3307/medivault_it?sslMode=VERIFY_CA";

	private static MockEnvironment environment(String url, String... profiles) {
		MockEnvironment environment = new MockEnvironment();
		if (url != null) {
			environment.setProperty("spring.datasource.url", url);
		}
		environment.setActiveProfiles(profiles);
		return environment;
	}

	/** A configuration that satisfies every 'mysql' rule; tests then break exactly one thing. */
	private static MockEnvironment validMySql(String url, String env) {
		MockEnvironment environment = environment(url, "mysql");
		environment.setProperty("spring.datasource.username", "medivault_" + env + "_app");
		environment.setProperty("spring.flyway.user", "medivault_" + env + "_migrator");
		environment.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
		environment.setProperty("spring.flyway.enabled", "true");
		environment.setProperty("spring.flyway.clean-disabled", "true");
		environment.setProperty("spring.flyway.baseline-on-migrate", "false");
		return environment;
	}

	private static MockEnvironment validDev(Consumer<MockEnvironment> change) {
		MockEnvironment environment = validMySql(DEV_URL, "dev");
		change.accept(environment);
		return environment;
	}

	// --- Database profile selection -------------------------------------------------

	@Test
	void aDatabaseProfileMustBeChosen() {
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(null)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage(DatabaseSafetyGuard.NO_DATABASE_PROFILE_MESSAGE);
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(DEV_URL, "prod")))
			.hasMessage(DatabaseSafetyGuard.NO_DATABASE_PROFILE_MESSAGE);
	}

	@Test
	void mysqlUrlWithoutTheMysqlProfileIsRefused() {
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(LEGACY_MYSQL_URL)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage(DatabaseSafetyGuard.NO_DATABASE_PROFILE_MESSAGE);
	}

	@Test
	void testProfileRefusesAMysqlUrlOnPort3306() {
		// Regression: 'test' used to accept any non-H2 URL, bypassing every 'mysql' rule.
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(LEGACY_MYSQL_URL, "test")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage(DatabaseSafetyGuard.MYSQL_PROFILE_REQUIRED_MESSAGE);
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(LEGACY_MYSQL_URL, "test", "demo")))
			.hasMessage(DatabaseSafetyGuard.MYSQL_PROFILE_REQUIRED_MESSAGE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "jdbc:mysql://127.0.0.1:3307/medivault_it", IT_URL,
			"jdbc:mariadb://127.0.0.1:3307/medivault_it?sslMode=VERIFY_CA", "jdbc:postgresql://127.0.0.1:5432/medivault" })
	void nonH2UrlsRequireTheMysqlProfileEvenUnderTest(String url) {
		// Even a policy-compliant URL needs 'mysql', so the account, TLS and Flyway rules always apply.
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(url, "test")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage(DatabaseSafetyGuard.MYSQL_PROFILE_REQUIRED_MESSAGE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "local-h2", "test", "demo" })
	void mysqlCannotBeCombinedWithLocalOrTestProfiles(String other) {
		MockEnvironment environment = validMySql(DEV_URL, "dev");
		environment.setActiveProfiles("mysql", other);
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment))
			.hasMessage(DatabaseSafetyGuard.MYSQL_PROFILE_CONFLICT_MESSAGE);
	}

	// --- H2 rules (unchanged from Milestone 1) ---------------------------------------

	@ParameterizedTest
	@ValueSource(strings = { "local-h2", "test" })
	void h2IsAllowedForLocalDemoAndTests(String profile) {
		assertThatCode(() -> DatabaseSafetyGuard.verify(environment(H2_URL, profile))).doesNotThrowAnyException();
	}

	@Test
	void h2WithoutAnAllowedProfileIsRefused() {
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(H2_URL)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage(DatabaseSafetyGuard.FAILURE_MESSAGE);
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(" JDBC:H2:file:./data", "prod", "demo")))
			.hasMessage(DatabaseSafetyGuard.FAILURE_MESSAGE);
		MockEnvironment mysqlWithH2 = validMySql(H2_URL, "dev");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(mysqlWithH2)).hasMessage(DatabaseSafetyGuard.FAILURE_MESSAGE);
	}

	@Test
	void emptyOrMissingUrlIsRefused() {
		// Spring Boot would fall back to an embedded database when the URL is empty.
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(""))).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(null))).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validMySql("", "dev")))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void failureMessageNeverContainsTheJdbcUrl() {
		String urlWithSecret = "jdbc:h2:mem:x;PASSWORD=not-a-real-secret";
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(urlWithSecret)))
			.message()
			.doesNotContain("not-a-real-secret");
	}

	@Test
	void localH2WithAMysqlOverrideIsRefusedWithoutRevealingTheUrl() {
		String overriddenUrl = "jdbc:mysql://db.example.test:3306/medivault?password=not-a-real-secret";
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(overriddenUrl, "local-h2")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage(DatabaseSafetyGuard.LOCAL_H2_MISMATCH_MESSAGE)
			.message()
			.doesNotContain("not-a-real-secret")
			.doesNotContain("db.example.test");
	}

	@Test
	void localH2AndDemoWithAMysqlOverrideIsRefused() {
		// Demo seeding needs "local-h2", so this is the path that could otherwise write demo records to MySQL.
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(LEGACY_MYSQL_URL, "local-h2", "demo")))
			.hasMessage(DatabaseSafetyGuard.LOCAL_H2_MISMATCH_MESSAGE);
	}

	@Test
	void localH2WithItsInMemoryDatabaseIsAllowed() {
		String localUrl = "jdbc:h2:mem:medivault_local;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
		assertThatCode(() -> DatabaseSafetyGuard.verify(environment(localUrl, "local-h2"))).doesNotThrowAnyException();
		assertThatCode(() -> DatabaseSafetyGuard.verify(environment(localUrl, "local-h2", "demo")))
			.doesNotThrowAnyException();
	}

	// --- 'mysql' profile rules -------------------------------------------------------

	@Test
	void validDevAndItConfigurationsAreAccepted() {
		assertThatCode(() -> DatabaseSafetyGuard.verify(validMySql(DEV_URL, "dev"))).doesNotThrowAnyException();
		assertThatCode(() -> DatabaseSafetyGuard.verify(validMySql(IT_URL, "it"))).doesNotThrowAnyException();
	}

	@Test
	void urlIsCheckedByTheMySqlPolicy() {
		MockEnvironment legacy = validMySql(LEGACY_MYSQL_URL, "dev");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(legacy)).hasMessageContaining("MySQL connection policy");
	}

	@Test
	void accountsMustBelongToTheTargetDatabase() {
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validDev(e -> e.setProperty("spring.datasource.username",
				"medivault_it_app"))))
			.hasMessageContaining("application account");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validDev(e -> e.setProperty("spring.flyway.user",
				"medivault_it_migrator"))))
			.hasMessageContaining("migration account");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validDev(e -> e.setProperty("spring.datasource.username",
				"root"))))
			.hasMessageContaining("application account");
		// Integration-test accounts can never be pointed at the development database, and vice versa.
		MockEnvironment itAccountsOnDev = validMySql(DEV_URL, "it");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(itAccountsOnDev)).isInstanceOf(IllegalStateException.class);
		MockEnvironment devAccountsOnIt = validMySql(IT_URL, "dev");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(devAccountsOnIt)).isInstanceOf(IllegalStateException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "update", "create", "create-drop", "none" })
	void hibernateMayOnlyValidate(String ddlAuto) {
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validDev(e -> e.setProperty("spring.jpa.hibernate.ddl-auto",
				ddlAuto))))
			.hasMessageContaining("ddl-auto must be 'validate'");
	}

	@Test
	void flywayMustBeEnabledWithoutCleanOrBaseline() {
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validDev(e -> e.setProperty("spring.flyway.enabled",
				"false"))))
			.hasMessageContaining("Flyway must be enabled");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validDev(e -> e.setProperty("spring.flyway.clean-disabled",
				"false"))))
			.hasMessageContaining("clean must stay disabled");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validDev(e -> e.setProperty(
				"spring.flyway.baseline-on-migrate", "true"))))
			.hasMessageContaining("baseline-on-migrate");
	}

	@Test
	void alternativeConnectionSettingsAreRefusedOrChecked() {
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validDev(e -> e.setProperty("spring.datasource.jndi-name",
				"java:comp/env/jdbc/x"))))
			.hasMessageContaining("jndi-name");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validDev(e -> e.setProperty(
				"spring.datasource.hikari.jdbc-url", DEV_URL))))
			.hasMessageContaining("hikari.jdbc-url");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validDev(e -> e.setProperty("spring.flyway.url",
				LEGACY_MYSQL_URL))))
			.hasMessageContaining("spring.flyway.url");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validDev(e -> e.setProperty("spring.flyway.url", IT_URL))))
			.hasMessageContaining("same MySQL port and database");
		assertThatCode(() -> DatabaseSafetyGuard.verify(validDev(e -> e.setProperty("spring.flyway.url", DEV_URL))))
			.doesNotThrowAnyException();
	}

	@Test
	void unsetEnvironmentVariablesAreRefusedWithoutValues() {
		MockEnvironment environment = validMySql("${MEDIVAULT_TEST_UNSET_VARIABLE_FOR_GUARD}", "dev");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Refusing to start: the setting 'spring.datasource.url' refers to an environment variable "
					+ "that is not set.");
	}

	@Test
	void mysqlRejectionMessagesNeverContainTheUrlOrCredentials() {
		String url = "jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA&password=not-a-real-secret";
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validMySql(url, "dev")))
			.message()
			.doesNotContain("not-a-real-secret")
			.doesNotContain("jdbc:mysql://127.0.0.1");
	}

	// --- Registration ----------------------------------------------------------------

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
