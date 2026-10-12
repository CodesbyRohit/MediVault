package com.medivault.user;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	/** Expects an already-normalised email (see {@link User#normalizeEmail(String)}). */
	Optional<User> findByEmail(String email);

	/** Expects an already-normalised email (see {@link User#normalizeEmail(String)}). */
	boolean existsByEmail(String email);

	List<User> findAllByOrderByCreatedAtAscIdAsc();

	/**
	 * Locks the account row until the transaction ends, so per-account checks (such as the document
	 * storage quota) cannot be bypassed by concurrent requests.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> lockById(@Param("id") Long id);

}
