package com.medivault.document.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import com.google.crypto.tink.StreamingAead;

/**
 * The MediVault encrypted-document container, version 1:
 *
 * <pre>
 * offset 0   5 bytes  magic "MVDOC" (a plaintext PDF starts with "%PDF-", so formats never collide)
 * offset 5   1 byte   format version 0x01
 * offset 6   4 bytes  id of the Tink key that encrypted the file (big-endian)
 * offset 10  ...      Tink StreamingAead ciphertext (AES256_GCM_HKDF_1MB): its own header with a random
 *                     salt and nonce prefix, then 1 MiB segments, each authenticated with AES-GCM; the
 *                     segment number and a last-segment flag are part of each nonce
 * </pre>
 *
 * The associated data is the 10 container bytes plus {@code "|record_documents|"} plus the file's
 * storage key, so a file renamed to another key, a changed key id or version, and segments that are
 * reordered, duplicated, truncated or taken from another file all fail authentication.
 * <p>
 * Decrypted bytes are released only after the segment containing them has been authenticated, but
 * the absence of truncation is only known at end of stream. Callers must therefore treat a document
 * as valid only after reading to EOF without an exception (see {@code DocumentStorage#verify}).
 */
public final class DocumentCipher {

	static final byte[] MAGIC = "MVDOC".getBytes(StandardCharsets.US_ASCII);

	static final byte VERSION = 0x01;

	public static final int HEADER_LENGTH = MAGIC.length + 1 + Integer.BYTES;

	private static final byte[] AAD_LABEL = "|record_documents|".getBytes(StandardCharsets.US_ASCII);

	private final DocumentKeyRing keys;

	public DocumentCipher(DocumentKeyRing keys) {
		this.keys = keys;
	}

	public int primaryKeyId() {
		return keys.primaryKeyId();
	}

	public boolean hasKey(int keyId) {
		return keys.hasKey(keyId);
	}

	/** Whether {@code firstBytes} start with the encrypted container's magic. */
	public static boolean hasEncryptedHeader(byte[] firstBytes, int length) {
		return length >= MAGIC.length && Arrays.equals(firstBytes, 0, MAGIC.length, MAGIC, 0, MAGIC.length);
	}

	/**
	 * Writes the container header to {@code raw} and returns a stream that encrypts everything written
	 * to it with the primary key. Closing the returned stream finalises the ciphertext (writes the last
	 * segment) and closes {@code raw}.
	 */
	public OutputStream encrypt(OutputStream raw, String storageKey) throws IOException {
		byte[] header = header(keys.primaryKeyId());
		raw.write(header);
		try {
			return keys.primary().newEncryptingStream(raw, associatedData(header, storageKey));
		}
		catch (GeneralSecurityException ex) {
			throw new DocumentCryptoException(DocumentCryptoException.Reason.ENCRYPTION_FAILED);
		}
	}

	/**
	 * Reads and checks the container header from {@code raw} and returns a stream of authenticated
	 * plaintext. Reading fails with a {@link DocumentCryptoException} (an {@code IOException}) if the
	 * header is wrong, the key is not available, or any segment fails authentication; a missing final
	 * segment is reported when the end of the ciphertext is reached.
	 */
	public InputStream decrypt(InputStream raw, String storageKey) throws IOException {
		byte[] header = raw.readNBytes(HEADER_LENGTH);
		if (header.length < HEADER_LENGTH || !hasEncryptedHeader(header, header.length)) {
			throw new DocumentCryptoException(DocumentCryptoException.Reason.NOT_ENCRYPTED);
		}
		if (header[MAGIC.length] != VERSION) {
			throw new DocumentCryptoException(DocumentCryptoException.Reason.UNSUPPORTED_VERSION);
		}
		int keyId = ByteBuffer.wrap(header, MAGIC.length + 1, Integer.BYTES).getInt();
		StreamingAead key = keys.key(keyId);
		if (key == null) {
			throw new DocumentCryptoException(DocumentCryptoException.Reason.KEY_UNAVAILABLE);
		}
		try {
			return new AuthenticationFailureMapping(key.newDecryptingStream(raw, associatedData(header, storageKey)));
		}
		catch (GeneralSecurityException ex) {
			throw new DocumentCryptoException(DocumentCryptoException.Reason.AUTHENTICATION_FAILED);
		}
	}

	/** The key id recorded in a container header, for inventory and re-encryption. */
	public static int keyIdOf(byte[] header) {
		return ByteBuffer.wrap(header, MAGIC.length + 1, Integer.BYTES).getInt();
	}

	static byte[] header(int keyId) {
		return ByteBuffer.allocate(HEADER_LENGTH).put(MAGIC).put(VERSION).putInt(keyId).array();
	}

	static byte[] associatedData(byte[] header, String storageKey) {
		byte[] key = storageKey.getBytes(StandardCharsets.US_ASCII);
		return ByteBuffer.allocate(header.length + AAD_LABEL.length + key.length)
			.put(header)
			.put(AAD_LABEL)
			.put(key)
			.array();
	}

	/**
	 * Turns Tink's authentication failures into a {@link DocumentCryptoException} with a fixed message,
	 * so no library detail reaches logs or responses.
	 */
	private static final class AuthenticationFailureMapping extends java.io.FilterInputStream {

		AuthenticationFailureMapping(InputStream in) {
			super(in);
		}

		@Override
		public int read() throws IOException {
			try {
				return super.read();
			}
			catch (DocumentCryptoException ex) {
				throw ex;
			}
			catch (IOException | RuntimeException ex) {
				throw new DocumentCryptoException(DocumentCryptoException.Reason.AUTHENTICATION_FAILED);
			}
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			try {
				return super.read(b, off, len);
			}
			catch (DocumentCryptoException ex) {
				throw ex;
			}
			catch (IOException | RuntimeException ex) {
				throw new DocumentCryptoException(DocumentCryptoException.Reason.AUTHENTICATION_FAILED);
			}
		}

		@Override
		public long skip(long n) throws IOException {
			// Implemented by reading, so skipped segments are still authenticated and failures still surface.
			byte[] discard = new byte[8192];
			long skipped = 0;
			while (skipped < n) {
				int read = read(discard, 0, (int) Math.min(discard.length, n - skipped));
				if (read < 0) {
					break;
				}
				skipped += read;
			}
			return skipped;
		}

	}

}
