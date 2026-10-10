package com.medivault.user;

/**
 * Account roles. Registration can only ever produce {@link #PATIENT}.
 */
public enum Role {

	PATIENT,
	ADMIN;

	/** Spring Security authority name, e.g. {@code ROLE_PATIENT}. */
	public String authority() {
		return "ROLE_" + name();
	}

}
