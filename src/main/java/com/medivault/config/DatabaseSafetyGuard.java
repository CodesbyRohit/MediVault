package com.medivault.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Startup checks that decide which database the application may use. It runs as an
 * {@link EnvironmentPostProcessor}, i.e. before the application context exists, so a rejected
 * configuration can never open a database connection. Rules, in order:
 * <ol>
 * <li>A database profile must be chosen explicitly: {@code local-h2}, {@code local-file},
 * {@code mysql} or (tests only) {@code test}.</li>
 * <li>{@code mysql} cannot be combined with {@code local-h2}, {@code local-file}, {@code test} or
 * {@code demo}; {@code local-file} cannot be combined with {@code local-h2} or {@code test}.</li>
 * <li>In every profile, only {@code spring.datasource.url} may choose the database: JNDI and other
 * pool types are refused, and {@code spring.datasource.hikari.*} and {@code spring.flyway.*} are
 * allow-listed (no driver properties, data source classes, accounts or connection SQL; a separate
 * Flyway URL only with {@code mysql}).</li>
 * <li>H2 (an H2 URL, or an empty URL, which Spring Boot would turn into an embedded database) is
 * only allowed with {@code local-h2}, {@code local-file} or {@code test}.</li>
 * <li>{@code local-h2} requires an in-memory H2 database ({@code jdbc:h2:mem:}), so an environment
 * override such as {@code SPRING_DATASOURCE_URL} cannot point it at MySQL.</li>
 * <li>With {@code local-file}: the URL, storage root and PDF folder must pass
 * {@link LocalFileStoragePolicy} (a {@code jdbc:h2:file:} database directly inside
 * {@code <root>/data}); and the schema rules of the {@code mysql} profile apply (Hibernate
 * {@code validate}, Flyway on with the packaged migrations only, no clean, no baseline-on-migrate,
 * no other schema generation), so persistent data is never dropped.</li>
 * <li>Any other non-in-memory H2 database requires {@code local-file}: a {@code test} run can never
 * open a file-backed database.</li>
 * <li>Any other (non-H2) database requires {@code mysql}, so the {@code test} profile can never
 * bypass the MySQL rules below.</li>
 * <li>With {@code mysql}: every URL-bearing setting must pass {@link MySqlUrlPolicy}; the
 * application and migration accounts must belong to the
 * same database ({@code medivault_<env>_app} / {@code medivault_<env>_migrator}); Hibernate must
 * only {@code validate}; Flyway must be enabled, with clean disabled and no baseline-on-migrate.</li>
 * </ol>
 * Messages never contain JDBC URLs, paths or credentials. Registered in
 * {@code META-INF/spring.factories}.
 */
public class DatabaseSafetyGuard implements EnvironmentPostProcessor {

	static final String LOCAL_H2_PROFILE = "local-h2";

	static final String LOCAL_FILE_PROFILE = "local-file";

	static final String MYSQL_PROFILE = "mysql";

	static final String TEST_PROFILE = "test";

	static final Set<String> DATABASE_PROFILES = Set.of(LOCAL_H2_PROFILE, LOCAL_FILE_PROFILE, MYSQL_PROFILE,
			TEST_PROFILE);

	static final Set<String> PROFILES_ALLOWING_H2 = Set.of(LOCAL_H2_PROFILE, LOCAL_FILE_PROFILE, TEST_PROFILE);

	/**
	 * The only {@code spring.datasource.hikari.*} settings accepted (uniform form: lower case, no
	 * dashes): pool sizing and timeouts. Everything else is refused, because HikariCP can pass driver
	 * properties ({@code data-source-properties.*}, e.g. an H2 {@code INIT} script), switch to another
	 * data source class or URL, override the account, or run SQL on every connection
	 * ({@code connection-init-sql}, {@code connection-test-query}), all outside the checked URL.
	 */
	static final Set<String> ALLOWED_POOL_SETTINGS = Set.of("poolname", "maximumpoolsize", "minimumidle",
			"connectiontimeout", "idletimeout", "maxlifetime", "keepalivetime", "validationtimeout",
			"leakdetectionthreshold", "initializationfailtimeout", "registermbeans", "autocommit");

	/**
	 * The only {@code spring.flyway.*} settings accepted (uniform form). Refused, for example:
	 * {@code init-sqls} (SQL on every Flyway connection) and {@code jdbc-properties.*}. {@code url}
	 * is accepted only with the {@code mysql} profile, where it must match the checked target.
	 */
	static final Set<String> ALLOWED_FLYWAY_SETTINGS = Set.of("enabled", "locations", "user", "password", "url",
			"baselineonmigrate", "cleandisabled", "validateonmigrate", "failonmissinglocations");

	/** Persistent profiles may only migrate from the versioned scripts packaged with the application. */
	static final String MIGRATION_LOCATIONS = "classpath:db/migration/{vendor}";

	/** JPA/Hibernate settings that could create or drop tables outside Flyway, besides ddl-auto. */
	static final List<String> SCHEMA_GENERATION_SETTINGS = List.of(
			"spring.jpa.properties.hibernate.hbm2ddl.auto",
			"spring.jpa.properties.jakarta.persistence.schema-generation.database.action",
			"spring.jpa.properties.javax.persistence.schema-generation.database.action");

