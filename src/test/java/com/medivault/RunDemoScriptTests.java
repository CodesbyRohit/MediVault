package com.medivault;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * {@code run-demo.cmd} is the normal way to start MediVault. It must never enable the {@code demo}
 * profile, so a newly registered patient always starts without records, in both of its modes.
 * Reads the script only; nothing is executed.
 */
class RunDemoScriptTests {

	private static final Path SCRIPT = Path.of(System.getProperty("user.dir"), "run-demo.cmd");

	private static final Pattern PROFILE_ASSIGNMENT = Pattern.compile("(?i)set\\s+\"PROFILES=([^\"]*)\"");

	private static final Pattern PROFILES_ARGUMENT = Pattern.compile("(?i)--spring\\.profiles\\.active=(\\S+)");

	private static List<String> matches(Pattern pattern, String text) {
		Matcher matcher = pattern.matcher(text);
		return matcher.results().map(result -> result.group(1)).toList();
	}

	@Test
	void theLauncherSelectsOnlyTheDisposableOrThePersistentProfileWithoutDemo() throws IOException {
		String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

		// The script first clears PROFILES (set "PROFILES="); only the non-empty assignments choose a mode.
		assertThat(matches(PROFILE_ASSIGNMENT, script).stream().filter(value -> !value.isEmpty()).toList())
			.as("profile assignments in run-demo.cmd")
			.isNotEmpty()
			.containsOnly("local-h2", "local-file")
			.contains("local-h2", "local-file");
		assertThat(matches(PROFILES_ARGUMENT, script)).as("the profiles the server is started with")
			.containsExactly("%PROFILES%");
	}

}
