package com.medivault.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.medivault.IntegrationTestSupport;

/**
 * Checks that need a real servlet container (MockMvc does not execute forwards and does not
 * apply Tomcat's session-cookie settings).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealServerSecurityTests extends IntegrationTestSupport {

	/** Test-only route: a public URL that internally FORWARDs to the admin-only page. */
	@TestConfiguration
	static class ForwardToAdminConfig {

		@Bean
		WebMvcConfigurer forwardPublicUrlToAdmin() {
			return new WebMvcConfigurer() {
				@Override
				public void addViewControllers(ViewControllerRegistry registry) {
					registry.addViewController("/favicon.ico").setViewName("forward:/admin");
				}
			};
		}

	}

	private static final Pattern SESSION_COOKIE = Pattern.compile("JSESSIONID=([^;]+)");

	private static final Pattern CSRF_TOKEN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

	@LocalServerPort
	private int port;

	private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	private HttpResponse<String> get(String path, String sessionId) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
		if (sessionId != null) {
			request.header("Cookie", "JSESSIONID=" + sessionId);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> postForm(String path, String sessionId, String form) throws Exception {
		return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			.header("Cookie", "JSESSIONID=" + sessionId)
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(form))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

	/** Logs in through the real login form; returns the session id after login. */
	private String logIn(String email) throws Exception {
		HttpResponse<String> loginPage = get("/login", null);
		String anonymousSession = group(SESSION_COOKIE, sessionCookieHeader(loginPage));
		String csrf = group(CSRF_TOKEN, loginPage.body());
		HttpResponse<String> login = postForm("/login", anonymousSession,
				"_csrf=" + encode(csrf) + "&email=" + encode(email) + "&password=" + encode(PASSWORD));
		assertThat(login.statusCode()).isEqualTo(302);
		return group(SESSION_COOKIE, sessionCookieHeader(login));
	}

	@Test
	void sessionCookieIsHttpOnlyAndSameSiteLax() throws Exception {
		String cookie = sessionCookieHeader(get("/login", null));

		assertThat(cookie).contains("HttpOnly").containsIgnoringCase("SameSite=Lax").contains("Path=/");
		// Expected and documented: no "Secure" flag without HTTPS in local development.
		assertThat(cookie).doesNotContainIgnoringCase("Secure");
	}

	@Test
	void realLoginIssuesANewSessionId() throws Exception {
		createPatient("Asha Fictional", "asha@example.test");
		HttpResponse<String> loginPage = get("/login", null);
		String anonymousSession = group(SESSION_COOKIE, sessionCookieHeader(loginPage));
		String csrf = group(CSRF_TOKEN, loginPage.body());

		HttpResponse<String> login = postForm("/login", anonymousSession,
				"_csrf=" + encode(csrf) + "&email=asha%40example.test&password=" + encode(PASSWORD));

		assertThat(login.statusCode()).isEqualTo(302);
		assertThat(login.headers().firstValue("Location").orElseThrow()).endsWith("/dashboard");
		String authenticatedSession = group(SESSION_COOKIE, sessionCookieHeader(login));
		assertThat(authenticatedSession).isNotEqualTo(anonymousSession);
		assertThat(get("/dashboard", authenticatedSession).statusCode()).isEqualTo(200);
		assertThat(get("/dashboard", anonymousSession).statusCode()).as("old session id is useless").isEqualTo(302);
	}

	@Test
	void internalForwardCannotBypassAuthorizationForAnonymousUsers() throws Exception {
		createAdmin("Admin Fictional", "admin@example.test");

		HttpResponse<String> response = get("/favicon.ico", null);

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("/login");
		assertThat(response.body()).doesNotContain("admin@example.test");
	}

	@Test
	void internalForwardCannotBypassRoleChecksForPatients() throws Exception {
		createAdmin("Admin Fictional", "admin@example.test");
		createPatient("Asha Fictional", "asha@example.test");
		String patientSession = logIn("asha@example.test");

		HttpResponse<String> response = get("/favicon.ico", patientSession);

		assertThat(response.statusCode()).isEqualTo(403);
		assertThat(response.body()).doesNotContain("admin@example.test");
	}

}
