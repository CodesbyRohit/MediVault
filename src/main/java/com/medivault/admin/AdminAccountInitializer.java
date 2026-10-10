package com.medivault.admin;

import java.util.Set;
import java.util.TreeSet;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.medivault.user.Role;
import com.medivault.user.User;
import com.medivault.user.UserRepository;
import com.medivault.user.ValidPassword;

/**
 * Optional admin bootstrap. Creates one ADMIN account at startup only when BOTH
 * {@code MEDIVAULT_ADMIN_EMAIL} and {@code MEDIVAULT_ADMIN_PASSWORD} are set and valid.
 * <ul>
 * <li>Stores only the BCrypt hash.</li>
 * <li>Never changes an existing account (no password or role overwrite on restart).</li>
 * <li>Never logs the email, password or hash, only the problem or the new account id.</li>
 * </ul>
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

	static final String ADMIN_FULL_NAME = "MediVault Administrator";

	public enum Result {

		NOT_CONFIGURED, INCOMPLETE, INVALID, ALREADY_EXISTS, CREATED

	}

	/** Validated view of the configuration; record components carry the same rules as registration. */
	record AdminCredentials(@NotBlank @Email @Size(max = 254) String email, @ValidPassword String password) {
	}

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final Validator validator;

	private final String configuredEmail;

	private final String configuredPassword;

	public AdminAccountInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder, Validator validator,
			@Value("${medivault.admin.email:}") String configuredEmail,
			@Value("${medivault.admin.password:}") String configuredPassword) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.validator = validator;
		this.configuredEmail = configuredEmail;
		this.configuredPassword = configuredPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		bootstrap(configuredEmail, configuredPassword);
	}

	public Result bootstrap(String rawEmail, String rawPassword) {
		boolean hasEmail = StringUtils.hasText(rawEmail);
		boolean hasPassword = StringUtils.hasText(rawPassword);
		if (!hasEmail && !hasPassword) {
			log.debug("Admin bootstrap not configured.");
			return Result.NOT_CONFIGURED;
		}
		if (!hasEmail || !hasPassword) {
			log.warn("Admin bootstrap skipped: both MEDIVAULT_ADMIN_EMAIL and MEDIVAULT_ADMIN_PASSWORD must be set.");
			return Result.INCOMPLETE;
		}

		String email = User.normalizeEmail(rawEmail);
		Set<String> invalid = new TreeSet<>();
		validator.validate(new AdminCredentials(email, rawPassword))
			.forEach(violation -> invalid.add(violation.getPropertyPath().toString()));
		if (!invalid.isEmpty()) {
			if (invalid.contains("email")) {
				log.warn("Admin bootstrap skipped: MEDIVAULT_ADMIN_EMAIL is not a valid email address.");
			}
			if (invalid.contains("password")) {
				log.warn("Admin bootstrap skipped: MEDIVAULT_ADMIN_PASSWORD does not meet the password policy "
						+ "(8-64 characters, at least one letter and one number, at most 72 bytes).");
			}
			return Result.INVALID;
		}

		if (userRepository.existsByEmail(email)) {
			log.info("Admin bootstrap: an account with the configured email already exists; nothing was changed.");
			return Result.ALREADY_EXISTS;
		}
		try {
			User admin = userRepository
				.saveAndFlush(new User(ADMIN_FULL_NAME, email, passwordEncoder.encode(rawPassword), Role.ADMIN));
			log.info("Admin bootstrap: created administrator account id={}.", admin.getId());
			return Result.CREATED;
		}
		catch (DataIntegrityViolationException ex) {
			log.info("Admin bootstrap: an account with the configured email already exists; nothing was changed.");
			return Result.ALREADY_EXISTS;
		}
	}

}
