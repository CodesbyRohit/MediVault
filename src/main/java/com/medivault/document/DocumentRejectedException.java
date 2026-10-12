package com.medivault.document;

/**
 * An upload that fails validation. Each reason carries a fixed message that is safe to show to the
 * user: it never repeats the filename, content or any path.
 */
public class DocumentRejectedException extends RuntimeException {

	public enum Reason {

		MISSING("Choose a PDF file to upload."),
		EMPTY("The selected file is empty. Choose a PDF document."),
		NOT_PDF_EXTENSION("Only PDF files (ending in .pdf) can be uploaded."),
		NOT_PDF_TYPE("Only PDF files can be uploaded."),
		NOT_PDF_CONTENT("The file is not a PDF document."),
		MALFORMED("The PDF file appears to be damaged or incomplete."),
		TOO_LARGE("The file is too large."),
		QUOTA_EXCEEDED("There is not enough document storage left for this file.");

		private final String message;

		Reason(String message) {
			this.message = message;
		}

		public String message() {
			return message;
		}

	}

	private final Reason reason;

	public DocumentRejectedException(Reason reason) {
		super(reason.message());
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

}
