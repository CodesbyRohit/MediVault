package com.medivault.document;

/**
 * A stored document failed verification: it is missing, damaged, tampered with, in the wrong format,
 * or cannot be decrypted with the available keys. The document is never served. The message names
 * only the reason: never a path, storage key, key id or library message.
 */
public class DocumentIntegrityException extends DocumentStorageException {

	public enum Reason {

		/** The file's header does not match the format recorded for it (for example plaintext in place of ciphertext). */
		FORMAT_MISMATCH,
		/** The file was encrypted with a key that is not in the key file. */
		KEY_UNAVAILABLE,
		/** Authenticated decryption failed: tampering, truncation, or a file from somewhere else. */
		AUTHENTICATION_FAILED,
		/** The decrypted (or plain) content has another size than recorded. */
		SIZE_MISMATCH,
		/** The decrypted (or plain) content has another SHA-256 than recorded. */
		CHECKSUM_MISMATCH,
		/** The file could not be read to the end. */
		UNREADABLE

	}

	private final Reason reason;

	public DocumentIntegrityException(Reason reason) {
		super("A stored document failed its integrity check (" + reason + ").");
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

}
