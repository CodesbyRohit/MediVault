package com.medivault.document;

/**
 * The file system could not complete a storage operation. Messages are generic and never carry
 * a path, and the underlying {@code IOException} is deliberately not attached as the cause: its
 * message usually contains the absolute path (and therefore the Windows user name).
 */
public class DocumentStorageException extends RuntimeException {

	public DocumentStorageException(String message) {
		super(message);
	}

}
