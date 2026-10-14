package com.medivault.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Pure rule tests for {@link HostedProfileGuard} (no Spring context). The effective "railway"
 * configuration and the real-server behaviour are covered by {@code RailwayProfileTests}.
 */
class HostedProfileGuardTests {

	/** A configuration that satisfies every 'railway' rule; tests then break exactly one thing. */
	private static MockEnvironment valid(String... profiles) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(profiles.length == 0 ? new String[] { "local-file", "railway" } : profiles);
		environment.setProperty("server.servlet.session.cookie.secure", "true");
		environment.setProperty("server.forward-headers-strategy", "native");
		return environment;
	}

	@Test
	void validRailwayConfigurationIsAccepted() {
		assertThatCode(() -> HostedProfileGuard.verify(valid())).doesNotThrowAnyException();
		MockEnvironment relaxed = valid();
		relaxed.setProperty("server.servlet.session.cookie.secure", " TRUE ");
		relaxed.setProperty("server.forward-headers-strategy", "NATIVE");
		assertThatCode(() -> HostedProfileGuard.verify(relaxed)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = { "local-file", "local-h2", "test", "mysql", "local-file,demo", "" })
	void otherProfilesAreNotAffected(String profiles) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(profiles.isEmpty() ? new String[0] : profiles.split(","));
		environment.setProperty("server.servlet.session.cookie.secure", "false");
		assertThatCode(() -> HostedProfileGuard.verify(environment)).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = { "railway", "railway,local-file", "local-h2,railway", "test,railway", "mysql,railway" })
	void railwayRequiresLocalFileListedBeforeIt(String profiles) {
		assertThatThrownBy(() -> HostedProfileGuard.verify(valid(profiles.split(","))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage(HostedProfileGuard.LOCAL_FILE_REQUIRED_MESSAGE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "local-file,railway,demo", "demo,local-file,railway", "local-file,demo,railway" })
	void railwayRefusesDemo(String profiles) {
		assertThatThrownBy(() -> HostedProfileGuard.verify(valid(profiles.split(","))))
			.hasMessage(HostedProfileGuard.DEMO_MESSAGE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "false", "", "yes" })
	void railwayRequiresASecureSessionCookie(String value) {
		MockEnvironment environment = valid();
		environment.setProperty("server.servlet.session.cookie.secure", value);
		assertThatThrownBy(() -> HostedProfileGuard.verify(environment))
			.hasMessage(HostedProfileGuard.SECURE_COOKIE_MESSAGE);

		MockEnvironment missing = new MockEnvironment();
		missing.setActiveProfiles("local-file", "railway");
		missing.setProperty("server.forward-headers-strategy", "native");
		assertThatThrownBy(() -> HostedProfileGuard.verify(missing)).hasMessage(HostedProfileGuard.SECURE_COOKIE_MESSAGE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "none", "framework", "" })
	void railwayRequiresNativeForwardedHeaderHandling(String value) {
		MockEnvironment environment = valid();
		environment.setProperty("server.forward-headers-strategy", value);
		assertThatThrownBy(() -> HostedProfileGuard.verify(environment))
			.hasMessage(HostedProfileGuard.FORWARD_HEADERS_MESSAGE);

		MockEnvironment missing = new MockEnvironment();
		missing.setActiveProfiles("local-file", "railway");
		missing.setProperty("server.servlet.session.cookie.secure", "true");
		assertThatThrownBy(() -> HostedProfileGuard.verify(missing)).hasMessage(HostedProfileGuard.FORWARD_HEADERS_MESSAGE);
	}

	@Test
	void unsetEnvironmentVariablesAreRefusedWithoutValues() {
		MockEnvironment environment = valid();
		environment.setProperty("server.servlet.session.cookie.secure", "${MEDIVAULT_UNSET_FOR_TEST_8f3c}");
		assertThatThrownBy(() -> HostedProfileGuard.verify(environment)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Refusing to start")
			.hasMessageNotContaining("MEDIVAULT_UNSET_FOR_TEST_8f3c");
	}

	@Test
	void messagesNeverContainSettingValues() {
		MockEnvironment environment = valid();
		environment.setProperty("server.forward-headers-strategy", "secret-looking-value");
		assertThatThrownBy(() -> HostedProfileGuard.verify(environment))
			.satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("secret-looking-value"));
	}

}
