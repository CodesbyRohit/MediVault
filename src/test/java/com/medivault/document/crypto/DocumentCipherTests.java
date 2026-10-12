package com.medivault.document.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.medivault.document.FictionalPdf;
import com.medivault.document.crypto.DocumentCryptoException.Reason;

/**
 * The encrypted container: round trips, and that every kind of tampering is detected without ever
 * releasing unauthenticated plaintext. Segment offsets follow Tink's AES256_GCM_HKDF_1MB layout, which
 * {@link #theCiphertextLayoutMatchesTinksDocumentedFormat} checks first.
 */
class DocumentCipherTests {

	private static final int SEGMENT = 1024 * 1024;

	/** Tink header: 1 length byte + 32-byte salt + 7-byte nonce prefix. */
	private static final int TINK_HEADER = 1 + 32 + 7;

	private static final int TAG = 16;

	private static final int FIRST_SEGMENT_PLAINTEXT = SEGMENT - TINK_HEADER - TAG;

	private static final int SEGMENT_PLAINTEXT = SEGMENT - TAG;

	private final DocumentCipher cipher = TestKeys.newCipher();

	private final String storageKey = UUID.randomUUID().toString();

	private static byte[] plaintext(int size) {
		byte[] data = new byte[size];
		new Random(42).nextBytes(data);
		return data;
	}

	private byte[] encrypt(byte[] plaintext) throws IOException {
		return encrypt(cipher, storageKey, plaintext);
	}

	private static byte[] encrypt(DocumentCipher cipher, String storageKey, byte[] plaintext) throws IOException {
		ByteArrayOutputStream raw = new ByteArrayOutputStream();
		try (OutputStream out = cipher.encrypt(raw, storageKey)) {
			out.write(plaintext);
		}
		return raw.toByteArray();
	}

	/** Reads everything it can; returns what was released before a failure, plus the failure. */
	private record Outcome(byte[] released, Exception failure) {
	}

	private Outcome decrypt(byte[] ciphertext) {
		return decrypt(cipher, storageKey, ciphertext);
	}

	private static Outcome decrypt(DocumentCipher cipher, String storageKey, byte[] ciphertext) {
		ByteArrayOutputStream released = new ByteArrayOutputStream();
		try (InputStream in = cipher.decrypt(new ByteArrayInputStream(ciphertext), storageKey)) {
			byte[] buffer = new byte[64 * 1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				released.write(buffer, 0, read);
			}
			return new Outcome(released.toByteArray(), null);
		}
		catch (IOException ex) {
			return new Outcome(released.toByteArray(), ex);
		}
	}

	/** The failure must be a fixed-message crypto error, and released bytes an authentic prefix only. */
	private static void assertRejected(Outcome outcome, byte[] original, int maxAuthenticPrefix) {
		assertThat(outcome.failure()).isInstanceOf(DocumentCryptoException.class);
		assertThat(outcome.failure().getMessage()).startsWith("Encrypted document check failed (")
			.doesNotContainIgnoringCase("tag");
		assertThat(outcome.released().length).isLessThanOrEqualTo(maxAuthenticPrefix);
		assertThat(outcome.released()).isEqualTo(Arrays.copyOf(original, outcome.released().length));
	}

	private static int segmentStart(int index) {
		return DocumentCipher.HEADER_LENGTH + (index == 0 ? TINK_HEADER : SEGMENT * index);
	}

