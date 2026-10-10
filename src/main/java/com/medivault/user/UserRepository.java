package com.medivault.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	/** Expects an already-normalised email (see {@link User#normalizeEmail(String)}). */
	Optional<User> findByEmail(String email);

	/** Expects an already-normalised email (see {@link User#normalizeEmail(String)}). */
	boolean existsByEmail(String email);

	List<User> findAllByOrderByCreatedAtAscIdAsc();

}
