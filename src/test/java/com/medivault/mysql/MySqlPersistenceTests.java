package com.medivault.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import com.medivault.record.MedicalRecord;
import com.medivault.user.Role;
import com.medivault.user.User;

/** Real MySQL 8.4: persistence, relationships and database-level constraints. */
@MySqlIntegrationTest
class MySqlPersistenceTests extends MySqlTestSupport {

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Test
	void usersAndRecordsPersistWithTheirOwnership() {
		User owner = createUser("Asha Fictional", "Asha@Example.TEST", Role.PATIENT);
		MedicalRecord record = createRecord(owner, "Fictional lipid profile");

		User reloaded = userRepository.findByEmail("asha@example.test").orElseThrow();
		assertThat(reloaded.getRole()).isEqualTo(Role.PATIENT);
		assertThat(reloaded.isEnabled()).isTrue();
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(recordRepository.findByIdAndOwnerId(record.getId(), owner.getId())).isPresent();
		assertThat(recordRepository.findByIdAndOwnerId(record.getId(), owner.getId() + 1000)).isEmpty();
	}

	@Test
	void normalisedEmailIsUnique() {
		createUser("Asha Fictional", "asha@example.test", Role.PATIENT);
		assertThatThrownBy(() -> createUser("Other Fictional", "ASHA@example.test", Role.PATIENT))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void databaseRejectsCaseVariantDuplicatesEvenWithoutTheApplication() {
		createUser("Asha Fictional", "asha@example.test", Role.PATIENT);
		assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO app_users "
				+ "(created_at, email, enabled, full_name, password_hash, role, updated_at) "
				+ "VALUES (NOW(6), 'ASHA@EXAMPLE.TEST', 1, 'Dup', 'x', 'PATIENT', NOW(6))"))
			.isInstanceOf(DataAccessException.class);
		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void foreignKeyRejectsOrphanRecords() {
		User owner = createUser("Asha Fictional", "asha@example.test", Role.PATIENT);
		assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO medical_records "
				+ "(created_at, record_date, record_type, title, updated_at, owner_id) "
				+ "VALUES (NOW(6), CURDATE(), 'OTHER', 'Orphan', NOW(6), ?)", owner.getId() + 1_000_000))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(recordRepository.count()).isZero();
	}

	@Test
	void enumColumnsRejectUnknownValues() {
		assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO app_users "
				+ "(created_at, email, enabled, full_name, password_hash, role, updated_at) "
				+ "VALUES (NOW(6), 'enum@example.test', 1, 'Enum', 'x', 'SUPERUSER', NOW(6))"))
			.isInstanceOf(DataAccessException.class);
		assertThat(userRepository.count()).isZero();
	}

	@Test
	void failedTransactionRollsBack() {
		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			createUser("Rollback Fictional", "rollback@example.test", Role.PATIENT);
			throw new IllegalStateException("simulated failure");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(userRepository.findByEmail("rollback@example.test")).isEmpty();
	}

}
