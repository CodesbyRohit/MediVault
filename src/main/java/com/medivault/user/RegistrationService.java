package com.medivault.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

	private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final ApplicationEventPublisher events;

	public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			ApplicationEventPublisher events) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.events = events;
	}

	/**
	 * Creates a new, enabled {@link Role#PATIENT} account. The role is fixed here and can
	 * never come from the request.
	 * @throws EmailAlreadyRegisteredException if the (normalised) email is already in use
	 */
	@Transactional
	public User registerPatient(RegistrationForm form) {
		String email = User.normalizeEmail(form.getEmail());
		if (userRepository.existsByEmail(email)) {
			throw new EmailAlreadyRegisteredException();
		}
		User patient = new User(form.getFullName(), email, passwordEncoder.encode(form.getPassword()), Role.PATIENT);
		try {
			userRepository.saveAndFlush(patient);
		}
		catch (DataIntegrityViolationException ex) {
			// Lost a race with a concurrent registration for the same email.
			throw new EmailAlreadyRegisteredException();
		}
		log.info("Registered new patient account id={}", patient.getId());
		events.publishEvent(new PatientRegisteredEvent(patient.getId()));
		return patient;
	}

}