	@Test
	void theCiphertextLayoutMatchesTinksDocumentedFormat() throws IOException {
		int size = FIRST_SEGMENT_PLAINTEXT + 2 * SEGMENT_PLAINTEXT + 1000;
		byte[] ciphertext = encrypt(plaintext(size));

		// 10-byte container header, 40-byte Tink header, 3 full segments of 1 MiB, then a last segment of 1000 + tag.
		assertThat(ciphertext).hasSize(DocumentCipher.HEADER_LENGTH + 3 * SEGMENT + 1000 + TAG);
		assertThat(new String(ciphertext, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("MVDOC");
		assertThat(ciphertext[5]).isEqualTo((byte) 1);
		assertThat(DocumentCipher.keyIdOf(ciphertext)).isEqualTo(cipher.primaryKeyId());
		assertThat(ciphertext[DocumentCipher.HEADER_LENGTH]).as("Tink header length byte").isEqualTo((byte) TINK_HEADER);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 1000, FIRST_SEGMENT_PLAINTEXT, FIRST_SEGMENT_PLAINTEXT + 1,
			FIRST_SEGMENT_PLAINTEXT + SEGMENT_PLAINTEXT })
	void plaintextRoundTripsAtSegmentBoundaries(int size) throws IOException {
		byte[] original = plaintext(size);
		Outcome outcome = decrypt(encrypt(original));
		assertThat(outcome.failure()).isNull();
		assertThat(outcome.released()).isEqualTo(original);
	}

	@Test
	void storedBytesRevealNeitherThePdfMarkerNorItsText() throws IOException {
		byte[] pdf = FictionalPdf.bytes("Fictional lipid profile ZZTOPSECRET");
		String raw = new String(encrypt(pdf), StandardCharsets.ISO_8859_1);
		assertThat(raw).doesNotContain("%PDF").doesNotContain("ZZTOPSECRET").doesNotContain("FICTIONAL");
	}

	@Test
	void encryptingTheSameContentTwiceGivesDifferentCiphertext() throws IOException {
		byte[] pdf = FictionalPdf.bytes("Same content");
		assertThat(encrypt(pdf)).isNotEqualTo(encrypt(pdf));
	}

	@Test
	void aFileRenamedToAnotherStorageKeyFailsAuthentication() throws IOException {
		byte[] original = plaintext(5000);
		Outcome outcome = decrypt(cipher, UUID.randomUUID().toString(), encrypt(original));
		assertRejected(outcome, original, 0);
	}

	@Test
	void aDifferentKeyCannotDecrypt() throws IOException {
		byte[] original = plaintext(5000);
		byte[] ciphertext = encrypt(original);

		Outcome otherKey = decrypt(TestKeys.newCipher(), storageKey, ciphertext);
		assertThat(otherKey.failure()).isInstanceOfSatisfying(DocumentCryptoException.class,
				ex -> assertThat(ex.reason()).isEqualTo(Reason.KEY_UNAVAILABLE));
		assertThat(otherKey.released()).isEmpty();
	}

	@Test
	void aKeyWithTheSameIdButOtherMaterialFailsAuthentication() throws IOException {
		byte[] original = plaintext(5000);
		byte[] ciphertext = encrypt(original);
		DocumentCipher impostor = new DocumentCipher(TestKeyRings.withFixedId(cipher.primaryKeyId()));

		assertRejected(decrypt(impostor, storageKey, ciphertext), original, 0);
	}

	@Test
	void plaintextInPlaceOfCiphertextIsRefused() {
		byte[] pdf = FictionalPdf.bytes("Substituted plaintext");
		Outcome outcome = decrypt(pdf);
		assertThat(outcome.failure()).isInstanceOfSatisfying(DocumentCryptoException.class,
				ex -> assertThat(ex.reason()).isEqualTo(Reason.NOT_ENCRYPTED));
		assertThat(outcome.released()).isEmpty();
	}

	@Test
	void anUnknownContainerVersionIsRefused() throws IOException {
		byte[] ciphertext = encrypt(plaintext(100));
		ciphertext[5] = 2;
		Outcome outcome = decrypt(ciphertext);
		assertThat(outcome.failure()).isInstanceOfSatisfying(DocumentCryptoException.class,
				ex -> assertThat(ex.reason()).isEqualTo(Reason.UNSUPPORTED_VERSION));
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 4, 9 })
	void containerHeaderTamperingIsDetected(int offset) throws IOException {
		byte[] original = plaintext(5000);
		byte[] ciphertext = encrypt(original);
		ciphertext[offset] ^= 0x01;
		assertRejected(decrypt(ciphertext), original, 0);
	}

	@Test
	void aKeyIdChangedToAnotherKnownKeyFailsAuthentication() throws IOException {
		// Both keys are in the ring, so only the associated data (which includes the header) catches the swap.
		DocumentKeyRing ring = TestKeyRings.withTwoKeys();
		DocumentCipher both = new DocumentCipher(ring);
		byte[] original = plaintext(5000);
		byte[] ciphertext = encrypt(both, storageKey, original);
		int other = TestKeyRings.otherKeyId(ring);
		java.nio.ByteBuffer.wrap(ciphertext, 6, 4).putInt(other);

		assertRejected(decrypt(both, storageKey, ciphertext), original, 0);
	}

