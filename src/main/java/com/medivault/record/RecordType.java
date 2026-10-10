package com.medivault.record;

public enum RecordType {

	LAB_REPORT("Lab report"),
	PRESCRIPTION("Prescription"),
	IMAGING("Imaging"),
	DISCHARGE_SUMMARY("Discharge summary"),
	VACCINATION("Vaccination"),
	OTHER("Other");

	private final String displayName;

	RecordType(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

}
