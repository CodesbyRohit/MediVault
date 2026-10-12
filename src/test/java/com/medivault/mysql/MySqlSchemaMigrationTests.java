package com.medivault.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * Real MySQL 8.4: Flyway migration, Hibernate validation (the context only starts if
 * {@code ddl-auto=validate} accepts the migrated schema), TLS, D4 enum evidence and repeat runs.
 */
@MySqlIntegrationTest
class MySqlSchemaMigrationTests extends MySqlTestSupport {

	@Autowired
	private Flyway flyway;

	@Autowired
	private Environment environment;

	@Test
	void flywayAppliedExactlyTheVersionedMigrations() {
		MigrationInfo[] applied = flyway.info().applied();
		assertThat(applied).extracting(info -> info.getVersion().getVersion()).containsExactly("1", "2", "3", "4");
		assertThat(applied).extracting(MigrationInfo::getState).containsOnly(MigrationState.SUCCESS);
		assertThat(flyway.info().pending()).isEmpty();
	}

	@Test
	void hibernateOnlyValidatesTheSchema() {
		// Reaching this point means Hibernate validated the Flyway-created schema successfully.
		assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
		assertThat(environment.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
	}

	@Test
	void repeatedMigrationIsANoOpAndChecksumsStillValidate() {
		assertThat(flyway.migrate().migrationsExecuted).isZero();
		assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
		assertThat(flyway.info().applied()).hasSize(4);
	}

	@Test
	void applicationConnectionIsEncrypted() {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("SHOW SESSION STATUS LIKE 'Ssl_cipher'");
		assertThat(rows).hasSize(1);
		assertThat(String.valueOf(rows.get(0).get("Value"))).isNotBlank();
		List<Map<String, Object>> version = jdbcTemplate.queryForList("SHOW SESSION STATUS LIKE 'Ssl_version'");
		assertThat(String.valueOf(version.get(0).get("Value"))).isIn("TLSv1.2", "TLSv1.3");
	}

	@Test
	void enumColumnsAreNativeEnums() {
		// Evidence for decision D4 on the real server.
		List<String> types = jdbcTemplate.queryForList("SELECT DATA_TYPE FROM information_schema.COLUMNS "
				+ "WHERE TABLE_SCHEMA = DATABASE() AND ((TABLE_NAME = 'app_users' AND COLUMN_NAME = 'role') "
				+ "OR (TABLE_NAME = 'medical_records' AND COLUMN_NAME = 'record_type'))", String.class);
		assertThat(types).containsExactlyInAnyOrder("enum", "enum");
	}

	@Test
	void connectedToTheIntegrationDatabaseOnly() {
		assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("medivault_it");
		assertThat(jdbcTemplate.queryForObject("SELECT @@port", Integer.class)).isNotEqualTo(3306);
	}

}
