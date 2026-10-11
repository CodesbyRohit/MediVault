package com.medivault.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationContextInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

import com.medivault.MedivaultApplication;

/**
 * Proves that every rejected database configuration fails before an application context (and
 * therefore any DataSource) exists, and that no TCP connection is attempted.
 * <ul>
 * <li>Layer 1: a real {@code SpringApplication} start must throw the guard's refusal and must never
 * publish {@link ApplicationContextInitializedEvent}.</li>
 * <li>Layer 2: rejected URLs that would otherwise be reachable point at a loopback "canary" socket
 * owned by this test, which must record zero accepted connections.</li>
 * <li>Positive control: a configuration the guard accepts, pointed at the canary, does produce a
 * connection attempt, proving the canary detects connections.</li>
 * </ul>
 * No test ever opens or targets port 3306; 3306 cases rely on layer 1 only.
 */
class DatabaseGuardNoConnectionTests {

	private Canary canary;

	@BeforeEach
	void openCanary() throws IOException {
		canary = new Canary();
		assertThat(canary.port()).isNotEqualTo(3306);
	}

	@AfterEach
	void closeCanary() throws IOException {
		canary.close();
	}

	/** A valid-looking 'mysql' configuration pointed at the canary; each case changes one setting. */
	private List<String> baseArgs(String url) {
		List<String> args = new ArrayList<>(List.of("--spring.main.banner-mode=off", "--logging.level.root=WARN",
				"--spring.profiles.active=mysql", "--spring.datasource.username=medivault_it_app",
				"--spring.datasource.password=", "--spring.flyway.user=medivault_it_migrator",
				"--spring.flyway.password=", "--medivault.admin.email=", "--medivault.admin.password="));
		args.add("--spring.datasource.url=" + url);
		return args;
	}

	private String canaryUrl(String query) {
		return "jdbc:mysql://127.0.0.1:" + canary.port() + "/medivault_it" + query;
	}

	private record Attempt(Throwable failure, boolean contextCreated) {
	}

	private static Attempt start(List<String> args) {
		AtomicBoolean contextCreated = new AtomicBoolean();
		ApplicationListener<ApplicationContextInitializedEvent> listener = event -> contextCreated.set(true);
		SpringApplicationBuilder app = new SpringApplicationBuilder(MedivaultApplication.class)
			.web(WebApplicationType.NONE)
			.listeners(listener);
		try (ConfigurableApplicationContext context = app.run(args.toArray(String[]::new))) {
			return new Attempt(null, contextCreated.get());
		}
		catch (Throwable ex) {
			return new Attempt(ex, contextCreated.get());
		}
	}

