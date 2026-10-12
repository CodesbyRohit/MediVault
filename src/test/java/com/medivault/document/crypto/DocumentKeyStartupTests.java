package com.medivault.document.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

import com.medivault.MedivaultApplication;

/**
 * A real application start (profile {@code local-h2}, temporary folders) refuses to run without a
 * valid document key, never creates one, and never logs the key file's path or contents.
 */
@ExtendWith(OutputCaptureExtension.class)
class DocumentKeyStartupTests {

	@TempDir
	Path tempDir;

	private List<String> args(String keyFile) {
		return new ArrayList<>(List.of("--spring.profiles.active=local-h2", "--server.port=0",
				"--spring.main.banner-mode=off", "--medivault.admin.email=", "--medivault.admin.password=",
				"--medivault.documents.directory=" + tempDir.resolve("storage").resolve("records"),
				"--medivault.documents.key-file=" + keyFile));
	}

	private static Throwable rootCause(Throwable thrown) {
		Throwable cause = thrown;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause;
	}

	@Test
	void startsWithAValidKeyAndLogsOnlyItsId(CapturedOutput output) {
		Path key = TestKeys.writeKeyFile(tempDir.resolve("keys").resolve("document-key.json"));
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MedivaultApplication.class)
			.run(args(key.toString()).toArray(String[]::new))) {
			DocumentCipher cipher = context.getBean(DocumentCipher.class);
			assertThat(output).contains("Document encryption: key file loaded (1 enabled key(s); new uploads use key id "
					+ Integer.toUnsignedString(cipher.primaryKeyId()))
				.doesNotContain(key.toString())
				.doesNotContain("document-key.json");
		}
	}

	@Test
	void refusesToStartWithoutAKeyAndCreatesNone() throws Exception {
		assertThatThrownBy(() -> new SpringApplicationBuilder(MedivaultApplication.class)
			.run(args("").toArray(String[]::new)))
			.satisfies(thrown -> assertThat(rootCause(thrown)).isInstanceOf(DocumentKeyException.class)
				.hasMessageContaining("MEDIVAULT_DOCUMENT_KEY_FILE is not set"));
		try (var files = Files.walk(tempDir)) {
			assertThat(files.filter(Files::isRegularFile).toList()).as("no key was generated").isEmpty();
		}
	}

	@Test
	void refusesToStartWithAMalformedKeyWithoutRevealingThePath(CapturedOutput output) throws Exception {
		Path bad = Files.createDirectories(tempDir.resolve("keys")).resolve("document-key.json");
		Files.createFile(bad);
		KeyFilePermissions.restrictToOwner(bad);
		Files.writeString(bad, "{\"primaryKeyId\": 7, \"key\": [{\"keyData\": \"secret-looking-value\"}]}");

		assertThatThrownBy(() -> new SpringApplicationBuilder(MedivaultApplication.class)
			.run(args(bad.toString()).toArray(String[]::new)))
			.satisfies(thrown -> assertThat(rootCause(thrown)).isInstanceOf(DocumentKeyException.class)
				.hasMessageContaining("not a Tink JSON keyset")
				.message()
				.doesNotContain(tempDir.toString()));
		assertThat(output).doesNotContain("secret-looking-value").doesNotContain(bad.toString());
	}

	@Test
	void refusesToStartWithAKeyInsideTheStorageFolder() {
		Path inside = TestKeys.writeKeyFile(tempDir.resolve("storage").resolve("document-key.json"));
		assertThatThrownBy(() -> new SpringApplicationBuilder(MedivaultApplication.class)
			.run(args(inside.toString()).toArray(String[]::new)))
			.satisfies(thrown -> assertThat(rootCause(thrown)).isInstanceOf(DocumentKeyException.class)
				.hasMessageContaining("inside the MediVault storage folder"));
	}

	@Test
	void documentsUploadedBeforeARotationStayReadableAfterIt() throws Exception {
		Path key = TestKeys.writeKeyFile(tempDir.resolve("keys").resolve("document-key.json"));
		DocumentCipher before = new DocumentCipher(DocumentKeyRing.load(key.toString(), null));
		var raw = new java.io.ByteArrayOutputStream();
		try (var out = before.encrypt(raw, "8e3c2a4f-1b2d-4c5e-9f60-7a8b9c0d1e2f")) {
			out.write("fictional".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		}

		assertThat(new DocumentKeyTool(System.out, System.err).execute(new String[] { "rotate", key.toString() }))
			.isZero();

		DocumentCipher after = new DocumentCipher(DocumentKeyRing.load(key.toString(), null));
		assertThat(after.primaryKeyId()).isNotEqualTo(before.primaryKeyId());
		try (var in = after.decrypt(new java.io.ByteArrayInputStream(raw.toByteArray()),
				"8e3c2a4f-1b2d-4c5e-9f60-7a8b9c0d1e2f")) {
			assertThat(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("fictional");
		}
	}

}
