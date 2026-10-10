package com.medivault.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.medivault.user.Role;
import com.medivault.user.User;

/**
 * The authenticated principal kept in the session. It carries the account id so that
 * ownership checks never depend on request data. The password hash is erased after login.
 */
public class MediVaultUserDetails implements UserDetails, CredentialsContainer {

	private static final long serialVersionUID = 1L;

	private final Long id;

	private final String fullName;

	private final String email;

	private final Role role;

	private final boolean enabled;

	private String passwordHash;

	public MediVaultUserDetails(Long id, String fullName, String email, String passwordHash, Role role,
			boolean enabled) {
		this.id = id;
		this.fullName = fullName;
		this.email = email;
		this.passwordHash = passwordHash;
		this.role = role;
		this.enabled = enabled;
	}

	public static MediVaultUserDetails from(User user) {
		return new MediVaultUserDetails(user.getId(), user.getFullName(), user.getEmail(), user.getPasswordHash(),
				user.getRole(), user.isEnabled());
	}

	public Long getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public Role getRole() {
		return role;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority(role.authority()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void eraseCredentials() {
		this.passwordHash = null;
	}

	@Override
	public String toString() {
		return "MediVaultUserDetails[id=" + id + ", role=" + role + "]";
	}

}
