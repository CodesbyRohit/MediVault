package com.medivault.config;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

/**
 * Startup checks for the hosted demo profile {@code railway} (Stage 1: one Railway service with a
 * persistent volume, fictional data only). Like {@link DatabaseSafetyGuard} it runs as an
 * {@link EnvironmentPostProcessor}, so a refused configuration never opens a database or a port.
 * Nothing is checked unless {@code railway} is active. Then:
 * <ol>
 * <li>{@code local-file} must be active and listed before {@code railway}: profile files listed
 * later win, so only in that order does {@code railway} replace local-file's loopback-only
 * {@code server.address}. {@code local-file} is the only storage approved for the hosted demo, and
 * {@link DatabaseSafetyGuard} applies all of its rules.</li>
 * <li>{@code demo} is refused: a hosted instance never creates fictional sample records.</li>
 * <li>The session cookie must be {@code Secure}, and forwarded headers must be handled by Tomcat
 * ({@code server.forward-headers-strategy=native}), so an environment variable cannot quietly
 * weaken either.</li>
 * </ol>
 * Messages never contain setting values. Registered in {@code META-INF/spring.factories}.
 */
public class HostedProfileGuard implements EnvironmentPostProcessor {

	static final String RAILWAY_PROFILE = "railway";

	static final String LOCAL_FILE_REQUIRED_MESSAGE = "Refusing to start: the 'railway' profile requires the "
			+ "'local-file' profile, listed before it. Start with exactly 'local-file,railway'.";

	static final String DEMO_MESSAGE = "Refusing to start: the 'demo' profile cannot be combined with 'railway'; "
			+ "a hosted instance never creates fictional sample records.";

	static final String SECURE_COOKIE_MESSAGE = "Refusing to start: the 'railway' profile requires "
			+ "server.servlet.session.cookie.secure=true.";

	static final String FORWARD_HEADERS_MESSAGE = "Refusing to start: the 'railway' profile requires "
			+ "server.forward-headers-strategy=native.";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		verify(environment);
	}

	static void verify(Environment environment) {
		List<String> profiles = Arrays.asList(environment.getActiveProfiles());
		int railway = profiles.indexOf(RAILWAY_PROFILE);
		if (railway < 0) {
			return;
		}
		int localFile = profiles.indexOf(DatabaseSafetyGuard.LOCAL_FILE_PROFILE);
		if (localFile < 0 || localFile > railway) {
			throw new IllegalStateException(LOCAL_FILE_REQUIRED_MESSAGE);
		}
		if (profiles.contains("demo")) {
			throw new IllegalStateException(DEMO_MESSAGE);
		}
		if (!"true".equals(lower(DatabaseSafetyGuard.property(environment, "server.servlet.session.cookie.secure")))) {
			throw new IllegalStateException(SECURE_COOKIE_MESSAGE);
		}
		if (!"native".equals(lower(DatabaseSafetyGuard.property(environment, "server.forward-headers-strategy")))) {
			throw new IllegalStateException(FORWARD_HEADERS_MESSAGE);
		}
	}

	private static String lower(String value) {
		return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
	}

}
