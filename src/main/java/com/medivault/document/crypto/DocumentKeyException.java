package com.medivault.document.crypto;

/**
 * The document key file is missing or unusable. Every message is fixed text: it never contains the
 * key file's path (which would reveal the Windows user name) or any key material. Startup is refused.
 */
public class DocumentKeyException extends IllegalStateException {

	public enum Problem {

		NOT_CONFIGURED("MEDIVAULT_DOCUMENT_KEY_FILE is not set. MediVault encrypts every stored PDF and needs a "
				+ "document key file (see README, \"Document encryption key\")."),
		NOT_ABSOLUTE("MEDIVAULT_DOCUMENT_KEY_FILE must be an absolute path."),
		NOT_FOUND("the document key file does not exist or cannot be read."),
		NOT_REGULAR_FILE("the document key file is a link, junction, folder or other special file; only a "
				+ "regular file is accepted."),
		TOO_LARGE("the document key file is larger than 64 KiB, so it cannot be a MediVault document keyset."),
		INSIDE_STORAGE("the document key file is inside the MediVault storage folder. Keep it elsewhere, so a "
				+ "copy of the data never includes the key."),
		TOO_PERMISSIVE("the document key file can be read by other users. Restrict it to your own account "
				+ "(see README, \"Document encryption key\")."),
		INVALID_KEYSET("the document key file is not a Tink JSON keyset. A raw AES key or any other format is "
				+ "not accepted; create the file with the document-key tool."),
		UNSUPPORTED_KEY("the document key file contains a key that is not an AES256_GCM_HKDF_1MB streaming key."),
		NO_ENABLED_PRIMARY("the document key file has no enabled primary key."),
		SELF_TEST_FAILED("a key in the document key file failed its encryption self-test.");

		private final String detail;

		Problem(String detail) {
			this.detail = detail;
		}

		public String detail() {
			return detail;
		}

	}

	private final Problem problem;

	public DocumentKeyException(Problem problem) {
		super("Refusing to start: " + problem.detail());
		this.problem = problem;
	}

	public Problem problem() {
		return problem;
	}

}
