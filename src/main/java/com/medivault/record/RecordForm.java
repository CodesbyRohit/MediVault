package com.medivault.record;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * Form object for creating and editing a medical record. It deliberately has no id, owner or
 * timestamp field, so request parameters can never set them; the controller also allow-lists the
 * bound fields. Text is normalised on binding: surrounding whitespace is removed, Windows line
 * breaks become {@code \n}, and blank optional fields become {@code null}.
 */
public class RecordForm {

	/** Records dated before this are refused as typing mistakes (for example year 0202). */
	public static final LocalDate EARLIEST_DATE = LocalDate.of(1900, 1, 1);

	public static final String TOO_EARLY_MESSAGE = "The record date must be on or after 1 January 1900.";

	@NotBlank(message = "Enter a title.")
	@Size(min = 3, max = 150, message = "Title must be between 3 and 150 characters.")
	private String title;

	@NotNull(message = "Choose a record type.")
	private RecordType recordType;

	@NotNull(message = "Enter the date of the record.")
	@PastOrPresent(message = "The record date cannot be in the future.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate recordDate;

	@Size(max = 150, message = "Provider must be 150 characters or fewer.")
	private String providerName;

	@Size(max = 1000, message = "Notes must be 1000 characters or fewer.")
	private String notes;

	/** The version of the record the form was based on; {@code null} for a new record. */
	private Long version;

	public static RecordForm from(MedicalRecord record) {
		RecordForm form = new RecordForm();
		form.title = record.getTitle();
		form.recordType = record.getRecordType();
		form.recordDate = record.getRecordDate();
		form.providerName = record.getProviderName();
		form.notes = record.getNotes();
		form.version = record.getVersion();
		return form;
	}

	public boolean isDateTooEarly() {
		return recordDate != null && recordDate.isBefore(EARLIEST_DATE);
	}

	private static String strip(String value) {
		return value == null ? null : value.strip();
	}

	private static String stripToNull(String value) {
		String stripped = strip(value);
		return stripped == null || stripped.isEmpty() ? null : stripped;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = strip(title);
	}

	public RecordType getRecordType() {
		return recordType;
	}

	public void setRecordType(RecordType recordType) {
		this.recordType = recordType;
	}

	public LocalDate getRecordDate() {
		return recordDate;
	}

	public void setRecordDate(LocalDate recordDate) {
		this.recordDate = recordDate;
	}

	public String getProviderName() {
		return providerName;
	}

	public void setProviderName(String providerName) {
		this.providerName = stripToNull(providerName);
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes == null ? null : stripToNull(notes.replace("\r\n", "\n").replace('\r', '\n'));
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

}
