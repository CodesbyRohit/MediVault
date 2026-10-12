package com.medivault.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.medivault.TestLinks;
import com.medivault.document.DocumentRejectedException.Reason;
import com.medivault.document.DocumentStorage.StoredFile;
import com.medivault.document.crypto.DocumentCipher;
import com.medivault.document.crypto.TestKeys;

/**
 * File-level rules of {@link DocumentStorage}, against a JUnit temporary folder only. Files are stored
 * encrypted, so content is always read back through {@link DocumentStorage#openVerified}, and the raw
 * bytes are checked to be ciphertext.
 */
class DocumentStorageTests {

	private static final DocumentCipher CIPHER = TestKeys.newCipher();

	private static final Pattern UUID_V4 = Pattern
		.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

	private static final long MAX = 64 * 1024;

	@TempDir
	Path tempDir;

	private Path directory() {
		return tempDir.resolve("records");
	}

	private DocumentStorage storage() {
		return new DocumentStorage(directory(), MAX, UUID::randomUUID, CIPHER);
	}

	private static InputStream stream(byte[] bytes) {
		return new ByteArrayInputStream(bytes);
	}

	private List<Path> files() throws IOException {
		try (Stream<Path> files = Files.list(directory())) {
			return files.toList();
		}
	}

	private static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	/** The document content, read the way downloads read it (decrypted and verified). */
	private static byte[] content(DocumentStorage storage, String key, byte[] expected) throws Exception {
		try (InputStream in = storage.openVerified(StorageFormat.ENCRYPTED_V1, key, expected.length, sha256(expected))) {
			return in.readAllBytes();
		}
	}

	/** The raw stored bytes. */
	private static byte[] raw(DocumentStorage storage, String key) throws IOException {
		try (InputStream in = storage.open(key)) {
			return in.readAllBytes();
		}
	}

	@Test
	void storesAValidPdfUnderAnOpaqueKeyWithSizeAndChecksum() throws Exception {
		DocumentStorage storage = storage();
		byte[] pdf = FictionalPdf.bytes("Complete blood count");

		StoredFile stored = storage.store(stream(pdf));

		assertThat(stored.key()).matches(UUID_V4);
		assertThat(stored.size()).isEqualTo(pdf.length);
		assertThat(stored.sha256()).isEqualTo(sha256(pdf)).hasSize(64);
		assertThat(stored.keyId()).isEqualTo(CIPHER.primaryKeyId());
		assertThat(files()).extracting(file -> file.getFileName().toString()).containsExactly(stored.key());
		assertThat(content(storage, stored.key(), pdf)).isEqualTo(pdf);
		assertThat(new String(raw(storage, stored.key()), StandardCharsets.ISO_8859_1)).as("stored encrypted")
			.startsWith("MVDOC")
			.doesNotContain("%PDF")
			.doesNotContain("Complete blood count");
	}

	@Test
	void storageKeysAreRandomAndUnrelatedToContent() {
		DocumentStorage storage = storage();
		byte[] pdf = FictionalPdf.bytes("Same content every time");
		Set<String> keys = new HashSet<>();
		for (int i = 0; i < 25; i++) {
			keys.add(storage.store(stream(pdf)).key());
		}
		assertThat(keys).hasSize(25).allMatch(key -> UUID_V4.matcher(key).matches());
	}

	@Test
	void theFolderIsCreatedWhenMissing() {
		Path nested = tempDir.resolve("a").resolve("b").resolve("records");
		new DocumentStorage(nested, MAX, UUID::randomUUID, CIPHER);
		assertThat(nested).isDirectory();
	}

	/**
	 * Regression (M4 audit): the storage folder itself must not be a symbolic link or junction in any
	 * profile (the local-file guard already checked this; local-h2 and mysql did not).
	 */
	@Test
	void aLinkedStorageFolderIsRefusedWithoutRevealingThePath() throws Exception {
		Path outside = Files.createDirectories(tempDir.resolve("outside"));
		assumeTrue(TestLinks.createDirectoryLink(directory(), outside), "no symbolic link or junction could be created");

		assertThatThrownBy(this::storage).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("not a plain directory")
			.message()
			.doesNotContain(tempDir.toString());
		try (Stream<Path> files = Files.list(outside)) {
			assertThat(files.toList()).as("nothing is written through the link").isEmpty();
		}
	}

