package com.medivault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.medivault.demo.DemoRecordSeeder;
import com.medivault.user.Role;
import com.medivault.user.User;
import com.medivault.user.UserRepository;

/**
 * The hosted demo configuration "local-file,railway", started for real with a JUnit temporary
 * storage root. The real-server tests override {@code server.address} with {@code 127.0.0.1} so
 * the test server is never reachable from the network; everything else is the effective profile
 * configuration. A request from 127.0.0.1 counts as coming from an internal proxy (Tomcat's
 * RemoteIpValve default), which is how these tests stand in for Railway's proxy. Whether Railway's
 * real proxy address is treated as internal can only be verified after deployment.
 */
class RailwayProfileTests {

	private static final String PROFILES = "local-file,railway";

	private static final Pattern SESSION_COOKIE = Pattern.compile("JSESSIONID=([^;]+)");

	private static final Pattern CSRF_TOKEN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

	@TempDir
	Path tempDir;

	private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	private List<String> args(String profiles, String... extra) {
		List<String> args = new ArrayList<>(List.of("--spring.profiles.active=" + profiles,
				"--medivault.storage.root=" + tempDir.resolve("MediVault"), "--spring.main.banner-mode=off",
				"--logging.level.root=WARN", "--medivault.admin.email=", "--medivault.admin.password="));
		args.addAll(List.of(extra));
		return args;
	}

	/** For startup refusals: the guards stop startup before any context, database or port exists. */
	private ConfigurableApplicationContext start(String profiles, String... extra) {
		return new SpringApplicationBuilder(MedivaultApplication.class).run(args(profiles, extra).toArray(String[]::new));
	}

	/** Runs startup only up to the prepared (and guard-checked) environment, then aborts: no context, no I/O. */
	private Environment preparedEnvironment(String profiles, String... extra) {
		AtomicReference<Environment> prepared = new AtomicReference<>();
		ApplicationListener<ApplicationEnvironmentPreparedEvent> capture = event -> {
			prepared.set(event.getEnvironment());
			throw new StopAfterEnvironment();
		};
		assertThatThrownBy(() -> new SpringApplicationBuilder(MedivaultApplication.class).listeners(capture)
			.run(args(profiles, extra).toArray(String[]::new))).isInstanceOf(StopAfterEnvironment.class);
		return prepared.get();
	}

	private static final class StopAfterEnvironment extends RuntimeException {

	}

	/** Starts Tomcat on a random port, bound to loopback only (see class comment). */
	private ConfigurableApplicationContext startServer() {
		return new SpringApplicationBuilder(MedivaultApplication.class)
			.run(args(PROFILES, "--server.port=0", "--server.address=127.0.0.1").toArray(String[]::new));
	}

	private static int port(ConfigurableApplicationContext context) {
		return ((WebServerApplicationContext) context).getWebServer().getPort();
	}

