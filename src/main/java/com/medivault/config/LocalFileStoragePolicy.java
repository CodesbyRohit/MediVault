package com.medivault.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Fail-closed validation of the {@code local-file} profile's storage settings, performed before any
 * connection is attempted. Accepted shape:
 * <pre>
 * medivault.storage.root          an absolute path without ';'
 * spring.datasource.url           jdbc:h2:file:&lt;root&gt;/data/&lt;name&gt;[;allow-listed H2 setting=value...]
 * medivault.documents.directory   &lt;root&gt;/records
 * </pre>
 * Existing {@code root}, {@code data} and {@code records} entries must be real directories, not
 * symbolic links or junctions. Rejection messages name the rule that failed, never a URL or path
 * (paths contain the Windows user name). This class only reads file attributes; it never creates
 * or deletes anything.
 */
final class LocalFileStoragePolicy {

	static final String ROOT_SETTING = "medivault.storage.root";

	static final String DOCUMENTS_SETTING = "medivault.documents.directory";

	static final String URL_SETTING = "spring.datasource.url";

	static final String PREFIX = "jdbc:h2:file:";

	static final String DATA_DIRECTORY = "data";

	static final String RECORDS_DIRECTORY = "records";

	private static final Pattern DATABASE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

	private static final Pattern SETTING_VALUE = Pattern.compile("[A-Za-z0-9_]{1,20}");

	/**
	 * Upper-case allow-list. Settings that run code or SQL (INIT), open network access (AUTO_SERVER),
	 * carry credentials (USER, PASSWORD) or change file handling are absent on purpose.
	 */
	private static final Set<String> ALLOWED_SETTINGS = Set.of("MODE", "DATABASE_TO_LOWER", "DB_CLOSE_ON_EXIT");

	private LocalFileStoragePolicy() {
	}

	/** @return the normalised, absolute storage root */
	static Path root(String value) {
		if (value == null || value.isBlank()) {
			throw reject(ROOT_SETTING, "it is missing");
		}
		if (value.indexOf(';') >= 0 || value.chars().anyMatch(Character::isISOControl)) {
			throw reject(ROOT_SETTING, "it must not contain ';' or control characters");
		}
		Path root = path(value.strip(), ROOT_SETTING);
		if (!root.isAbsolute()) {
			throw reject(ROOT_SETTING, "it must be an absolute path");
		}
		return root.normalize();
	}

	static void verify(String url, String documentsDirectory, Path root) {
		if (url == null || !url.startsWith(PREFIX)) {
			throw reject(URL_SETTING, "it must be a jdbc:h2:file: URL");
		}
		if (url.chars().anyMatch(Character::isISOControl)) {
			throw reject(URL_SETTING, "control characters are not allowed");
		}
		String rest = url.substring(PREFIX.length());
		int semicolon = rest.indexOf(';');
		Path database = path(semicolon < 0 ? rest : rest.substring(0, semicolon), URL_SETTING);
		if (semicolon >= 0) {
			verifySettings(rest.substring(semicolon + 1));
		}
		if (!database.isAbsolute()) {
			throw reject(URL_SETTING, "the database path must be absolute");
		}
		database = database.normalize();
		Path dataDirectory = root.resolve(DATA_DIRECTORY);
		if (database.getFileName() == null || !DATABASE_NAME.matcher(database.getFileName().toString()).matches()
				|| !dataDirectory.equals(database.getParent())) {
			throw reject(URL_SETTING, "the database must be a plainly named file directly inside the storage "
					+ "root's 'data' folder");
		}

		if (documentsDirectory == null || documentsDirectory.isBlank()) {
			throw reject(DOCUMENTS_SETTING, "it is missing");
		}
		Path recordsDirectory = root.resolve(RECORDS_DIRECTORY);
		if (!recordsDirectory.equals(path(documentsDirectory.strip(), DOCUMENTS_SETTING).normalize())) {
			throw reject(DOCUMENTS_SETTING, "it must be the storage root's 'records' folder");
		}

		requireDirectoryIfPresent(root, ROOT_SETTING);
		requireDirectoryIfPresent(dataDirectory, URL_SETTING);
		requireDirectoryIfPresent(recordsDirectory, DOCUMENTS_SETTING);
	}

	private static void verifySettings(String settings) {
		Set<String> seen = new HashSet<>();
		for (String setting : settings.split(";", -1)) {
			int equals = setting.indexOf('=');
			if (equals <= 0 || equals == setting.length() - 1) {
				throw reject(URL_SETTING, "every H2 setting must be KEY=VALUE");
			}
			String key = setting.substring(0, equals).toUpperCase(Locale.ROOT);
			if (!ALLOWED_SETTINGS.contains(key)) {
				throw reject(URL_SETTING, "an H2 setting is not on the allow-list");
			}
			if (!SETTING_VALUE.matcher(setting.substring(equals + 1)).matches()) {
				throw reject(URL_SETTING, "H2 setting values must be plain words");
			}
			if (!seen.add(key)) {
				throw reject(URL_SETTING, "duplicate H2 setting");
			}
		}
	}

	/** An existing entry must be a real directory: not a file, symbolic link or junction. */
	private static void requireDirectoryIfPresent(Path directory, String setting) {
		BasicFileAttributes attributes;
		try {
			attributes = Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		}
		catch (NoSuchFileException ex) {
			return;
		}
		catch (IOException | SecurityException ex) {
			throw reject(setting, "its folder cannot be inspected");
		}
		// On Windows a junction reports isDirectory() AND isOther() (a reparse point), so both are checked.
		if (!attributes.isDirectory() || attributes.isOther() || attributes.isSymbolicLink()) {
			throw reject(setting, "its folder exists but is not a plain directory (files, symbolic links and "
					+ "junctions are refused)");
		}
	}

	private static Path path(String value, String setting) {
		try {
			return Path.of(value);
		}
		catch (InvalidPathException ex) {
			throw reject(setting, "it is not a valid path");
		}
	}

	private static IllegalStateException reject(String setting, String reason) {
		return new IllegalStateException(
				"Refusing to start: " + setting + " was rejected by the local-file storage policy (" + reason + ").");
	}

}
