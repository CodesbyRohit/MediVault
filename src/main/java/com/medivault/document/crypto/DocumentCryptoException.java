package com.medivault.document.crypto;

import java.io.IOException;

/**
 * An encrypted document could not be decrypted or encrypted. The message is fixed text naming only
 * the reason: never a path, key id, key material or a library message. Extends {@code IOException}
 * so it surfaces naturally from stream reads.
 */
public class DocumentCryptoException extends IOException {

	public enum Reason {

		/** The file does not start with the encrypted container header. */
		NOT_ENCRYPTED,
		/** The container version is not one this MediVault version can read. */
		UNSUPPORTED_VERSION,
		/** The key that encrypted the file is not in the key file (or not enabled). */
		KEY_UNAVAILABLE,
		/** A segment, the header or the end of the ciphertext failed authentication. */
		AUTHENTICATION_FAILED,
		/** Tink refused to create an encrypting stream. */
		ENCRYPTION_FAILED

	}

	private final Reason reason;

	public DocumentCryptoException(Reason reason) {
		super("Encrypted document check failed (" + reason + ").");
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

}
