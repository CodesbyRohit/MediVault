package com.medivault.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

import com.medivault.MedivaultApplication;
import com.medivault.TestLinks;

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
		environment.setProperty("spring.flyway.locations", "classpath:db/migration/{vendor}");
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
	@ValueSource(strings = { "local-h2", "local-file", "test", "demo" })
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

	@Test
	void fileBackedH2RequiresTheLocalFileProfile() {
		// Only 'local-file' may open a persistent H2 database; 'test' and 'local-h2' stay in-memory.
		String fileUrl = "jdbc:h2:file:" + tempDir.resolve("data").resolve("medivault");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(fileUrl, "test")))
			.hasMessage(DatabaseSafetyGuard.FILE_H2_REQUIRES_LOCAL_FILE_MESSAGE);
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment("jdbc:h2:./medivault", "test", "demo")))
			.hasMessage(DatabaseSafetyGuard.FILE_H2_REQUIRES_LOCAL_FILE_MESSAGE);
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment("jdbc:h2:tcp://localhost/x", "test")))
			.hasMessage(DatabaseSafetyGuard.FILE_H2_REQUIRES_LOCAL_FILE_MESSAGE);
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(fileUrl, "local-h2")))
			.hasMessage(DatabaseSafetyGuard.LOCAL_H2_MISMATCH_MESSAGE);
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(fileUrl, "prod")))
			.hasMessage(DatabaseSafetyGuard.FAILURE_MESSAGE);
	}

	// --- 'local-file' profile rules --------------------------------------------------

	@TempDir
	Path tempDir;

	private static String fileUrl(Path database) {
		return "jdbc:h2:file:" + database + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE";
	}

	/** A configuration that satisfies every 'local-file' rule; tests then break exactly one thing. */
	private MockEnvironment validLocalFile(String... profiles) {
		Path root = tempDir.resolve("MediVault");
		MockEnvironment environment = environment(fileUrl(root.resolve("data").resolve("medivault")),
				profiles.length == 0 ? new String[] { "local-file" } : profiles);
		environment.setProperty("medivault.storage.root", root.toString());
		environment.setProperty("medivault.documents.directory", root.resolve("records").toString());
		environment.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
		environment.setProperty("spring.flyway.enabled", "true");
		environment.setProperty("spring.flyway.locations", "classpath:db/migration/{vendor}");
		environment.setProperty("spring.flyway.clean-disabled", "true");
		environment.setProperty("spring.flyway.baseline-on-migrate", "false");
		return environment;
	}

	private MockEnvironment validLocalFile(Consumer<MockEnvironment> change) {
		MockEnvironment environment = validLocalFile();
		change.accept(environment);
		return environment;
	}

	@Test
	void validLocalFileConfigurationsAreAccepted() throws Exception {
		assertThatCode(() -> DatabaseSafetyGuard.verify(validLocalFile())).doesNotThrowAnyException();
		assertThatCode(() -> DatabaseSafetyGuard.verify(validLocalFile("local-file", "demo")))
			.doesNotThrowAnyException();
		// Settings may be omitted, and existing plain directories are fine.
		Files.createDirectories(tempDir.resolve("MediVault").resolve("data"));
		Files.createDirectories(tempDir.resolve("MediVault").resolve("records"));
		assertThatCode(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty("spring.datasource.url",
				"jdbc:h2:file:" + tempDir.resolve("MediVault").resolve("data").resolve("medivault")))))
			.doesNotThrowAnyException();
		// Mixed separators and redundant segments that still resolve to the data folder are accepted.
		assertThatCode(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty("spring.datasource.url",
				fileUrl(tempDir.resolve("MediVault").resolve("records").resolve("..").resolve("data").resolve("medivault"))))))
			.doesNotThrowAnyException();
	}

	@Test
	void guardNeverCreatesTheStorageFolders() {
		DatabaseSafetyGuard.verify(validLocalFile());
		assertThat(tempDir.resolve("MediVault")).doesNotExist();
	}

	@ParameterizedTest
	@ValueSource(strings = { "local-h2", "test" })
	void localFileCannotBeCombinedWithInMemoryProfiles(String other) {
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile("local-file", other)))
			.hasMessage(DatabaseSafetyGuard.LOCAL_FILE_PROFILE_CONFLICT_MESSAGE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "jdbc:h2:mem:medivault_local", "jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA",
			"jdbc:mysql://localhost:3306/medivault", "", "jdbc:h2:tcp://localhost/medivault", "jdbc:h2:./medivault",
			"jdbc:h2:file:./data/medivault", "jdbc:h2:file:~/medivault", "JDBC:H2:FILE:C:/medivault" })
	void localFileRefusesAnythingButItsFileDatabase(String url) {
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty("spring.datasource.url", url))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageStartingWith("Refusing to start");
	}

	@Test
	void localFileDatabaseMustBeDirectlyInsideTheDataFolder() {
		Path root = tempDir.resolve("MediVault");
		for (Path database : new Path[] { tempDir.resolve("elsewhere").resolve("medivault"), root.resolve("medivault"),
				root.resolve("records").resolve("medivault"), root.resolve("data").resolve("nested").resolve("medivault"),
				root.resolve("data").resolve("..").resolve("..").resolve("escape").resolve("medivault"),
				root.resolve("data").resolve("medi vault") }) {
			assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty("spring.datasource.url",
					fileUrl(database)))))
				.as(database.toString())
				.hasMessageContaining("spring.datasource.url was rejected by the local-file storage policy");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { ";INIT=RUNSCRIPT FROM 'x.sql'", ";AUTO_SERVER=TRUE", ";PASSWORD=not-a-real-secret",
			";USER=sa", ";MODE=MySQL;MODE=MySQL", ";", ";MODE=", ";IFEXISTS=FALSE", ";CIPHER=AES",
			";FILE_LOCK=NO", ";MODE=MySQL\\;INIT=x" })
	void localFileOnlyAcceptsAllowListedH2Settings(String settings) {
		Path database = tempDir.resolve("MediVault").resolve("data").resolve("medivault");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty("spring.datasource.url",
				"jdbc:h2:file:" + database + settings))))
			.hasMessageContaining("local-file storage policy")
			.message()
			.doesNotContain("not-a-real-secret");
	}

	@Test
	void storageRootMustBeAnAbsoluteSafePath() {
		for (String root : new String[] { "", "   ", "relative/MediVault", "MediVault", tempDir + ";INIT=x" }) {
			assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty("medivault.storage.root",
					root))))
				.as("root '%s'", root)
				.hasMessageContaining("medivault.storage.root was rejected by the local-file storage policy");
		}
	}

	@Test
	void documentsFolderMustBeTheRootsRecordsFolder() {
		Path root = tempDir.resolve("MediVault");
		for (String directory : new String[] { "", tempDir.resolve("records").toString(), root.toString(),
				root.resolve("data").toString(), root.resolve("records").resolve("sub").toString(), "records",
				"src/main/resources/static" }) {
			assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty(
					"medivault.documents.directory", directory))))
				.as("directory '%s'", directory)
				.hasMessageContaining("medivault.documents.directory was rejected");
		}
	}

	@Test
	void storageFoldersMustBePlainDirectories() throws Exception {
		Path root = Files.createDirectories(tempDir.resolve("MediVault"));
		Files.writeString(root.resolve("data"), "not a folder");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile()))
			.hasMessageContaining("not a plain directory");
		Files.delete(root.resolve("data"));
		Files.writeString(root.resolve("records"), "not a folder");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile()))
			.hasMessageContaining("not a plain directory");
	}

	@Test
	void linkedDataFolderIsRefused() throws Exception {
		Path root = Files.createDirectories(tempDir.resolve("MediVault"));
		Path outside = Files.createDirectories(tempDir.resolve("outside"));
		assumeTrue(TestLinks.createDirectoryLink(root.resolve("data"), outside),
				"no symbolic link or junction could be created");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile()))
			.hasMessageContaining("not a plain directory");
		Path records = Files.createDirectories(tempDir.resolve("records-target"));
		Files.delete(root.resolve("data"));
		assumeTrue(TestLinks.createDirectoryLink(root.resolve("records"), records));
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile()))
			.hasMessageContaining("medivault.documents.directory")
			.hasMessageContaining("not a plain directory");
	}

	@ParameterizedTest
	@ValueSource(strings = { "update", "create", "create-drop", "none" })
	void localFileHibernateMayOnlyValidate(String ddlAuto) {
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty(
				"spring.jpa.hibernate.ddl-auto", ddlAuto))))
			.hasMessageContaining("ddl-auto must be 'validate' with the 'local-file' profile");
	}

	@Test
	void localFileSchemaIsOnlyChangedByFlyway() {
		for (String setting : DatabaseSafetyGuard.SCHEMA_GENERATION_SETTINGS) {
			assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty(setting,
					"drop-and-create"))))
				.hasMessageContaining(setting);
			assertThatCode(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty(setting, "none"))))
				.doesNotThrowAnyException();
		}
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty("spring.flyway.enabled",
				"false"))))
			.hasMessageContaining("Flyway must be enabled");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty(
				"spring.flyway.clean-disabled", "false"))))
			.hasMessageContaining("clean must stay disabled");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty(
				"spring.flyway.baseline-on-migrate", "true"))))
			.hasMessageContaining("baseline-on-migrate");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty("spring.sql.init.mode",
				"always"))))
			.hasMessageContaining("spring.sql.init.mode");
	}

	@Test
	void localFileRefusesAlternativeConnectionSettings() {
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty(
				"spring.datasource.jndi-name", "java:comp/env/jdbc/x"))))
			.hasMessageContaining("jndi-name");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty(
				"spring.datasource.hikari.jdbc-url", "jdbc:h2:mem:x"))))
			.hasMessageContaining("hikari.jdbc-url");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty("spring.flyway.url",
				"jdbc:h2:mem:x"))))
			.hasMessageContaining("spring.flyway.url");
	}

	@Test
	void localFileMessagesNeverContainPathsOrUrls() {
		String secretPath = tempDir.resolve("secret-user-name").toString();
		MockEnvironment outside = validLocalFile(e -> e.setProperty("spring.datasource.url",
				fileUrl(Path.of(secretPath, "data", "medivault"))));
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(outside)).message()
			.doesNotContain("secret-user-name")
			.doesNotContain(tempDir.toString())
			.doesNotContain("jdbc:h2:file:");
		MockEnvironment badRoot = validLocalFile(e -> e.setProperty("medivault.storage.root", "secret-user-name"));
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(badRoot)).message().doesNotContain("secret-user-name");
	}

	@Test
	void unsetStorageRootVariableIsRefusedWithoutValues() {
		MockEnvironment environment = validLocalFile(e -> e.setProperty("medivault.storage.root",
				"${MEDIVAULT_TEST_UNSET_VARIABLE_FOR_GUARD}/MediVault"));
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment))
			.hasMessage("Refusing to start: the setting 'medivault.storage.root' refers to an environment variable "
					+ "that is not set.");
	}

	// --- Pool and Flyway settings, every profile (M4 audit regression) ---------------------

	/** One valid environment per database profile; each test then adds exactly one setting. */
	private List<MockEnvironment> validForEveryProfile() {
		return List.of(environment(H2_URL, "local-h2"), environment(H2_URL, "local-h2", "demo"),
				environment(H2_URL, "test"), validLocalFile(), validLocalFile("local-file", "demo"),
				validMySql(DEV_URL, "dev"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "spring.datasource.hikari.connection-init-sql", "spring.datasource.hikari.connection-test-query",
			"spring.datasource.hikari.data-source-class-name", "spring.datasource.hikari.data-source-properties.INIT",
			"spring.datasource.hikari.data-source-properties.sslMode", "spring.datasource.hikari.dataSourceProperties.url",
			"spring.datasource.hikari.jdbc-url", "spring.datasource.hikari.username", "spring.datasource.hikari.password",
			"spring.datasource.hikari.driver-class-name", "spring.datasource.hikari.data-source-j-n-d-i",
			"spring.datasource.hikari.schema", "spring.datasource.hikari.catalog",
			"spring.flyway.init-sqls", "spring.flyway.jdbc-properties.sslMode", "spring.flyway.driver-class-name",
			"spring.flyway.schemas", "spring.flyway.default-schema", "spring.flyway.target", "spring.flyway.out-of-order",
			"spring.flyway.placeholders.x" })
	void connectionAndSqlSettingsOutsideTheAllowListAreRefusedInEveryProfile(String setting) {
		for (MockEnvironment environment : validForEveryProfile()) {
			environment.setProperty(setting, "not-a-real-secret");
			assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment))
				.as("%s with %s", setting, List.of(environment.getActiveProfiles()))
				.hasMessageStartingWith("Refusing to start: the setting 'spring.")
				.hasMessageContaining("is not allowed")
				.message()
				.doesNotContain("not-a-real-secret");
		}
	}

	@Test
	void relaxedEnvironmentVariableNamesAreRefusedToo() {
		for (String variable : List.of("SPRING_DATASOURCE_HIKARI_CONNECTIONINITSQL",
				"SPRING_DATASOURCE_HIKARI_DATASOURCEPROPERTIES_INIT", "SPRING_FLYWAY_INITSQLS")) {
			for (MockEnvironment environment : validForEveryProfile()) {
				environment.getPropertySources()
					.addFirst(new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
							Map.of(variable, "DROP ALL OBJECTS")));
				assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment)).as(variable)
					.hasMessageContaining("is not allowed")
					.message()
					.doesNotContain("DROP ALL OBJECTS");
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "spring.datasource.hikari.maximum-pool-size", "spring.datasource.hikari.minimumIdle",
			"spring.datasource.hikari.connection-timeout", "spring.datasource.hikari.pool-name",
			"spring.datasource.hikari.max-lifetime", "spring.flyway.validate-on-migrate", "spring.flyway.fail-on-missing-locations" })
	void poolTuningAndPlainFlywaySwitchesAreStillAccepted(String setting) {
		for (MockEnvironment environment : validForEveryProfile()) {
			environment.setProperty(setting, setting.contains("flyway") ? "true" : "5");
			assertThatCode(() -> DatabaseSafetyGuard.verify(environment))
				.as("%s with %s", setting, List.of(environment.getActiveProfiles()))
				.doesNotThrowAnyException();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "local-h2", "test" })
	void aSeparateFlywayUrlIsOnlyAllowedWithMysql(String profile) {
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment(H2_URL, profile).withProperty("spring.flyway.url",
				"jdbc:mysql://localhost:3306/medivault")))
			.hasMessageContaining("spring.flyway.url is only allowed with the 'mysql' profile");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty("spring.flyway.url",
				"jdbc:h2:mem:x"))))
			.hasMessageContaining("spring.flyway.url is only allowed with the 'mysql' profile");
	}

	@Test
	void jndiAndOtherPoolTypesAreRefusedInEveryProfile() {
		for (MockEnvironment environment : validForEveryProfile()) {
			environment.setProperty("spring.datasource.jndi-name", "java:comp/env/jdbc/x");
			assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment)).hasMessageContaining("jndi-name");
		}
		for (MockEnvironment environment : validForEveryProfile()) {
			environment.setProperty("spring.datasource.type", "org.springframework.jdbc.datasource.SimpleDriverDataSource");
			assertThatThrownBy(() -> DatabaseSafetyGuard.verify(environment)).hasMessageContaining("spring.datasource.type");
		}
		for (MockEnvironment environment : validForEveryProfile()) {
			environment.setProperty("spring.datasource.type", "com.zaxxer.hikari.HikariDataSource");
			assertThatCode(() -> DatabaseSafetyGuard.verify(environment)).doesNotThrowAnyException();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "filesystem:C:/evil", "classpath:db/migration/mysql", "classpath:db/migration/{vendor},filesystem:x",
			"classpath:db/migration", "" })
	void persistentProfilesOnlyRunThePackagedMigrations(String locations) {
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validLocalFile(e -> e.setProperty("spring.flyway.locations",
				locations))))
			.hasMessageContaining("spring.flyway.locations must be classpath:db/migration/{vendor}");
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(validDev(e -> e.setProperty("spring.flyway.locations",
				locations))))
			.hasMessageContaining("spring.flyway.locations must be classpath:db/migration/{vendor}");
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
