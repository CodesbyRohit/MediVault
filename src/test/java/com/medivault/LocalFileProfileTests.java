package com.medivault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.medivault.demo.DemoRecordSeeder;
import com.medivault.document.FictionalPdf;
import com.medivault.document.RecordDocumentService;
import com.medivault.document.RecordDocumentService.DocumentSummary;
import com.medivault.record.MedicalRecord;
import com.medivault.record.MedicalRecordRepository;
import com.medivault.record.RecordType;
import com.medivault.user.RegistrationForm;
import com.medivault.user.RegistrationService;
import com.medivault.user.Role;
import com.medivault.user.User;
import com.medivault.user.UserRepository;

/**
 * The persistent "local-file" profile, started for real (Tomcat on a random loopback port) with
 * the effective profile configuration. Every run uses a JUnit temporary storage root passed as
 * {@code --medivault.storage.root}, which outranks a developer's {@code MEDIVAULT_STORAGE_ROOT}.
 * The developer's real {@code %LOCALAPPDATA%\MediVault} is checked to be untouched afterwards.
 */
class LocalFileProfileTests {

	private static final String LOCAL_APP_DATA = System.getenv("LOCALAPPDATA");

	private static final Path DEVELOPER_ROOT = LOCAL_APP_DATA == null ? null : Path.of(LOCAL_APP_DATA, "MediVault");

	private static String developerRootFingerprint;

	@TempDir
	Path tempDir;

	@BeforeAll
	static void fingerprintDeveloperData() throws Exception {
		developerRootFingerprint = fingerprint(DEVELOPER_ROOT);
	}

	@AfterAll
	static void developerDataIsUntouched() throws Exception {
		assertThat(fingerprint(DEVELOPER_ROOT)).as("the developer's local MediVault data must not change")
			.isEqualTo(developerRootFingerprint);
	}

	/** Names, sizes and modification times only (never contents) of the developer's storage root. */
	private static String fingerprint(Path root) throws Exception {
		if (root == null || !Files.exists(root)) {
			return "absent";
		}
		List<String> entries = new ArrayList<>();
		try (Stream<Path> files = Files.walk(root)) {
			for (Path file : files.sorted().toList()) {
				BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
				entries.add(root.relativize(file) + ":" + attributes.size() + ":" + attributes.lastModifiedTime());
			}
		}
		return String.join("|", entries);
	}

	private static List<String> args(String profiles, String... extra) {
		List<String> args = new ArrayList<>(List.of("--spring.profiles.active=" + profiles, "--server.port=0",
				"--spring.main.banner-mode=off", "--logging.level.root=WARN", "--medivault.admin.email=",
				"--medivault.admin.password="));
		args.addAll(List.of(extra));
		return args;
	}

	private static ConfigurableApplicationContext start(Path root, String profiles) {
		return new SpringApplicationBuilder(MedivaultApplication.class)
			.run(args(profiles, "--medivault.storage.root=" + root).toArray(String[]::new));
	}

	private static User createPatient(ConfigurableApplicationContext context, String email) {
		return context.getBean(UserRepository.class)
			.save(new User("Fictional Patient", email, context.getBean(PasswordEncoder.class).encode("Fictional123"),
					Role.PATIENT));
	}

	// --- Effective configuration ------------------------------------------------------------

