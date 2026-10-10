package com.medivault.record;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown both when a record doesn't exist and when it belongs to someone else, so the
 * response never reveals that another patient's record exists.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RecordNotFoundException extends RuntimeException {

	public RecordNotFoundException() {
		super("Record not found");
	}

}
