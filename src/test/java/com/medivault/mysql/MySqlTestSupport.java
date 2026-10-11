package com.medivault.mysql;

import java.sql.SQLException;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.medivault.document.RecordDocumentRepository;
import com.medivault.record.MedicalRecord;
import com.medivault.record.MedicalRecordRepository;
import com.medivault.record.RecordType;
import com.medivault.user.Role;
import com.medivault.user.User;
import com.medivault.user.UserRepository;

/**
 * Shared helpers for {@link MySqlIntegrationTest} classes. Each concrete test class must carry the
 * {@code @MySqlIntegrationTest} annotation itself.
 * <p>
 * Cleanup only deletes rows (DELETE is an application privilege) in {@code medivault_it}; the
 * guard guarantees that database is the only possible target. Schema objects are never dropped.
 */
abstract class MySqlTestSupport {

	/** Fictional password that satisfies the password policy; never a real credential. */
	protected static final String PASSWORD = "Fictional123";

	@Autowired
	protected UserRepository userRepository;

	@Autowired
	protected MedicalRecordRepository recordRepository;

	@Autowired
	protected RecordDocumentRepository documentRepository;

	@Autowired
	protected PasswordEncoder passwordEncoder;

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	@BeforeEach
	void deleteTestRows() {
		documentRepository.deleteAll();
		recordRepository.deleteAll();
		userRepository.deleteAll();
	}

	protected User createUser(String fullName, String email, Role role) {
		return userRepository.save(new User(fullName, email, passwordEncoder.encode(PASSWORD), role));
	}

	protected MedicalRecord createRecord(User owner, String title) {
		return recordRepository.save(new MedicalRecord(owner, title, RecordType.LAB_REPORT,
				LocalDate.now().minusDays(1), "Fictional Clinic", "Fictional notes"));
	}

	/** MySQL vendor error code behind a Spring data-access exception, or -1. */
	protected static int mysqlErrorCode(Throwable failure) {
		Throwable cause = failure instanceof DataAccessException dae ? dae.getMostSpecificCause() : failure;
		for (Throwable t = cause; t != null; t = t.getCause()) {
			if (t instanceof SQLException sql) {
				return sql.getErrorCode();
			}
		}
		return -1;
	}

}
