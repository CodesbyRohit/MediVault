package com.medivault.admin;

import java.time.LocalDate;
import java.time.ZoneId;

import com.medivault.user.Role;
import com.medivault.user.User;

/**
 * What an administrator may see about an account: no password hash and no medical data.
 */
public record UserSummary(Long id, String fullName, String email, Role role, boolean enabled, LocalDate createdOn) {

	static UserSummary from(User user) {
		return new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.isEnabled(),
				LocalDate.ofInstant(user.getCreatedAt(), ZoneId.systemDefault()));
	}

}
