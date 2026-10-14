package com.medivault.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.diagnostics.FailureAnalysis;

import com.medivault.document.crypto.DocumentKeyException;
import com.medivault.document.crypto.DocumentKeyException.Problem;

/** Pure tests for {@link StartupRefusalFailureAnalyzer} (no Spring context). */
class StartupRefusalFailureAnalyzerTests {

	private final StartupRefusalFailureAnalyzer analyzer = new StartupRefusalFailureAnalyzer();

	@Test
	void guardRefusalBecomesAShortReport() {
		FailureAnalysis analysis = analyzer
			.analyze(new IllegalStateException(DatabaseSafetyGuard.NO_DATABASE_PROFILE_MESSAGE));
		assertThat(analysis).isNotNull();
		assertThat(analysis.getDescription()).startsWith("No database profile is active.");
		assertThat(analysis.getAction()).contains("run-demo.cmd");
		assertThat(analysis.getCause()).isNull();
	}

	@Test
	void keyRefusalNestedInBeanCreationIsFound() {
		Throwable failure = new BeanCreationException("documentController",
				new BeanCreationException("documentCipher", new DocumentKeyException(Problem.NOT_CONFIGURED)));
		FailureAnalysis analysis = analyzer.analyze(failure);
		assertThat(analysis).isNotNull();
		assertThat(analysis.getDescription()).isEqualTo(Problem.NOT_CONFIGURED.detail());
	}

	@Test
	void otherFailuresAreLeftToSpringBoot() {
		assertThat(analyzer.analyze(new IllegalStateException("Something else"))).isNull();
		assertThat(analyzer.analyze(new IllegalStateException((String) null))).isNull();
		assertThat(analyzer.analyze(new RuntimeException("Refusing to start: not an IllegalStateException"))).isNull();
	}

}
