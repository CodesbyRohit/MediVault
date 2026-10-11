package com.medivault.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Startup checks that decide which database the application may use. It runs as an
 * {@link EnvironmentPostProcessor}, i.e. before the application context exists, so a rejected
 * configuration can never open a database connection. Rules, in order:
 * <ol>
 * <li>A database profile must be chosen explicitly: {@code local-h2}, {@code mysql} or (tests
 * only) {@code test}.</li>
 * <li>{@code mysql} cannot be combined with {@code local-h2}, {@code test} or {@code demo}.</li>
 * <li>H2 (an H2 URL, or an empty URL, which Spring Boot would turn into an embedded database) is
 * only allowed with {@code local-h2} or {@code test}.</li>
 * <li>{@code local-h2} requires an in-memory H2 database ({@code jdbc:h2:mem:}), so an environment
 * override such as {@code SPRING_DATASOURCE_URL} cannot point it at MySQL.</li>
 * <li>Any other (non-H2) database requires {@code mysql}, so the {@code test} profile can never
 * bypass the MySQL rules below.</li>
 * <li>With {@code mysql}: every URL-bearing setting must pass {@link MySqlUrlPolicy}; JNDI and
 * pool-level URL overrides are refused; the application and migration accounts must belong to the
 * same database ({@code medivault_<env>_app} / {@code medivault_<env>_migrator}); Hibernate must
 * only {@code validate}; Flyway must be enabled, with clean disabled and no baseline-on-migrate.</li>
 * </ol>
 * Messages never contain JDBC URLs or credentials. Registered in {@code META-INF/spring.factories}.
 */
public class DatabaseSafetyGuard implements EnvironmentPostProcessor {

	static final String LOCAL_H2_PROFILE = "local-h2";

	static final String MYSQL_PROFILE = "mysql";

	static final String TEST_PROFILE = "test";

	static final Set<String> DATABASE_PROFILES = Set.of(LOCAL_H2_PROFILE, MYSQL_PROFILE, TEST_PROFILE);

	static final Set<String> PROFILES_ALLOWING_H2 = Set.of(LOCAL_H2_PROFILE, TEST_PROFILE);

	static final String FAILURE_MESSAGE = "Refusing to start: the configured database is H2 (spring.datasource.url "
			+ "is an H2 URL or is empty), but neither the 'local-h2' nor the 'test' profile is active. "
			+ "MediVault must use MySQL outside local development and tests. Start with the 'mysql' profile "
			+ "(isolated MySQL 8.4), or with the 'local-h2' profile for a local in-memory demo.";

	static final String LOCAL_H2_MISMATCH_MESSAGE = "Refusing to start: the 'local-h2' profile requires its "
			+ "in-memory H2 database (a jdbc:h2:mem: URL), but the configured datasource is different, most likely "
			+ "because a SPRING_DATASOURCE_URL environment variable overrides the profile. Clear the SPRING_DATASOURCE_* "
			+ "environment variables, or start without the 'local-h2' profile to use MySQL.";

	static final String NO_DATABASE_PROFILE_MESSAGE = "Refusing to start: no database profile is active. "
			+ "Start with the 'local-h2' profile (in-memory demo) or the 'mysql' profile (isolated MySQL 8.4).";

	static final String MYSQL_PROFILE_CONFLICT_MESSAGE = "Refusing to start: the 'mysql' profile cannot be combined "
			+ "with the 'local-h2', 'test' or 'demo' profiles.";