	static final String FAILURE_MESSAGE = "Refusing to start: the configured database is H2 (spring.datasource.url "
			+ "is an H2 URL or is empty), but none of the 'local-h2', 'local-file' or 'test' profiles is active. "
			+ "MediVault must use MySQL outside local development and tests. Start with the 'mysql' profile "
			+ "(isolated MySQL 8.4), the 'local-h2' profile for a disposable in-memory demo, or the 'local-file' "
			+ "profile for persistent local storage.";

	static final String LOCAL_H2_MISMATCH_MESSAGE = "Refusing to start: the 'local-h2' profile requires its "
			+ "in-memory H2 database (a jdbc:h2:mem: URL), but the configured datasource is different, most likely "
			+ "because a SPRING_DATASOURCE_URL environment variable overrides the profile. Clear the SPRING_DATASOURCE_* "
			+ "environment variables, or start without the 'local-h2' profile to use MySQL.";

	static final String NO_DATABASE_PROFILE_MESSAGE = "Refusing to start: no database profile is active. "
			+ "Start with the 'local-h2' profile (in-memory demo), the 'local-file' profile (persistent local "
			+ "storage) or the 'mysql' profile (isolated MySQL 8.4).";

	static final String MYSQL_PROFILE_CONFLICT_MESSAGE = "Refusing to start: the 'mysql' profile cannot be combined "
			+ "with the 'local-h2', 'local-file', 'test' or 'demo' profiles.";

	static final String LOCAL_FILE_PROFILE_CONFLICT_MESSAGE = "Refusing to start: the 'local-file' profile cannot "
			+ "be combined with the 'local-h2' or 'test' profiles.";

	static final String FILE_H2_REQUIRES_LOCAL_FILE_MESSAGE = "Refusing to start: the configured H2 database is not "
			+ "in-memory, but the 'local-file' profile is not active. Only the 'local-file' profile may open a "
			+ "persistent H2 database, so its storage-location and schema rules always apply.";

