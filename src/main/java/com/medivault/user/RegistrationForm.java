package com.medivault.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Form object for self-registration. It deliberately has no role, enabled or id field,
 * so request parameters can never set them.
 */
public class RegistrationForm {

	@NotNull(message = "Enter your full name.")
	@Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters.")
	private String fullName;

	@NotBlank(message = "Enter your email address.")
	@Email(message = "Enter a valid email address, like name@example.com.")
	@Size(max = 254, message = "Email address must be 254 characters or fewer.")
	private String email;

	@ValidPassword
	private String password;

	@NotEmpty(message = "Confirm your password.")
	private String confirmPassword;

	public boolean passwordsMatch() {
		return password != null && password.equals(confirmPassword);
	}

	/** Passwords are never echoed back to the browser when the form is re-displayed. */
	public void clearPasswords() {
		this.password = null;
		this.confirmPassword = null;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName == null ? null : fullName.strip();
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email == null ? null : email.strip();
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getConfirmPassword() {
		return confirmPassword;
	}

	public void setConfirmPassword(String confirmPassword) {
		this.confirmPassword = confirmPassword;
	}

}
