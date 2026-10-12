package com.medivault.document;

/**
 * How a document's file is stored. The value in the database is authoritative: a file is read only
 * in the format its row names, so an encrypted document can never be "downgraded" by replacing its
 * file with a plaintext one.
 */
public enum StorageFormat {

	/** A plain PDF, written before encryption existed. Readable, and converted only on request. */
	PLAIN_V0,

	/** The encrypted container, version 1 ({@link com.medivault.document.crypto.DocumentCipher}). */
	ENCRYPTED_V1

}