	@ParameterizedTest
	@ValueSource(ints = { 10, 11, 30, 49 })
	void tinkHeaderTamperingIsDetected(int offset) throws IOException {
		byte[] original = plaintext(5000);
		byte[] ciphertext = encrypt(original);
		ciphertext[offset] ^= 0x40;
		assertRejected(decrypt(ciphertext), original, 0);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3 })
	void aFlippedByteInAnySegmentStopsTheStreamAtThatSegment(int segment) throws IOException {
		int size = FIRST_SEGMENT_PLAINTEXT + 2 * SEGMENT_PLAINTEXT + 1000;
		byte[] original = plaintext(size);
		byte[] ciphertext = encrypt(original);
		ciphertext[segmentStart(segment) + 7] ^= 0x01;

		int authentic = segment == 0 ? 0 : FIRST_SEGMENT_PLAINTEXT + (segment - 1) * SEGMENT_PLAINTEXT;
		assertRejected(decrypt(ciphertext), original, authentic);
	}

	@Test
	void aFlippedAuthenticationTagIsDetected() throws IOException {
		byte[] original = plaintext(5000);
		byte[] ciphertext = encrypt(original);
		ciphertext[ciphertext.length - 1] ^= 0x01;
		assertRejected(decrypt(ciphertext), original, 0);
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, TAG, 500 })
	void truncationInsideTheLastSegmentIsDetected(int removed) throws IOException {
		byte[] original = plaintext(5000);
		byte[] ciphertext = encrypt(original);
		assertRejected(decrypt(Arrays.copyOf(ciphertext, ciphertext.length - removed)), original, 0);
	}

	@Test
	void truncationAtASegmentBoundaryIsDetected() throws IOException {
		// Dropping the whole last segment leaves a well-formed prefix whose last segment lacks the final flag.
		int size = FIRST_SEGMENT_PLAINTEXT + SEGMENT_PLAINTEXT + 1000;
		byte[] original = plaintext(size);
		byte[] ciphertext = encrypt(original);
		byte[] cut = Arrays.copyOf(ciphertext, segmentStart(2));

		assertRejected(decrypt(cut), original, FIRST_SEGMENT_PLAINTEXT + SEGMENT_PLAINTEXT);
		assertThat(decrypt(cut).failure()).isNotNull();
	}

	@Test
	void aFileCutToItsHeadersOnlyIsDetected() throws IOException {
		byte[] original = plaintext(5000);
		byte[] ciphertext = encrypt(original);
		assertRejected(decrypt(Arrays.copyOf(ciphertext, DocumentCipher.HEADER_LENGTH + TINK_HEADER)), original, 0);
		assertRejected(decrypt(Arrays.copyOf(ciphertext, DocumentCipher.HEADER_LENGTH)), original, 0);
		assertRejected(decrypt(Arrays.copyOf(ciphertext, 3)), original, 0);
	}

	@Test
	void reorderedSegmentsAreDetected() throws IOException {
		int size = FIRST_SEGMENT_PLAINTEXT + 3 * SEGMENT_PLAINTEXT + 1000;
		byte[] original = plaintext(size);
		byte[] ciphertext = encrypt(original);
		byte[] swapped = ciphertext.clone();
		System.arraycopy(ciphertext, segmentStart(1), swapped, segmentStart(2), SEGMENT);
		System.arraycopy(ciphertext, segmentStart(2), swapped, segmentStart(1), SEGMENT);

		assertRejected(decrypt(swapped), original, FIRST_SEGMENT_PLAINTEXT);
	}

	@Test
	void aDuplicatedSegmentIsDetected() throws IOException {
		int size = FIRST_SEGMENT_PLAINTEXT + 2 * SEGMENT_PLAINTEXT + 1000;
		byte[] original = plaintext(size);
		byte[] ciphertext = encrypt(original);
		byte[] duplicated = ciphertext.clone();
		System.arraycopy(ciphertext, segmentStart(1), duplicated, segmentStart(2), SEGMENT);

		assertRejected(decrypt(duplicated), original, FIRST_SEGMENT_PLAINTEXT + SEGMENT_PLAINTEXT);
	}

	@Test
	void aSegmentFromAnotherFileIsDetected() throws IOException {
		int size = FIRST_SEGMENT_PLAINTEXT + 2 * SEGMENT_PLAINTEXT + 1000;
		byte[] original = plaintext(size);
		byte[] ciphertext = encrypt(original);
		byte[] other = encrypt(plaintext(size));
		System.arraycopy(other, segmentStart(1), ciphertext, segmentStart(1), SEGMENT);

		assertRejected(decrypt(ciphertext), original, FIRST_SEGMENT_PLAINTEXT);
	}

	@Test
	void appendedBytesAreDetected() throws IOException {
		byte[] original = plaintext(5000);
		byte[] ciphertext = encrypt(original);
		byte[] extended = Arrays.copyOf(ciphertext, ciphertext.length + 32);
		assertRejected(decrypt(extended), original, 0);
	}

	@Test
	void anUnfinishedEncryptionIsNotAValidFile() throws IOException {
		// Simulates a crash: the encrypting stream is flushed but never closed, so no final segment is written.
		byte[] original = plaintext(FIRST_SEGMENT_PLAINTEXT + 5000);
		ByteArrayOutputStream raw = new ByteArrayOutputStream();
		OutputStream out = cipher.encrypt(raw, storageKey);
		out.write(original);
		out.flush();

		assertRejected(decrypt(raw.toByteArray()), original, FIRST_SEGMENT_PLAINTEXT);
	}

}