	static final String MYSQL_PROFILE_REQUIRED_MESSAGE = "Refusing to start: the configured database is not H2, "
			+ "but the 'mysql' profile is not active. Only the 'mysql' profile may use MySQL (or any other non-H2 "
			+ "database), so its connection, TLS and account rules always apply.";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		verify(environment);
	}

	static void verify(Environment environment) {
		// The URL itself is deliberately never included in a message: JDBC URLs can contain credentials.
		String url = property(environment, "spring.datasource.url");
		boolean anyDatabaseProfile = Arrays.stream(environment.getActiveProfiles())
			.anyMatch(DATABASE_PROFILES::contains);
		if (!anyDatabaseProfile) {
			throw new IllegalStateException(
					StringUtils.hasText(url) && selectsH2(url) ? FAILURE_MESSAGE : NO_DATABASE_PROFILE_MESSAGE);
		}
		boolean mysql = environment.matchesProfiles(MYSQL_PROFILE);
		if (mysql && environment.matchesProfiles(LOCAL_H2_PROFILE + " | " + TEST_PROFILE + " | demo")) {
			throw new IllegalStateException(MYSQL_PROFILE_CONFLICT_MESSAGE);
		}
		if (selectsH2(url) && !allowsH2(environment)) {
			throw new IllegalStateException(FAILURE_MESSAGE);
		}
		if (environment.matchesProfiles(LOCAL_H2_PROFILE) && !isInMemoryH2(url)) {
			throw new IllegalStateException(LOCAL_H2_MISMATCH_MESSAGE);
		}
		if (!selectsH2(url) && !mysql) {
			throw new IllegalStateException(MYSQL_PROFILE_REQUIRED_MESSAGE);
		}
		if (mysql) {
			verifyMySql(environment, url);
		}
	}

	private static void verifyMySql(Environment environment, String url) {
		MySqlUrlPolicy.Target target = MySqlUrlPolicy.parse(url, "spring.datasource.url");

		if (StringUtils.hasText(property(environment, "spring.datasource.jndi-name"))) {
			throw new IllegalStateException("Refusing to start: spring.datasource.jndi-name is not allowed "
					+ "with the 'mysql' profile.");
		}
		if (StringUtils.hasText(property(environment, "spring.datasource.hikari.jdbc-url"))) {
			throw new IllegalStateException("Refusing to start: spring.datasource.hikari.jdbc-url must not be set; "
					+ "only spring.datasource.url is allowed with the 'mysql' profile.");
		}
		String flywayUrl = property(environment, "spring.flyway.url");
		if (StringUtils.hasText(flywayUrl)) {
			MySqlUrlPolicy.Target flywayTarget = MySqlUrlPolicy.parse(flywayUrl, "spring.flyway.url");
			if (!flywayTarget.equals(target)) {
				throw new IllegalStateException("Refusing to start: spring.flyway.url must target the same "
						+ "MySQL port and database as spring.datasource.url.");
			}
		}

		String expectedApp = "medivault_" + target.environment() + "_app";
		String expectedMigrator = "medivault_" + target.environment() + "_migrator";
		if (!expectedApp.equals(property(environment, "spring.datasource.username"))) {
			throw new IllegalStateException("Refusing to start: the application account (spring.datasource.username) "
					+ "must be " + expectedApp + " for database " + target.database() + ".");
		}
		if (!expectedMigrator.equals(property(environment, "spring.flyway.user"))) {
			throw new IllegalStateException("Refusing to start: the migration account (spring.flyway.user) must be "
					+ expectedMigrator + " for database " + target.database() + ".");
		}

		if (!"validate".equals(lower(property(environment, "spring.jpa.hibernate.ddl-auto")))) {
			throw new IllegalStateException("Refusing to start: spring.jpa.hibernate.ddl-auto must be 'validate' "
					+ "with the 'mysql' profile; schema changes are made only by Flyway migrations.");
		}
		if (!"true".equals(lower(property(environment, "spring.flyway.enabled")))) {
			throw new IllegalStateException("Refusing to start: Flyway must be enabled with the 'mysql' profile.");
		}
		if ("false".equals(lower(property(environment, "spring.flyway.clean-disabled")))) {
			throw new IllegalStateException("Refusing to start: Flyway clean must stay disabled.");
		}
		if ("true".equals(lower(property(environment, "spring.flyway.baseline-on-migrate")))) {
			throw new IllegalStateException("Refusing to start: Flyway baseline-on-migrate is not allowed.");
		}
	}

	/** Reads a setting; an unresolvable ${...} placeholder becomes a clear, value-free refusal. */
	static String property(Environment environment, String key) {
		try {
			return environment.getProperty(key);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("Refusing to start: the setting '" + key
					+ "' refers to an environment variable that is not set.");
		}
	}

	static boolean selectsH2(String url) {
		return !StringUtils.hasText(url) || url.strip().toLowerCase(Locale.ROOT).startsWith("jdbc:h2:");
	}

	static boolean isInMemoryH2(String url) {
		return StringUtils.hasText(url) && url.strip().toLowerCase(Locale.ROOT).startsWith("jdbc:h2:mem:");
	}

	private static boolean allowsH2(Environment environment) {
		return Arrays.stream(environment.getActiveProfiles()).anyMatch(PROFILES_ALLOWING_H2::contains);
	}

	private static String lower(String value) {
		return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
	}

}
