package com.medivault;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.medivault.document.RecordDocumentRepository;
import com.medivault.record.MedicalRecord;
import com.medivault.record.MedicalRecordRepository;
import com.medivault.record.RecordType;
import com.medivault.security.MediVaultUserDetails;
import com.medivault.user.Role;
import com.medivault.user.User;
import com.medivault.user.UserRepository;

/**
 * Base class for all integration tests.
 * <p>
 * {@code @TestPropertySource} outranks OS environment variables, so the in-memory H2
 * database is always used (even if {@code SPRING_DATASOURCE_*} is set), uploaded PDFs go to
 * {@code target/test-storage} (never {@code %LOCALAPPDATA%\MediVault}), and any
 * {@code MEDIVAULT_ADMIN_*} variables on the developer's machine are ignored.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties",
		properties = { "medivault.admin.email=", "medivault.admin.password=" })
public abstract class IntegrationTestSupport {

	/** Fictional password that satisfies the password policy. */
	protected static final String PASSWORD = "Fictional123";

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected UserRepository userRepository;

	@Autowired
	protected PasswordEncoder passwordEncoder;

	@Autowired
	protected MedicalRecordRepository recordRepository;

	@Autowired
	protected RecordDocumentRepository documentRepository;

	@BeforeEach
	void cleanDatabase() {
		documentRepository.deleteAll();
		recordRepository.deleteAll();
		userRepository.deleteAll();
	}

	protected MedicalRecord createRecord(User owner, String title) {
		return recordRepository.save(new MedicalRecord(owner, title, RecordType.LAB_REPORT, LocalDate.now().minusDays(1),
				"Fictional Clinic", "Fictional notes for " + title));
	}

	protected MedicalRecord createRecord(User owner, String title, LocalDate date) {
		return recordRepository
			.save(new MedicalRecord(owner, title, RecordType.PRESCRIPTION, date, null, null));
	}

	protected User createUser(String fullName, String email, Role role) {
		return userRepository.save(new User(fullName, email, passwordEncoder.encode(PASSWORD), role));
	}

	protected User createPatient(String fullName, String email) {
		return createUser(fullName, email, Role.PATIENT);
	}

	protected User createAdmin(String fullName, String email) {
		return createUser(fullName, email, Role.ADMIN);
	}

	protected static MediVaultUserDetails principal(User user) {
		return MediVaultUserDetails.from(user);
	}

}