	@Test
	void aRelativeFolderIsRefused() {
		assertThatThrownBy(() -> new DocumentStorage(Path.of("records"), MAX, UUID::randomUUID, CIPHER))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("absolute");
	}

	@Test
	void aFileInPlaceOfTheFolderIsRefusedWithoutRevealingThePath() throws Exception {
		Files.writeString(directory(), "not a folder");
		assertThatThrownBy(this::storage).isInstanceOf(IllegalStateException.class)
			.message()
			.doesNotContain(tempDir.toString());
	}

	@Test
	void emptyContentIsRejected() throws Exception {
		assertThatThrownBy(() -> storage().store(stream(new byte[0])))
			.isInstanceOfSatisfying(DocumentRejectedException.class, ex -> assertThat(ex.reason()).isEqualTo(Reason.EMPTY));
		assertThat(files()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "MZ\u0090\u0000 fictional executable %%EOF", "<html><script>alert(1)</script></html>%%EOF",
			"PK\u0003\u0004 fictional zip %%EOF", "%PDX-1.4 %%EOF", " %PDF-1.4 leading space %%EOF", "%PDF-", "%PDF-9.9 %%EOF",
			"plain text" })
	void contentThatIsNotAPdfIsRejected(String content) throws Exception {
		DocumentStorage storage = storage();
		assertThatThrownBy(() -> storage.store(stream(content.getBytes(StandardCharsets.ISO_8859_1))))
			.isInstanceOfSatisfying(DocumentRejectedException.class,
					ex -> assertThat(ex.reason()).isEqualTo(Reason.NOT_PDF_CONTENT));
		assertThat(files()).as("no stored or partial file may remain").isEmpty();
	}

	@Test
	void aTruncatedPdfWithoutEndMarkerIsRejectedAsMalformed() throws Exception {
		byte[] pdf = FictionalPdf.bytes("Truncated");
		byte[] truncated = java.util.Arrays.copyOf(pdf, pdf.length - 8);
		assertThatThrownBy(() -> storage().store(stream(truncated)))
			.isInstanceOfSatisfying(DocumentRejectedException.class, ex -> assertThat(ex.reason()).isEqualTo(Reason.MALFORMED));
		assertThat(files()).isEmpty();
	}

	@Test
	void theEndMarkerMustBeInTheLastKilobyte() throws Exception {
		byte[] pdf = FictionalPdf.bytes("Marker too early");
		byte[] padded = new byte[pdf.length + 2048];
		System.arraycopy(pdf, 0, padded, 0, pdf.length);
		java.util.Arrays.fill(padded, pdf.length, padded.length, (byte) 'x');
		assertThatThrownBy(() -> storage().store(stream(padded)))
			.isInstanceOfSatisfying(DocumentRejectedException.class, ex -> assertThat(ex.reason()).isEqualTo(Reason.MALFORMED));
	}

	@Test
	void contentOverTheLimitIsRejectedWhileStreaming() throws Exception {
		DocumentStorage storage = storage();
		assertThat(storage.store(stream(FictionalPdf.ofSize((int) MAX))).size()).isEqualTo(MAX);

		assertThatThrownBy(() -> storage.store(stream(FictionalPdf.ofSize((int) MAX + 1))))
			.isInstanceOfSatisfying(DocumentRejectedException.class, ex -> assertThat(ex.reason()).isEqualTo(Reason.TOO_LARGE));
		assertThat(files()).as("only the accepted file remains").hasSize(1);
	}