	static Stream<Arguments> rejectedAgainstCanary() {
		return Stream.of(Arguments.of("sslMode missing", "", null),
				Arguments.of("sslMode=REQUIRED", "?sslMode=REQUIRED", null),
				Arguments.of("sslMode=DISABLED", "?sslMode=DISABLED", null),
				Arguments.of("allowPublicKeyRetrieval", "?sslMode=VERIFY_CA&allowPublicKeyRetrieval=true", null),
				Arguments.of("password in URL", "?sslMode=VERIFY_CA&password=x", null),
				Arguments.of("wrong application account", "?sslMode=VERIFY_CA", "--spring.datasource.username=root"),
				Arguments.of("dev account on it database", "?sslMode=VERIFY_CA",
						"--spring.flyway.user=medivault_dev_migrator"),
				Arguments.of("ddl-auto=update", "?sslMode=VERIFY_CA", "--spring.jpa.hibernate.ddl-auto=update"),
				Arguments.of("Flyway clean enabled", "?sslMode=VERIFY_CA", "--spring.flyway.clean-disabled=false"),
				Arguments.of("JNDI datasource", "?sslMode=VERIFY_CA", "--spring.datasource.jndi-name=java:comp/env/x"),
				Arguments.of("Hikari URL override", "?sslMode=VERIFY_CA",
						"--spring.datasource.hikari.jdbc-url=jdbc:mysql://127.0.0.1:1/medivault_it?sslMode=VERIFY_CA"),
				Arguments.of("mysql combined with demo", "?sslMode=VERIFY_CA", "--spring.profiles.active=mysql,demo"),
				Arguments.of("no database profile", "?sslMode=VERIFY_CA", "--spring.profiles.active=none"));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("rejectedAgainstCanary")
	void rejectedConfigurationsNeverReachTheCanary(String name, String query, String override) throws Exception {
		List<String> args = baseArgs(canaryUrl(query));
		if (override != null) {
			// Replace (not append): Spring merges repeated command-line options into one list.
			String key = override.substring(0, override.indexOf('=') + 1);
			args.removeIf(arg -> arg.startsWith(key));
			args.add(override);
		}

		Attempt attempt = start(args);

		assertThat(attempt.failure()).as(name).isNotNull();
		assertThat(rootMessages(attempt.failure())).as(name).anyMatch(m -> m.startsWith("Refusing to start"));
		assertThat(attempt.contextCreated()).as(name + ": no application context may be created").isFalse();
		assertThat(canary.acceptedAfterSettling()).as(name + ": no connection may be attempted").isZero();
	}

	static Stream<Arguments> rejectedWithoutReachableTarget() {
		return Stream.of(
				Arguments.of("port 3306", "jdbc:mysql://127.0.0.1:3306/medivault_it?sslMode=VERIFY_CA"),
				Arguments.of("implicit default port", "jdbc:mysql://127.0.0.1/medivault_it?sslMode=VERIFY_CA"),
				Arguments.of("localhost", "jdbc:mysql://localhost:3307/medivault_it?sslMode=VERIFY_CA"),
				Arguments.of("malformed", "jdbc:mysql://127.0.0.1:3307"),
				Arguments.of("other driver", "jdbc:mariadb://127.0.0.1:3307/medivault_it?sslMode=VERIFY_CA"));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("rejectedWithoutReachableTarget")
	void rejectedTargetsFailBeforeAnyContextExists(String name, String url) {
		Attempt attempt = start(baseArgs(url));

		assertThat(attempt.failure()).as(name).isNotNull();
		assertThat(rootMessages(attempt.failure())).as(name).anyMatch(m -> m.startsWith("Refusing to start"));
		assertThat(attempt.contextCreated()).as(name + ": no application context may be created").isFalse();
	}

	/**
	 * The 'test' profile with a MySQL URL. application-test.properties (on the test classpath) sets
	 * the H2 driver, so Connector/J is forced here, as it would be in a packaged application where
	 * that file is absent; without the guard rule this configuration really connects to the canary.
	 */
	private List<String> testProfileArgs(String url) {
		List<String> args = new ArrayList<>(List.of("--spring.main.banner-mode=off", "--logging.level.root=WARN",
				"--spring.profiles.active=test", "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
				"--spring.datasource.username=medivault_it_app", "--spring.datasource.password=",
				"--medivault.admin.email=", "--medivault.admin.password="));
		args.add("--spring.datasource.url=" + url);
		return args;
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@ValueSource(strings = { "?sslMode=VERIFY_CA&connectTimeout=1000&socketTimeout=1000",
			"?connectTimeout=1000&socketTimeout=1000" })
	void testProfileWithAMysqlUrlNeverReachesTheCanary(String query) {
		Attempt attempt = start(testProfileArgs(canaryUrl(query)));

		assertThat(attempt.failure()).isNotNull();
		assertThat(rootMessages(attempt.failure()))
			.contains(DatabaseSafetyGuard.MYSQL_PROFILE_REQUIRED_MESSAGE);
		assertThat(attempt.contextCreated()).as("no application context may be created").isFalse();
		assertThat(canary.acceptedAfterSettling()).as("no connection may be attempted").isZero();
	}

	@Test
	void testProfileWithAMysqlUrlOnPort3306FailsBeforeAnyContextExists() {
		// Layer 1 only (3306 is never targeted by a canary). The H2 driver from application-test.properties
		// is deliberately kept, so even if the guard regressed, the driver would refuse this URL
		// instead of contacting port 3306; the assertions below would still fail.
		List<String> args = testProfileArgs("jdbc:mysql://127.0.0.1:3306/medivault");
		args.removeIf(arg -> arg.startsWith("--spring.datasource.driver-class-name="));

		Attempt attempt = start(args);

		assertThat(attempt.failure()).isNotNull();
		assertThat(rootMessages(attempt.failure()))
			.contains(DatabaseSafetyGuard.MYSQL_PROFILE_REQUIRED_MESSAGE);
		assertThat(attempt.contextCreated()).as("no application context may be created").isFalse();
	}

	@Test
	void positiveControlCanaryDetectsAConnectionAttempt() {
		// Accepted by the guard, so the app really tries to connect; the canary never speaks MySQL,
		// so the attempt fails quickly on the socket timeout.
		Attempt attempt = start(baseArgs(canaryUrl("?sslMode=VERIFY_CA&connectTimeout=1000&socketTimeout=1000")));

		assertThat(attempt.failure()).as("startup must fail against the canary").isNotNull();
		assertThat(attempt.contextCreated()).as("guard accepted the configuration").isTrue();
		assertThat(canary.acceptedAfterSettling()).as("canary must see the connection attempt").isPositive();
	}

	@Test
	void guardRejectionAloneNeverOpensSockets() {
		assertThatThrownBy(() -> DatabaseSafetyGuard.verify(new org.springframework.mock.env.MockEnvironment()))
			.isInstanceOf(IllegalStateException.class);
		assertThat(canary.acceptedAfterSettling()).isZero();
	}

	private static List<String> rootMessages(Throwable failure) {
		List<String> messages = new ArrayList<>();
		for (Throwable t = failure; t != null; t = t.getCause()) {
			if (t.getMessage() != null) {
				messages.add(t.getMessage());
			}
		}
		return messages;
	}

	/** Loopback listener that only counts accepted connections; it never responds. */
	private static final class Canary implements AutoCloseable {

		private final ServerSocket server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));

		private final AtomicInteger accepted = new AtomicInteger();

		private final List<Socket> held = new CopyOnWriteArrayList<>();

		private final Thread acceptor;

		Canary() throws IOException {
			acceptor = new Thread(() -> {
				while (!server.isClosed()) {
					try {
						held.add(server.accept());
						accepted.incrementAndGet();
					}
					catch (IOException ex) {
						return;
					}
				}
			}, "canary-acceptor");
			acceptor.setDaemon(true);
			acceptor.start();
		}

		int port() {
			return server.getLocalPort();
		}

		int acceptedAfterSettling() {
			try {
				Thread.sleep(200);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return accepted.get();
		}

		@Override
		public void close() throws IOException {
			server.close();
			for (Socket socket : held) {
				socket.close();
			}
		}

	}

}
