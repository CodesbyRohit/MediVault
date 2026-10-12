package com.medivault.document.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The offline key tool: generate never overwrites, rotate keeps old keys, nothing prints key material. */
class DocumentKeyToolTests {

	private static final String STORAGE_KEY = "8e3c2a4f-1b2d-4c5e-9f60-7a8b9c0d1e2f";

	@TempDir
	Path tempDir;

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	private final ByteArrayOutputStream err = new ByteArrayOutputStream();

	private int run(String... args) {
		return new DocumentKeyTool(new PrintStream(out, true, StandardCharsets.UTF_8),
				new PrintStream(err, true, StandardCharsets.UTF_8)).execute(args);
	}

	private String output() {
		return out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8);
	}

	private static byte[] encrypt(DocumentCipher cipher, String text) throws Exception {
		ByteArrayOutputStream raw = new ByteArrayOutputStream();
		try (OutputStream stream = cipher.encrypt(raw, STORAGE_KEY)) {
			stream.write(text.getBytes(StandardCharsets.US_ASCII));
		}
		return raw.toByteArray();
	}

	private static String decrypt(DocumentCipher cipher, byte[] ciphertext) throws Exception {
		try (InputStream in = cipher.decrypt(new ByteArrayInputStream(ciphertext), STORAGE_KEY)) {
			return new String(in.readAllBytes(), StandardCharsets.US_ASCII);
		}
	}

	private static DocumentCipher load(Path file) {
		return new DocumentCipher(DocumentKeyRing.load(file.toString(), null));
	}

	@Test
	void generateCreatesALoadableOwnerOnlyKeyFileAndPrintsOnlyTheKeyId() throws Exception {
		Path file = tempDir.resolve("document-key.json");

		assertThat(run("generate", file.toString())).isZero();

		DocumentCipher cipher = load(file);
		assertThat(output()).contains(Integer.toUnsignedString(cipher.primaryKeyId()))
			.doesNotContain("\"value\"")
			.doesNotContain(Files.readString(file).substring(0, 20));
		assertThat(KeyFilePermissions.isOwnerOnly(file)).isTrue();
	}

	@Test
	void generateNeverOverwritesAnExistingFile() throws Exception {
		Path file = tempDir.resolve("document-key.json");
		run("generate", file.toString());
		byte[] original = Files.readAllBytes(file);

		assertThat(run("generate", file.toString())).isEqualTo(1);

		assertThat(Files.readAllBytes(file)).isEqualTo(original);
		assertThat(output()).contains("nothing was changed");
	}

	@Test
	void rotateAddsAPrimaryKeyKeepsOldOnesAndKeepsABackup() throws Exception {
		Path file = tempDir.resolve("document-key.json");
		run("generate", file.toString());
		DocumentCipher before = load(file);
		byte[] oldDocument = encrypt(before, "fictional document from before the rotation");

		assertThat(run("rotate", file.toString())).isZero();

		DocumentCipher after = load(file);
		assertThat(after.primaryKeyId()).isNotEqualTo(before.primaryKeyId());
		assertThat(after.hasKey(before.primaryKeyId())).as("the old key is kept").isTrue();
		assertThat(decrypt(after, oldDocument)).isEqualTo("fictional document from before the rotation");
		byte[] newDocument = encrypt(after, "fictional document after the rotation");
		assertThat(DocumentCipher.keyIdOf(newDocument)).isEqualTo(after.primaryKeyId());

		try (var files = Files.list(tempDir)) {
			Path backup = files.filter(p -> p.getFileName().toString().startsWith("document-key.json.bak-"))
				.findFirst()
				.orElseThrow();
			assertThat(load(backup).primaryKeyId()).isEqualTo(before.primaryKeyId());
			assertThat(KeyFilePermissions.isOwnerOnly(backup)).isTrue();
		}
		assertThat(KeyFilePermissions.isOwnerOnly(file)).isTrue();
		assertThat(tempDir.resolve("document-key.json.new")).doesNotExist();
	}

	@Test
	void inspectListsKeyIdsWithoutKeyMaterial() throws Exception {
		Path file = tempDir.resolve("document-key.json");
		run("generate", file.toString());
		run("rotate", file.toString());
		out.reset();

		assertThat(run("inspect", file.toString())).isZero();

		assertThat(output()).contains("2 key(s)").contains("primary").doesNotContain("\"value\"").doesNotContain("keyData");
	}

	@Test
	void invalidUsageAndRelativePathsAreRefused() {
		assertThat(run()).isEqualTo(2);
		assertThat(run("generate", "relative/key.json")).isEqualTo(2);
		assertThat(run("destroy", tempDir.resolve("k.json").toString())).isEqualTo(2);
		assertThat(run("rotate", tempDir.resolve("missing.json").toString())).isEqualTo(1);
		assertThat(tempDir.resolve("missing.json")).doesNotExist();
	}

}