	@Test
	void firstStartCreatesTheStorageFoldersAndMigratesTheFileDatabase() throws Exception {
		Path root = tempDir.resolve("MediVault");
		assertThat(root).doesNotExist();

		try (ConfigurableApplicationContext context = start(root, "local-file")) {
			Environment environment = context.getEnvironment();
			assertThat(environment.getActiveProfiles()).containsExactly("local-file");
			assertThat(root.resolve("data").resolve("medivault.mv.db")).isRegularFile();
			assertThat(root.resolve("records")).isDirectory();

			try (Connection connection = context.getBean(DataSource.class).getConnection()) {
				String url = connection.getMetaData().getURL();
				assertThat(url).startsWith("jdbc:h2:file:");
				Path database = Path.of(url.substring("jdbc:h2:file:".length()).split(";")[0] + ".mv.db");
				assertThat(Files.isSameFile(database, root.resolve("data").resolve("medivault.mv.db"))).isTrue();
			}
			assertThat(Path.of(environment.getProperty("medivault.documents.directory")).normalize())
				.isEqualTo(root.resolve("records"));

			Flyway flyway = context.getBean(Flyway.class);
			assertThat(flyway.info().applied()).extracting(info -> info.getVersion().getVersion())
				.containsExactly("1", "2");
			assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
			assertThat(environment.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
			assertThat(environment.getProperty("spring.h2.console.enabled")).isEqualTo("false");
			assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
		}
	}

	@Test
	void constraintsFromTheMigrationsAreEnforced() {
		Path root = tempDir.resolve("MediVault");
		try (ConfigurableApplicationContext context = start(root, "local-file")) {
			createPatient(context, "asha@example.test");
			UserRepository users = context.getBean(UserRepository.class);
			assertThatThrownBy(() -> users.saveAndFlush(new User("Duplicate", "ASHA@example.test", "x", Role.PATIENT)))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
			var jdbc = new org.springframework.jdbc.core.JdbcTemplate(context.getBean(DataSource.class));
			assertThatThrownBy(() -> jdbc.update("INSERT INTO medical_records (created_at, record_date, record_type, "
					+ "title, updated_at, owner_id) VALUES (CURRENT_TIMESTAMP, CURRENT_DATE, 'OTHER', 'Orphan', "
					+ "CURRENT_TIMESTAMP, 987654)"))
				.as("foreign key to app_users")
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
			assertThatThrownBy(() -> jdbc.update("INSERT INTO record_documents (content_type, original_filename, sha256, "
					+ "size_bytes, storage_key, uploaded_at, record_id) VALUES ('application/pdf', 'x.pdf', 'x', 1, "
					+ "'k', CURRENT_TIMESTAMP, 987654)"))
				.as("foreign key to medical_records")
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		}
	}

	@Test
	void theDefaultRootIsLocalAppDataAndMediVaultStorageRootOverridesIt() {
		assumeTrue(LOCAL_APP_DATA != null, "LOCALAPPDATA is only set on Windows");
		assumeTrue(System.getenv("MEDIVAULT_STORAGE_ROOT") == null, "MEDIVAULT_STORAGE_ROOT is set on this machine");

		Environment defaults = preparedEnvironment(args("local-file"));
		assertThat(Path.of(defaults.getProperty("medivault.storage.root")).normalize()).isEqualTo(DEVELOPER_ROOT);
		assertThat(defaults.getProperty("spring.datasource.url")).startsWith("jdbc:h2:file:")
			.endsWith("/data/medivault;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE");
		assertThat(Path.of(defaults.getProperty("medivault.documents.directory")).normalize())
			.isEqualTo(DEVELOPER_ROOT.resolve("records"));

		Path custom = tempDir.resolve("Custom Root");
		Environment overridden = preparedEnvironment(args("local-file", "--MEDIVAULT_STORAGE_ROOT=" + custom));
		assertThat(Path.of(overridden.getProperty("medivault.storage.root")).normalize()).isEqualTo(custom);
		assertThat(Path.of(overridden.getProperty("medivault.documents.directory")).normalize())
			.isEqualTo(custom.resolve("records"));
		assertThat(custom).as("preparing the environment creates nothing").doesNotExist();
	}

	/** Runs startup only up to the prepared (and guard-checked) environment, then aborts: no context, no I/O. */
	private static Environment preparedEnvironment(List<String> args) {
		AtomicReference<Environment> prepared = new AtomicReference<>();
		ApplicationListener<ApplicationEnvironmentPreparedEvent> capture = event -> {
			prepared.set(event.getEnvironment());
			throw new StopAfterEnvironment();
		};
		assertThatThrownBy(() -> new SpringApplicationBuilder(MedivaultApplication.class).listeners(capture)
			.run(args.toArray(String[]::new))).isInstanceOf(StopAfterEnvironment.class);
		return prepared.get();
	}

	private static final class StopAfterEnvironment extends RuntimeException {

	}

	@Test
	void conflictingProfilesAreNamedInTheRefusal() {
		Path root = tempDir.resolve("MediVault");
		List<String> withMysql = args("local-file,mysql", "--medivault.storage.root=" + root);
		assertThatThrownBy(() -> new SpringApplicationBuilder(MedivaultApplication.class)
			.run(withMysql.toArray(String[]::new))).hasMessageContaining("'mysql' profile cannot be combined");
		List<String> withLocalH2 = args("local-file,local-h2,demo", "--medivault.storage.root=" + root);
		assertThatThrownBy(() -> new SpringApplicationBuilder(MedivaultApplication.class)
			.run(withLocalH2.toArray(String[]::new))).hasMessageContaining("'local-file' profile cannot be combined");
		assertThat(root).doesNotExist();
	}

	@Test
	void overridesThatLeaveTheStorageRootAreRefusedBeforeAnythingIsCreated() {
		Path root = tempDir.resolve("MediVault");
		Path elsewhere = tempDir.resolve("elsewhere");
		for (String override : List.of("--spring.datasource.url=jdbc:h2:file:" + elsewhere + "/medivault",
				"--spring.datasource.url=jdbc:h2:mem:medivault_local",
				"--spring.datasource.url=jdbc:h2:file:" + root + "/data/medivault;INIT=RUNSCRIPT FROM 'x'",
				"--medivault.documents.directory=" + elsewhere, "--spring.jpa.hibernate.ddl-auto=create-drop",
				"--spring.flyway.clean-disabled=false", "--spring.profiles.active=local-file,local-h2",
				"--spring.profiles.active=local-file,mysql")) {
			List<String> args = args("local-file", "--medivault.storage.root=" + root);
			if (override.startsWith("--spring.profiles.active=")) {
				args.removeIf(arg -> arg.startsWith("--spring.profiles.active="));
			}
			args.add(override);
			assertThatThrownBy(() -> new SpringApplicationBuilder(MedivaultApplication.class)
				.run(args.toArray(String[]::new))).as(override).hasMessageStartingWith("Refusing to start");
			assertThat(root).as(override).doesNotExist();
			assertThat(elsewhere).as(override).doesNotExist();
		}
	}

	/**
	 * Regression (M4 audit): HikariCP and Flyway settings could run SQL or open another database
	 * outside the checked URL (for example DROP ALL OBJECTS on every new connection, or an H2 INIT
	 * script passed as a driver property). Before the fix, each of these started successfully.
	 */
	@Test
	void poolAndFlywaySettingsThatBypassTheStoragePolicyAreRefused() {
		Path root = tempDir.resolve("MediVault");
		Path elsewhere = tempDir.resolve("elsewhere");
		List<List<String>> cases = List.of(
				List.of("--spring.datasource.hikari.connection-init-sql=CREATE TABLE pwned_init (x INT)"),
				List.of("--spring.datasource.hikari.data-source-properties.INIT=CREATE TABLE pwned_property (x INT)"),
				List.of("--spring.datasource.hikari.data-source-class-name=org.h2.jdbcx.JdbcDataSource",
						"--spring.datasource.hikari.data-source-properties.URL=jdbc:h2:file:" + elsewhere + "/medivault"),
				List.of("--spring.datasource.hikari.connection-test-query=CREATE TABLE pwned_test (x INT)"),
				List.of("--spring.datasource.hikari.username=other"),
				List.of("--spring.flyway.init-sqls=CREATE TABLE pwned_flyway (x INT)"),
				List.of("--spring.flyway.locations=filesystem:" + elsewhere));
		org.assertj.core.api.SoftAssertions softly = new org.assertj.core.api.SoftAssertions();
		for (List<String> overrides : cases) {
			List<String> args = args("local-file", "--medivault.storage.root=" + tempDir.resolve("case-" + cases.indexOf(overrides)));
			args.addAll(overrides);
			softly.assertThatThrownBy(() -> {
				try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MedivaultApplication.class)
					.run(args.toArray(String[]::new))) {
					// reaching this line means the override was accepted
				}
			}).as(overrides.toString()).hasMessageStartingWith("Refusing to start");
			softly.assertThat(tempDir.resolve("case-" + cases.indexOf(overrides))).as(overrides.toString()).doesNotExist();
		}
		softly.assertThat(root).doesNotExist();
		softly.assertThat(elsewhere).doesNotExist();
		softly.assertAll();
	}

	// --- Persistence across restarts ---------------------------------------------------------

	@Test
	void accountsRecordsAndPdfsSurviveARestart() throws Exception {
		Path root = tempDir.resolve("MediVault");
		byte[] pdf = FictionalPdf.bytes("Persistence check");
		Long userId;
		Long recordId;
		String passwordHash;
		try (ConfigurableApplicationContext first = start(root, "local-file")) {
			User patient = createPatient(first, "asha@example.test");
			userId = patient.getId();
			passwordHash = patient.getPasswordHash();
			MedicalRecord record = first.getBean(MedicalRecordRepository.class)
				.save(new MedicalRecord(patient, "Fictional thyroid panel", RecordType.LAB_REPORT,
						LocalDate.now().minusDays(3), "Fictional Clinic", "Fictional notes"));
			recordId = record.getId();
			first.getBean(RecordDocumentService.class)
				.upload(recordId, userId, new MockMultipartFile("file", "thyroid.pdf", "application/pdf", pdf));
		}
		Path database = root.resolve("data").resolve("medivault.mv.db");
		FileTime created = Files.readAttributes(database, BasicFileAttributes.class).creationTime();
		try (Stream<Path> files = Files.list(root.resolve("records"))) {
			assertThat(files.toList()).as("one stored PDF on disk").hasSize(1);
		}

		try (ConfigurableApplicationContext second = start(root, "local-file")) {
			User patient = second.getBean(UserRepository.class).findByEmail("asha@example.test").orElseThrow();
			assertThat(patient.getId()).isEqualTo(userId);
			assertThat(patient.getPasswordHash()).isEqualTo(passwordHash);
			assertThat(second.getBean(PasswordEncoder.class).matches("Fictional123", patient.getPasswordHash())).isTrue();
			assertThat(second.getBean(MedicalRecordRepository.class).findByIdAndOwnerId(recordId, userId))
				.get()
				.extracting(MedicalRecord::getTitle)
				.isEqualTo("Fictional thyroid panel");

			RecordDocumentService documents = second.getBean(RecordDocumentService.class);
			List<DocumentSummary> listed = documents.listForOwner(recordId, userId);
			assertThat(listed).extracting(DocumentSummary::filename).containsExactly("thyroid.pdf");
			try (InputStream in = documents.openForOwner(recordId, listed.get(0).id(), userId).content()) {
				assertThat(in.readAllBytes()).isEqualTo(pdf);
			}

			Flyway flyway = second.getBean(Flyway.class);
			assertThat(flyway.info().applied()).hasSize(2).extracting(MigrationInfo::getInstalledRank).containsExactly(1, 2);
			assertThat(flyway.info().pending()).isEmpty();
		}
		assertThat(Files.readAttributes(database, BasicFileAttributes.class).creationTime())
			.as("the database file is reused, never recreated").isEqualTo(created);
	}

	@Test
	void demoSamplesAreCreatedOnceAndRestartsNeverDuplicateThem() {
		Path root = tempDir.resolve("MediVault");
		Long firstId;
		String firstHash;
		try (ConfigurableApplicationContext first = start(root, "local-file,demo")) {
			User patient = first.getBean(RegistrationService.class).registerPatient(form("demo@example.test"));
			firstId = patient.getId();
			firstHash = first.getBean(UserRepository.class).findById(firstId).orElseThrow().getPasswordHash();
			assertThat(first.getBean(MedicalRecordRepository.class).countByOwnerId(firstId)).isEqualTo(3);
		}
		try (ConfigurableApplicationContext second = start(root, "local-file,demo")) {
			MedicalRecordRepository records = second.getBean(MedicalRecordRepository.class);
			assertThat(records.countByOwnerId(firstId)).isEqualTo(3);
			assertThat(second.getBean(DemoRecordSeeder.class).seedFor(firstId)).isZero();
			User unchanged = second.getBean(UserRepository.class).findById(firstId).orElseThrow();
			assertThat(unchanged.getPasswordHash()).isEqualTo(firstHash);
			assertThat(unchanged.getRole()).isEqualTo(Role.PATIENT);
			assertThat(records.count()).isEqualTo(3);
		}
		try (ConfigurableApplicationContext plain = start(root, "local-file")) {
			assertThat(plain.getBeanProvider(DemoRecordSeeder.class).getIfAvailable()).as("no demo without 'demo'")
				.isNull();
			User later = plain.getBean(RegistrationService.class).registerPatient(form("later@example.test"));
			assertThat(plain.getBean(MedicalRecordRepository.class).countByOwnerId(later.getId())).isZero();
		}
	}

	private static RegistrationForm form(String email) {
		RegistrationForm form = new RegistrationForm();
		form.setFullName("Demo Fictional");
		form.setEmail(email);
		form.setPassword("Fictional123");
		form.setConfirmPassword("Fictional123");
		return form;
	}

}
