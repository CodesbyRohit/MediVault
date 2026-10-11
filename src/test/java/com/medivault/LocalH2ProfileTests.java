package com.medivault;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.test.context.ActiveProfiles;

/**
 * The MySQL-free development mode ("local-h2" profile): in-memory H2 only, no MySQL settings,
 * no Flyway, and reachable from this computer only. Runs with profiles "test" + "local-h2";
 * the test H2 database still wins because {@code @TestPropertySource} outranks profile files.
 */
@ActiveProfiles("local-h2")
class LocalH2ProfileTests extends IntegrationTestSupport {

	@Autowired
	private ConfigurableEnvironment environment;

	@Autowired
	private ApplicationContext context;

	private PropertySource<?> localH2File() {
		return environment.getPropertySources()
			.stream()
			.filter(source -> source.getName().contains("application-local-h2.properties"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("application-local-h2.properties was not loaded"));
	}

	@Test
	void profileFileSelectsInMemoryH2WithoutAnyMySqlSetting() {
		PropertySource<?> file = localH2File();
		assertThat(String.valueOf(file.getProperty("spring.datasource.url"))).startsWith("jdbc:h2:mem:medivault_local");
		assertThat(file.getProperty("spring.datasource.driver-class-name")).isEqualTo("org.h2.Driver");
		assertThat(file.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
		assertThat(file.getProperty("spring.flyway.enabled")).isEqualTo("false");
		assertThat(file.getProperty("spring.flyway.user")).isNull();
		assertThat(file.getProperty("spring.flyway.locations")).isNull();
	}

	@Test
	void localModeIsReachableFromThisComputerOnlyAndHasNoLiveReloadServer() {
		assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
		assertThat(environment.getProperty("spring.devtools.livereload.enabled")).isEqualTo("false");
		assertThat(environment.getProperty("spring.h2.console.enabled")).isEqualTo("false");
	}

	@Test
	void flywayDoesNotRunInLocalMode() {
		assertThat(context.getBeanNamesForType(Flyway.class)).isEmpty();
	}

}
