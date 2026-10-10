package com.medivault.user;

public class EmailAlreadyRegisteredException extends RuntimeException {

	public EmailAlreadyRegisteredException() {
		super("An account with this email already exists.");
	}

}