	@Test
	void anExistingStoredFileIsNeverOverwritten() throws Exception {
		UUID fixed = UUID.randomUUID();
		DocumentStorage storage = new DocumentStorage(directory(), MAX, () -> fixed, CIPHER);
		byte[] first = FictionalPdf.bytes("First upload");
		String key = storage.store(stream(first)).key();

		assertThatThrownBy(() -> storage.store(stream(FictionalPdf.bytes("Second upload, same key"))))
			.isInstanceOf(DocumentStorageException.class)
			.hasMessageContaining("nothing was overwritten");

		assertThat(content(storage, key, first)).isEqualTo(first);
		assertThat(files()).as("no partial file may remain").hasSize(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "..", "../outside", "..\\outside", "C:\\Windows\\win.ini", "/etc/passwd",
			"records/../../x", "8e3c2a4f-1b2d-4c5e-9f60-7a8b9c0d1e2f/../x", "8E3C2A4F-1B2D-4C5E-9F60-7A8B9C0D1E2F",
			"8e3c2a4f-1b2d-4c5e-9f60-7a8b9c0d1e2f.pdf", "8e3c2a4f-1b2d-4c5e-9f60-7a8b9c0d1e2", "%2e%2e%2fsecret",
			"upload-123.part", "8e3c2a4f-1b2d-1c5e-9f60-7a8b9c0d1e2f" })
	void storageKeysThatAreNotPlainUuidsAreRefused(String key) {
		DocumentStorage storage = storage();
		assertThatThrownBy(() -> storage.open(key)).isInstanceOf(DocumentStorageException.class);
		assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(DocumentStorageException.class);
		assertThatThrownBy(() -> storage.exists(key)).isInstanceOf(DocumentStorageException.class);
		assertThatThrownBy(() -> storage.open(null)).isInstanceOf(DocumentStorageException.class);
	}

	@Test
	void filesOutsideTheFolderAreUnreachable() throws Exception {
		Path outside = Files.writeString(tempDir.resolve("outside.pdf"), "secret");
		DocumentStorage storage = storage();
		assertThatThrownBy(() -> storage.open("../outside.pdf")).isInstanceOf(DocumentStorageException.class);
		assertThatThrownBy(() -> storage.delete("../outside.pdf")).isInstanceOf(DocumentStorageException.class);
		assertThat(outside).exists().hasContent("secret");
	}

	@Test
	void aFileLinkNamedLikeAStorageKeyIsNeverFollowed() throws Exception {
		DocumentStorage storage = storage();
		Path outside = Files.writeString(tempDir.resolve("outside.txt"), "secret");
		String key = UUID.randomUUID().toString();
		try {
			Files.createSymbolicLink(directory().resolve(key), outside);
		}
		catch (IOException | UnsupportedOperationException | SecurityException ex) {
			// Windows only allows file symbolic links with Developer Mode or elevation.
			assumeTrue(false, "file symbolic links cannot be created here: " + ex.getClass().getSimpleName());
		}
		assertThatThrownBy(() -> storage.open(key)).isInstanceOf(DocumentStorageException.class);
		assertThat(storage.exists(key)).isFalse();
	}

	@Test
	void aDirectoryLinkNamedLikeAStorageKeyIsNeverFollowed() throws Exception {
		DocumentStorage storage = storage();
		Path outside = Files.createDirectories(tempDir.resolve("outside"));
		Files.writeString(outside.resolve("secret.txt"), "secret");
		String key = UUID.randomUUID().toString();
		assumeTrue(TestLinks.createDirectoryLink(directory().resolve(key), outside),
				"no symbolic link or junction could be created");

		assertThatThrownBy(() -> storage.open(key)).isInstanceOf(DocumentStorageException.class);
		assertThat(storage.exists(key)).isFalse();
		assertThat(outside.resolve("secret.txt")).exists();
	}

	@Test
	void deletingOneDocumentNeverAffectsAnother() throws Exception {
		DocumentStorage storage = storage();
		byte[] keep = FictionalPdf.bytes("Keep me");
		String first = storage.store(stream(FictionalPdf.bytes("Delete me"))).key();
		String second = storage.store(stream(keep)).key();

		assertThat(storage.delete(first)).isTrue();
		assertThat(storage.delete(first)).as("already gone").isFalse();

		assertThat(storage.exists(first)).isFalse();
		assertThat(storage.exists(second)).isTrue();
		assertThat(content(storage, second, keep)).isEqualTo(keep);
	}

	@Test
	void aMissingFileProducesASafeError() {
		DocumentStorage storage = storage();
		assertThatThrownBy(() -> storage.open(UUID.randomUUID().toString()))
			.isInstanceOf(DocumentStorageException.class)
			.hasNoCause()
			.message()
			.doesNotContain(tempDir.toString())
			.doesNotContain("records");
	}

	@Test
	void leftoverPartialUploadsAreNeverDeletedAutomatically() throws Exception {
		Files.createDirectories(directory());
		Path leftover = Files.writeString(directory().resolve("upload-1234" + DocumentStorage.PART_SUFFIX), "partial");
		DocumentStorage storage = storage();
		storage.store(stream(FictionalPdf.bytes("New upload")));
		assertThat(leftover).exists();
	}

}
