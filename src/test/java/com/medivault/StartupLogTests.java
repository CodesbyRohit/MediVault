package com.medivault;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Regression (M4 review): Spring Boot's startup-info lines name the Windows user and the project
 * folder ("Starting ... started by &lt;user&gt; in &lt;folder&gt;"); they are switched off with
 * {@code spring.main.log-startup-info=false}. Starts the disposable in-memory profile at INFO level.
 */
@ExtendWith(OutputCaptureExtension.class)
class StartupLogTests {

	@Test
	void startupLogNamesNeitherTheUserNorTheProjectFolder(CapturedOutput output) {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MedivaultApplication.class).run(
				"--spring.profiles.active=local-h2", "--server.port=0", "--spring.main.banner-mode=off",
				"--logging.level.root=INFO", "--medivault.admin.email=", "--medivault.admin.password=")) {
			assertThat(context.getEnvironment().getProperty("spring.main.log-startup-info")).isEqualTo("false");
		}

		assertThat(output.getOut()).contains("Tomcat started on port")
			.doesNotContain("started by")
			.doesNotContain("Starting MedivaultApplication")
			.doesNotContain(System.getProperty("user.name"))
			.doesNotContain(System.getProperty("user.dir"));
	}

}