	private HttpResponse<String> get(int port, String path, String forwardedProto, String sessionId) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
		if (forwardedProto != null) {
			request.header("X-Forwarded-Proto", forwardedProto);
		}
		if (sessionId != null) {
			request.header("Cookie", "JSESSIONID=" + sessionId);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static String sessionCookieHeader(HttpResponse<?> response) {
		return response.headers()
			.allValues("Set-Cookie")
			.stream()
			.filter(c -> c.startsWith("JSESSIONID="))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no JSESSIONID cookie set"));
	}

	private static String group(Pattern pattern, String text) {
		Matcher matcher = pattern.matcher(text);
		assertThat(matcher.find()).as("pattern %s", pattern).isTrue();
		return matcher.group(1);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	// --- Effective configuration ------------------------------------------------------------

	@Test
	void railwayOverridesOnlyHowTheServerIsReached() {
		Environment environment = preparedEnvironment(PROFILES, "--PORT=4321");
		assertThat(environment.getActiveProfiles()).containsExactly("local-file", "railway");
		assertThat(environment.getProperty("server.address")).isEqualTo("0.0.0.0");
		assertThat(environment.getProperty("server.port")).isEqualTo("4321");
		assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("native");
		assertThat(environment.getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
		// local-file's storage and schema settings are unchanged.
		Path root = tempDir.resolve("MediVault");
		assertThat(Path.of(environment.getProperty("medivault.documents.directory")).normalize())
			.isEqualTo(root.resolve("records"));
		assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:h2:file:");
		assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
		assertThat(environment.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
		assertThat(root).as("preparing the environment creates nothing").doesNotExist();
	}

	@Test
	void portDefaultsTo8080WithoutAPortVariable() {
		assumeTrue(System.getenv("PORT") == null, "a PORT environment variable is set on this machine");
		assertThat(preparedEnvironment(PROFILES).getProperty("server.port")).isEqualTo("8080");
	}

	@Test
	void unsafeOrMisorderedHostedConfigurationsAreRefusedAtStartup() {
		assertThatThrownBy(() -> start("local-file,railway,demo").close())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("'demo' profile cannot be combined with 'railway'");
		assertThatThrownBy(() -> start("railway,local-file").close())
			.hasMessageContaining("requires the 'local-file' profile, listed before it");
		assertThatThrownBy(() -> start("railway").close())
			.hasMessageContaining("requires the 'local-file' profile, listed before it");
		assertThatThrownBy(() -> start(PROFILES, "--server.servlet.session.cookie.secure=false").close())
			.hasMessageContaining("requires server.servlet.session.cookie.secure=true");
		assertThatThrownBy(() -> start(PROFILES, "--server.forward-headers-strategy=framework").close())
			.hasMessageContaining("requires server.forward-headers-strategy=native");
		assertThat(tempDir.resolve("MediVault")).as("nothing is created before the refusal").doesNotExist();
	}

	// --- Real server: storage, forwarded headers, Secure cookie, HSTS -----------------------

	@Test
	void startsWithTheLocalFileStorageAndWithoutDemoSeeding() {
		try (ConfigurableApplicationContext context = startServer()) {
			Path root = tempDir.resolve("MediVault");
			assertThat(root.resolve("data").resolve("medivault.mv.db")).isRegularFile();
			assertThat(root.resolve("records")).isDirectory();
			assertThat(context.getBean(Flyway.class).info().applied()).extracting(info -> info.getVersion().getVersion())
				.containsExactly("1", "2", "3", "4");
			assertThat(context.getBeanProvider(DemoRecordSeeder.class).getIfAvailable()).as("no demo seeding").isNull();
		}
	}

	@Test
	void behindTheProxyRequestsAreTreatedAsHttps() throws Exception {
		try (ConfigurableApplicationContext context = startServer()) {
			int port = port(context);

			HttpResponse<String> login = get(port, "/login", "https", null);
			assertThat(login.statusCode()).isEqualTo(200);
			assertThat(login.headers().firstValue("Strict-Transport-Security")).hasValueSatisfying(
					hsts -> assertThat(hsts).startsWith("max-age=31536000").contains("includeSubDomains"));
			assertThat(sessionCookieHeader(login)).contains("Secure").contains("HttpOnly").containsIgnoringCase("SameSite=Lax");

			HttpResponse<String> protectedPage = get(port, "/dashboard", "https", null);
			assertThat(protectedPage.statusCode()).isEqualTo(302);
			assertThat(protectedPage.headers().firstValue("Location").orElseThrow()).startsWith("https://")
				.endsWith("/login");
		}
	}

	@Test
	void withoutTheForwardedProtoHeaderThereIsNoHstsButTheCookieIsStillSecure() throws Exception {
		try (ConfigurableApplicationContext context = startServer()) {
			int port = port(context);
			for (String proto : new String[] { null, "http" }) {
				HttpResponse<String> login = get(port, "/login", proto, null);
				assertThat(login.statusCode()).isEqualTo(200);
				assertThat(login.headers().firstValue("Strict-Transport-Security")).as("HSTS only over HTTPS").isEmpty();
				assertThat(sessionCookieHeader(login)).as("cookie is Secure regardless").contains("Secure");
				assertThat(get(port, "/dashboard", proto, null).headers().firstValue("Location").orElseThrow())
					.startsWith("http://");
			}
		}
	}

	@Test
	void loginBehindTheProxyRedirectsOverHttpsWithANewSession() throws Exception {
		try (ConfigurableApplicationContext context = startServer()) {
			int port = port(context);
			context.getBean(UserRepository.class)
				.save(new User("Fictional Patient", "railway@example.test",
						context.getBean(PasswordEncoder.class).encode("Fictional123"), Role.PATIENT));

			HttpResponse<String> loginPage = get(port, "/login", "https", null);
			String anonymousSession = group(SESSION_COOKIE, sessionCookieHeader(loginPage));
			String csrf = group(CSRF_TOKEN, loginPage.body());
			HttpResponse<String> login = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/login"))
				.header("X-Forwarded-Proto", "https")
				.header("Cookie", "JSESSIONID=" + anonymousSession)
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString("_csrf=" + encode(csrf) + "&email="
						+ encode("railway@example.test") + "&password=" + encode("Fictional123")))
				.build(), HttpResponse.BodyHandlers.ofString());

			assertThat(login.statusCode()).isEqualTo(302);
			assertThat(login.headers().firstValue("Location").orElseThrow()).startsWith("https://").endsWith("/dashboard");
			String session = group(SESSION_COOKIE, sessionCookieHeader(login));
			assertThat(session).isNotEqualTo(anonymousSession);
			assertThat(sessionCookieHeader(login)).contains("Secure");
			assertThat(get(port, "/dashboard", "https", session).statusCode()).isEqualTo(200);
		}
	}

}