	static final String MYSQL_PROFILE_REQUIRED_MESSAGE = "Refusing to start: the configured database is not H2, "
			+ "but the 'mysql' profile is not active. Only the 'mysql' profile may use MySQL (or any other non-H2 "
			+ "database), so its connection, TLS and account rules always apply.";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		verify(environment);
	}

	static void verify(Environment environment) {
		boolean mysql = environment.matchesProfiles(MYSQL_PROFILE);
		boolean localFile = environment.matchesProfiles(LOCAL_FILE_PROFILE);
		// Profile conflicts first: they need no settings, so the message names the real problem.
		if (mysql && environment.matchesProfiles(
				LOCAL_H2_PROFILE + " | " + LOCAL_FILE_PROFILE + " | " + TEST_PROFILE + " | demo")) {
			throw new IllegalStateException(MYSQL_PROFILE_CONFLICT_MESSAGE);
		}
		if (localFile && environment.matchesProfiles(LOCAL_H2_PROFILE + " | " + TEST_PROFILE)) {
			throw new IllegalStateException(LOCAL_FILE_PROFILE_CONFLICT_MESSAGE);
		}
		// Resolve the storage root before the URL, so a missing variable is reported against the root.
		String storageRoot = localFile ? property(environment, LocalFileStoragePolicy.ROOT_SETTING) : null;
		// The URL itself is deliberately never included in a message: JDBC URLs can contain credentials.
		String url = property(environment, "spring.datasource.url");
		boolean anyDatabaseProfile = Arrays.stream(environment.getActiveProfiles())
			.anyMatch(DATABASE_PROFILES::contains);
		if (!anyDatabaseProfile) {
			throw new IllegalStateException(
					StringUtils.hasText(url) && selectsH2(url) ? FAILURE_MESSAGE : NO_DATABASE_PROFILE_MESSAGE);
		}
		// Every profile: nothing besides spring.datasource.url may choose the connection or run SQL.
		verifyConnectionSettings(environment, mysql);
		if (selectsH2(url) && !allowsH2(environment)) {
			throw new IllegalStateException(FAILURE_MESSAGE);
		}
		if (environment.matchesProfiles(LOCAL_H2_PROFILE) && !isInMemoryH2(url)) {
			throw new IllegalStateException(LOCAL_H2_MISMATCH_MESSAGE);
		}
		if (localFile) {
			verifyLocalFile(environment, url, storageRoot);
			return;
		}
		if (StringUtils.hasText(url) && selectsH2(url) && !isInMemoryH2(url)) {
			throw new IllegalStateException(FILE_H2_REQUIRES_LOCAL_FILE_MESSAGE);
		}
		if (!selectsH2(url) && !mysql) {
			throw new IllegalStateException(MYSQL_PROFILE_REQUIRED_MESSAGE);
		}
		if (mysql) {
			verifyMySql(environment, url);
		}
	}

	private static void verifyLocalFile(Environment environment, String url, String storageRoot) {
		LocalFileStoragePolicy.verify(url, property(environment, LocalFileStoragePolicy.DOCUMENTS_SETTING),
				LocalFileStoragePolicy.root(storageRoot));
		if ("always".equals(lower(property(environment, "spring.sql.init.mode")))) {
			throw new IllegalStateException("Refusing to start: spring.sql.init.mode=always is not allowed with the "
					+ "'local-file' profile; schema changes are made only by Flyway migrations.");
		}
		verifySchemaManagement(environment, LOCAL_FILE_PROFILE);
	}

	/**
	 * Applies to every profile. Only {@code spring.datasource.url} (checked by the profile rules) may
	 * choose the database: no JNDI, no other pool type, only allow-listed pool and Flyway settings, and
	 * a separate Flyway URL only with {@code mysql}. Setting names are enumerated from every property
	 * source (files, command line, relaxed environment variables) so none can slip past.
	 */
	private static void verifyConnectionSettings(Environment environment, boolean mysql) {
		if (StringUtils.hasText(property(environment, "spring.datasource.jndi-name"))) {
			throw new IllegalStateException("Refusing to start: spring.datasource.jndi-name is not allowed.");
		}
		String type = property(environment, "spring.datasource.type");
		if (StringUtils.hasText(type) && !"com.zaxxer.hikari.HikariDataSource".equals(type.strip())) {
			throw new IllegalStateException("Refusing to start: spring.datasource.type must be HikariCP (or unset).");
		}
		for (String setting : settingsUnder(environment, "spring.datasource.hikari", ALLOWED_POOL_SETTINGS)) {
			throw new IllegalStateException("Refusing to start: the setting '" + setting + "' is not allowed; only "
					+ "pool sizing and timeout settings may be changed (spring.datasource.hikari.jdbc-url, driver "
					+ "properties, data source classes, accounts and connection SQL are refused).");
		}
		for (String setting : settingsUnder(environment, "spring.flyway", ALLOWED_FLYWAY_SETTINGS)) {
			throw new IllegalStateException("Refusing to start: the setting '" + setting + "' is not allowed; it "
					+ "could run SQL or connect outside the checked database settings.");
		}
		if (!mysql && StringUtils.hasText(property(environment, "spring.flyway.url"))) {
			throw new IllegalStateException("Refusing to start: spring.flyway.url is only allowed with the 'mysql' "
					+ "profile; otherwise Flyway could connect to a database that no rule has checked.");
		}
	}

	/**
	 * Names of all settings below {@code prefix} that are not a single allow-listed element. Names
	 * only, never values. Returns nothing for a plain (non-configurable) {@link Environment}.
	 */
	static Set<String> settingsUnder(Environment environment, String prefix, Set<String> allowed) {
		Set<String> refused = new TreeSet<>();
		if (!(environment instanceof ConfigurableEnvironment configurable)) {
			return refused;
		}
		ConfigurationPropertyName root = ConfigurationPropertyName.of(prefix);
		int depth = root.getNumberOfElements();
		for (ConfigurationPropertySource source : ConfigurationPropertySources.get(configurable)) {
			if (source instanceof IterableConfigurationPropertySource iterable) {
				iterable.stream()
					.filter(root::isAncestorOf)
					.filter(name -> name.getNumberOfElements() != depth + 1
							|| !allowed.contains(name.getElement(depth, ConfigurationPropertyName.Form.UNIFORM)))
					.forEach(name -> refused.add(name.toString()));
			}
		}
		return refused;
	}

	private static void verifyMySql(Environment environment, String url) {
		MySqlUrlPolicy.Target target = MySqlUrlPolicy.parse(url, "spring.datasource.url");

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

		verifySchemaManagement(environment, MYSQL_PROFILE);
	}

	/** Shared by the persistent profiles: only Flyway may change the schema, and it may never drop it. */
	private static void verifySchemaManagement(Environment environment, String profile) {
		if (!"validate".equals(lower(property(environment, "spring.jpa.hibernate.ddl-auto")))) {
			throw new IllegalStateException("Refusing to start: spring.jpa.hibernate.ddl-auto must be 'validate' "
					+ "with the '" + profile + "' profile; schema changes are made only by Flyway migrations.");
		}
		for (String setting : SCHEMA_GENERATION_SETTINGS) {
			String value = lower(property(environment, setting));
			if (value != null && !"none".equals(value) && !"validate".equals(value)) {
				throw new IllegalStateException("Refusing to start: " + setting + " is not allowed with the '"
						+ profile + "' profile; schema changes are made only by Flyway migrations.");
			}
		}
		if (!"true".equals(lower(property(environment, "spring.flyway.enabled")))) {
			throw new IllegalStateException(
					"Refusing to start: Flyway must be enabled with the '" + profile + "' profile.");
		}
		String locations = property(environment, "spring.flyway.locations");
		if (locations == null || !MIGRATION_LOCATIONS.equals(locations.strip())) {
			throw new IllegalStateException("Refusing to start: spring.flyway.locations must be " + MIGRATION_LOCATIONS
					+ " with the '" + profile + "' profile, so only the packaged, versioned migrations can run.");
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
