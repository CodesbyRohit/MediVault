package com.medivault.config;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fail-closed validation of a MySQL JDBC URL, performed before any connection is attempted.
 * <p>
 * The parser is deliberately strict and self-contained (it does not reuse Connector/J's internal
 * parser): anything it does not fully understand is rejected. Accepted shape:
 * <pre>
 * jdbc:mysql://127.0.0.1:&lt;port&gt;/medivault_(dev|it)?sslMode=VERIFY_CA[&amp;allow-listed=...]
 * </pre>
 * Rejection messages name the rule that failed, never the URL itself (JDBC URLs can contain
 * credentials). This class performs no I/O.
 */
final class MySqlUrlPolicy {

	static final String PREFIX = "jdbc:mysql://";

	static final String REQUIRED_HOST = "127.0.0.1";

	static final int FORBIDDEN_PORT = 3306;

	private static final Pattern DATABASE = Pattern.compile("medivault_(dev|it)");

	private static final Pattern PORT = Pattern.compile("[0-9]{1,5}");

	private static final Pattern PRINTABLE_KEY = Pattern.compile("[A-Za-z0-9_]{1,40}");

	private static final Set<String> ALLOWED_SSL_MODES = Set.of("VERIFY_CA", "VERIFY_IDENTITY");

	/** Lower-case allow-list. Credentials, plaintext fallbacks and risky driver features are absent on purpose. */
	private static final Set<String> ALLOWED_PARAMETERS = Set.of("sslmode", "trustcertificatekeystoreurl",
			"trustcertificatekeystoretype", "connecttimeout", "sockettimeout", "connectiontimezone",
			"forceconnectiontimezonetosession", "characterencoding", "tlsversions");

	/** The validated connection target. {@code environment} is {@code "dev"} or {@code "it"}. */
	record Target(int port, String database, String environment) {
	}

	private MySqlUrlPolicy() {
	}

	static Target parse(String url, String setting) {
		if (url == null || url.isBlank()) {
			throw reject(setting, "it is missing");
		}
		if (!url.startsWith(PREFIX)) {
			// Name the scheme without the "://" form, so log scans for leaked JDBC URLs stay meaningful.
			throw reject(setting, "it must start with the jdbc:mysql scheme");
		}
		if (url.indexOf('#') >= 0 || url.chars().anyMatch(Character::isWhitespace)) {
			throw reject(setting, "fragments and whitespace are not allowed");
		}
		String rest = url.substring(PREFIX.length());
		int slash = rest.indexOf('/');
		if (slash < 0) {
			throw reject(setting, "a database name is required");
		}
		String authority = rest.substring(0, slash);
		if (authority.indexOf('@') >= 0) {
			throw reject(setting, "credentials are not allowed in the URL");
		}
		if (authority.indexOf(',') >= 0 || authority.indexOf('(') >= 0 || authority.indexOf('[') >= 0) {
			throw reject(setting, "only a single host " + REQUIRED_HOST + " is allowed");
		}
		int colon = authority.indexOf(':');
		String host = colon < 0 ? authority : authority.substring(0, colon);
		if (!REQUIRED_HOST.equals(host)) {
			throw reject(setting, "the host must be exactly " + REQUIRED_HOST);
		}
		String portText = colon < 0 ? "" : authority.substring(colon + 1);
		if (!PORT.matcher(portText).matches()) {
			throw reject(setting, "an explicit numeric port is required");
		}
		int port = Integer.parseInt(portText);
		if (port == FORBIDDEN_PORT) {
			throw reject(setting, "port " + FORBIDDEN_PORT + " is never allowed");
		}
		if (port < 1 || port > 65535) {
			throw reject(setting, "the port is out of range");
		}

		String pathAndQuery = rest.substring(slash + 1);
		int question = pathAndQuery.indexOf('?');
		String database = question < 0 ? pathAndQuery : pathAndQuery.substring(0, question);
		var databaseMatch = DATABASE.matcher(database);
		if (!databaseMatch.matches()) {
			throw reject(setting, "the database must be medivault_dev or medivault_it");
		}
		Map<String, String> parameters = parseParameters(question < 0 ? "" : pathAndQuery.substring(question + 1),
				setting);

		String sslMode = parameters.get("sslmode");
		if (sslMode == null) {
			throw reject(setting, "sslMode is required");
		}
		if (!ALLOWED_SSL_MODES.contains(sslMode.toUpperCase(Locale.ROOT))) {
			throw reject(setting, "sslMode must be VERIFY_CA or VERIFY_IDENTITY");
		}
		String trustStore = parameters.get("trustcertificatekeystoreurl");
		if (trustStore != null && !trustStore.toLowerCase(Locale.ROOT).startsWith("file:")) {
			throw reject(setting, "trustCertificateKeyStoreUrl must be a file: URL");
		}
		return new Target(port, database, databaseMatch.group(1));
	}

	private static Map<String, String> parseParameters(String query, String setting) {
		Map<String, String> parameters = new HashMap<>();
		if (query.isEmpty()) {
			return parameters;
		}
		for (String pair : query.split("&", -1)) {
			int equals = pair.indexOf('=');
			if (equals <= 0 || equals == pair.length() - 1) {
				throw reject(setting, "every parameter must be key=value");
			}
			String key = pair.substring(0, equals);
			String normalized = key.toLowerCase(Locale.ROOT);
			if (!ALLOWED_PARAMETERS.contains(normalized)) {
				throw reject(setting, "parameter "
						+ (PRINTABLE_KEY.matcher(key).matches() ? "'" + key + "'" : "(unprintable)")
						+ " is not on the allow-list");
			}
			if (parameters.put(normalized, pair.substring(equals + 1)) != null) {
				throw reject(setting, "duplicate parameter '" + key + "'");
			}
		}
		return parameters;
	}

	private static IllegalStateException reject(String setting, String reason) {
		return new IllegalStateException(
				"Refusing to start: " + setting + " was rejected by the MySQL connection policy (" + reason + ").");
	}

}
