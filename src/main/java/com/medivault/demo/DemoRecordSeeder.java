package com.medivault.demo;

import java.time.LocalDate;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medivault.record.MedicalRecord;
import com.medivault.record.MedicalRecordRepository;
import com.medivault.record.RecordType;
import com.medivault.user.PatientRegisteredEvent;
import com.medivault.user.Role;
import com.medivault.user.User;
import com.medivault.user.UserRepository;

/**
 * Adds three clearly labelled, FICTIONAL sample records to each patient who registers while the
 * {@code demo} profile is active together with one of the local H2 profiles ({@code local-h2} or
 * {@code local-file}). The bean does not exist otherwise, so demo data can never be written to a
 * MySQL database ({@code DatabaseSafetyGuard} pins both local profiles to H2).
 * <p>
 * Seeding happens at most once per account, at registration: an account that already has records is
 * skipped, so restarting with a persistent database never duplicates samples. Existing accounts,
 * passwords and roles are never changed.
 */
@Component
@Profile(DemoRecordSeeder.PROFILE_EXPRESSION)
public class DemoRecordSeeder {

	static final String PROFILE_EXPRESSION = "demo & (local-h2 | local-file)";

	private static final Logger log = LoggerFactory.getLogger(DemoRecordSeeder.class);

	static final String DEMO_PROVIDER = "Demo Health Centre (fictional)";

	static final String DEMO_NOTE = "Fictional sample record created by MediVault demo mode. This is not real medical data.";

	private final UserRepository userRepository;

	private final MedicalRecordRepository recordRepository;

	public DemoRecordSeeder(UserRepository userRepository, MedicalRecordRepository recordRepository) {
		this.userRepository = userRepository;
		this.recordRepository = recordRepository;
	}

	@PostConstruct
	void announce() {
		log.warn("Demo profile is ACTIVE: each newly registered patient receives 3 fictional sample records "
				+ "(once per account; restarts never add more). "
				+ "Never enable the 'demo' profile in production.");
	}

	@EventListener
	public void onPatientRegistered(PatientRegisteredEvent event) {
		seedFor(event.userId());
	}

	/** @return the number of records created (0 if not eligible or already seeded) */
	@Transactional
	public int seedFor(Long userId) {
		User owner = userRepository.findById(userId).orElse(null);
		if (owner == null || owner.getRole() != Role.PATIENT || recordRepository.countByOwnerId(userId) > 0) {
			return 0;
		}
		LocalDate today = LocalDate.now();
		List<MedicalRecord> samples = List.of(
				new MedicalRecord(owner, "Sample: Complete blood count (fictional)", RecordType.LAB_REPORT,
						today.minusDays(12), DEMO_PROVIDER, DEMO_NOTE),
				new MedicalRecord(owner, "Sample: Seasonal flu vaccination (fictional)", RecordType.VACCINATION,
						today.minusDays(75), DEMO_PROVIDER, DEMO_NOTE),
				new MedicalRecord(owner, "Sample: Vitamin D prescription (fictional)", RecordType.PRESCRIPTION,
						today.minusDays(160), DEMO_PROVIDER, DEMO_NOTE));
		recordRepository.saveAll(samples);
		log.info("Demo mode: added {} fictional sample records for account id={}.", samples.size(), userId);
		return samples.size();
	}

}
