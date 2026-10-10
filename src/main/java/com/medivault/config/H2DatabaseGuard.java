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
 * Startup checks that keep the in-memory H2 database and real databases apart:
 * <ol>
 * <li>H2 is refused unless the {@code local-h2} or {@code test} profile is active. Without this,
 * an {@code H2} JDBC URL, or an empty {@code spring.datasource.url} (which makes Spring Boot fall
 * back to an embedded database), would silently run a deployment on a throwaway in-memory
 * database.</li>
 * <li>The {@code local-h2} profile is refused unless the datasource really is in-memory H2
 * ({@code jdbc:h2:mem:}). Environment variables such as {@code SPRING_DATASOURCE_URL} outrank
 * profile files, so without this check {@code local-h2} (and its demo records) could silently
 * run against MySQL.</li>
 * </ol>
 * Runs before the application context, so no database connection is ever opened. Failure
 * messages never include the JDBC URL, because URLs can contain credentials.
 * <p>
 * Registered in {@code META-INF/spring.factories}.
 */
public class H2DatabaseGuard implements EnvironmentPostProcessor {

	static final String LOCAL_H2_PROFILE = "local-h2";

	static final Set<String> PROFILES_ALLOWING_H2 = Set.of(LOCAL_H2_PROFILE, "test");

	static final String FAILURE_MESSAGE = "Refusing to start: the configured database is H2 (spring.datasource.url "
			+ "is an H2 URL or is empty), but neither the 'local-h2' nor the 'test' profile is active. "
			+ "MediVault must use MySQL outside local development and tests. Set DB_URL to a MySQL 8.x JDBC URL, "
			+ "or start with the 'local-h2' profile for a local in-memory demo.";

	static final String LOCAL_H2_MISMATCH_MESSAGE = "Refusing to start: the 'local-h2' profile requires its "
			+ "in-memory H2 database (a jdbc:h2:mem: URL), but the configured datasource is different, most likely "
			+ "because a SPRING_DATASOURCE_URL environment variable overrides the profile. Clear the SPRING_DATASOURCE_* "
			+ "environment variables, or start without the 'local-h2' profile to use MySQL.";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		verify(environment);
	}

	static void verify(Environment environment) {
		String url = environment.getProperty("spring.datasource.url");
		// The URL itself is deliberately never included in a message: JDBC URLs can contain credentials.
		if (selectsH2(url) && !allowsH2(environment)) {
			throw new IllegalStateException(FAILURE_MESSAGE);
		}
		if (environment.matchesProfiles(LOCAL_H2_PROFILE) && !isInMemoryH2(url)) {
			throw new IllegalStateException(LOCAL_H2_MISMATCH_MESSAGE);
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

}
