package com.medivault.config;

import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;

/**
 * Turns MediVault's deliberate startup refusals (database profile rules, the document key checks,
 * the "railway" rules) into Spring Boot's short "APPLICATION FAILED TO START" report, instead of a
 * long stack trace that hides the one useful sentence. Only exceptions whose message starts with
 * {@link #PREFIX} are handled; those messages are fixed text and never contain paths, URLs or
 * credentials. Registered in {@code META-INF/spring.factories}.
 */
public class StartupRefusalFailureAnalyzer implements FailureAnalyzer {

	static final String PREFIX = "Refusing to start: ";

	static final String ACTION = "This is one of MediVault's safety checks, not a crash. To run the demo from the "
			+ "project folder in one step, use:\n\n    run-demo.cmd              (disposable demo)\n"
			+ "    run-demo.cmd persistent   (data kept between runs)\n\n"
			+ "It builds the app, creates the document key once and starts the right profiles. See README, "
			+ "\"Quick start\".";

	@Override
	public FailureAnalysis analyze(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof IllegalStateException && cause.getMessage() != null
					&& cause.getMessage().startsWith(PREFIX)) {
				String reason = cause.getMessage().substring(PREFIX.length());
				String description = reason.isEmpty() ? reason
						: Character.toUpperCase(reason.charAt(0)) + reason.substring(1);
				return new FailureAnalysis(description, ACTION, null);
			}
			if (cause.getCause() == cause) {
				break;
			}
		}
		return null;
	}

}
