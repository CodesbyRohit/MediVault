package com.medivault.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.medivault.user.User;
import com.medivault.user.UserRepository;

/**
 * Database-backed login by email. Its presence also switches off Spring Boot's
 * generated in-memory default user.
 */
@Service
public class MediVaultUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public MediVaultUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		return userRepository.findByEmail(User.normalizeEmail(email))
			.map(MediVaultUserDetails::from)
			// Spring Security turns this into a generic "bad credentials" failure.
			.orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
	}

}
