package com.medivault.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;

import com.medivault.IntegrationTestSupport;
import com.medivault.user.User;

/**
 * Regression (M4 review): handled request errors are logged without request-controlled values.
 * Spring's own "Resolved [...]" warnings (DefaultHandlerExceptionResolver in every profile, the other
 * resolvers under DevTools) repeated, for example, a malformed record ID or the requested path.
 * Uses the default configuration; the DevTools case is checked with a real {@code spring-boot:run}.
 */
@ExtendWith(OutputCaptureExtension.class)
class RejectedRequestLoggingTests extends IntegrationTestSupport {

	private static final String RAW_VALUE = "zzRawValue7731";

	@Autowired
	private Environment environment;

	@Test
	void springsRawResolvedExceptionLinesStayOffAndTheSanitisedLoggerIsOn() {
		assertThat(environment.getProperty("spring.mvc.log-resolved-exception")).isEqualTo("false");
		assertThat(environment.getProperty("medivault.web.log-rejected-requests")).isEqualTo("true");
	}

	@Test
	void aMalformedIdIsLoggedWithoutItsValue(CapturedOutput output) throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(get("/records/" + RAW_VALUE).with(user(principal(asha)))).andExpect(status().isBadRequest());

		assertThat(output.getOut()).contains("Rejected request: GET /records/{id} -> MethodArgumentTypeMismatchException "
				+ "(parameter 'id' must be Long)")
			.doesNotContain(RAW_VALUE)
			.doesNotContain("Resolved [");
	}

	@Test
	void unknownRecordsAndMissingFilesAreLoggedWithoutThePath(CapturedOutput output) throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(get("/records/987654321").with(user(principal(asha)))).andExpect(status().isNotFound());
		mockMvc.perform(get("/css/" + RAW_VALUE + ".css")).andExpect(status().isNotFound());

		assertThat(output.getOut()).contains("Rejected request: GET /records/{id} -> RecordNotFoundException")
			.contains("-> NoResourceFoundException")
			.doesNotContain("987654321")
			.doesNotContain(RAW_VALUE)
			.doesNotContain("Resolved [");
	}

}
