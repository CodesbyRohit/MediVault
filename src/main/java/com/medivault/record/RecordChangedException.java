package com.medivault.record;

/**
 * An edit was based on an older version of the record (it was changed in another tab or window in
 * the meantime). Nothing is saved; the patient is asked to review the current version.
 */
public class RecordChangedException extends RuntimeException {

	public static final String MESSAGE = "This record was changed in another window since you opened it. "
			+ "Your edits were not saved. Review the current version below and make your changes again.";

	public RecordChangedException() {
		super("Record was changed concurrently");
	}

}
