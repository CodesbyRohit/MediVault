package com.medivault.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.medivault.user.Role;

/**
 * Where each kind of user "lives" in the application.
 */
public final class RoleHome {

	private RoleHome() {
	}

	public static boolean isLoggedIn(Authentication authentication) {
		return authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken);
	}

	/** {@code /admin} for administrators, {@code /dashboard} for patients, {@code /} otherwise. */
	public static String pathFor(Authentication authentication) {
		if (!isLoggedIn(authentication)) {
			return "/";
		}
		boolean admin = authentication.getAuthorities()
			.stream()
			.anyMatch(a -> Role.ADMIN.authority().equals(a.getAuthority()));
		return admin ? "/admin" : "/dashboard";
	}

}
