package com.medivault.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Real MySQL 8.4: the application and migration accounts have only their planned privileges and
 * cannot reach the development database. Every probe is denied by MySQL's privilege check before
 * it could act (the DROP target does not exist), so a passing run changes nothing.
 */
@MySqlIntegrationTest
class MySqlLeastPrivilegeTests extends MySqlTestSupport {

	/** ER_TABLEACCESS_DENIED_ERROR and ER_DBACCESS_DENIED_ERROR. */
	private static final int TABLE_ACCESS_DENIED = 1142;

	private static final int DB_ACCESS_DENIED = 1044;

	private static JdbcTemplate migratorTemplate() {
		// Same guard-validated URL; the migration account's credentials come from the environment.
		DriverManagerDataSource dataSource = new DriverManagerDataSource(System.getenv("MEDIVAULT_IT_DB_URL"),
				System.getenv("MEDIVAULT_IT_DB_MIGRATION_USERNAME"),
				System.getenv("MEDIVAULT_IT_DB_MIGRATION_PASSWORD"));
		return new JdbcTemplate(dataSource);
	}

	@Test
	void applicationAccountCannotChangeTheSchema() {
		assertThat(mysqlErrorCode(catchThrowable(() -> jdbcTemplate.execute(
				"CREATE TABLE zz_privilege_probe (id INT)")))).isEqualTo(TABLE_ACCESS_DENIED);
		assertThat(mysqlErrorCode(catchThrowable(() -> jdbcTemplate.execute(
				"DROP TABLE zz_privilege_probe_does_not_exist")))).isEqualTo(TABLE_ACCESS_DENIED);
		assertThat(mysqlErrorCode(catchThrowable(() -> jdbcTemplate.execute(
				"ALTER TABLE app_users ADD COLUMN zz_probe INT")))).isEqualTo(TABLE_ACCESS_DENIED);
	}

	@Test
	void applicationAccountCannotReadTheDevelopmentDatabase() {
		assertThat(mysqlErrorCode(catchThrowable(() -> jdbcTemplate.queryForList(
				"SELECT 1 FROM medivault_dev.app_users")))).isIn(TABLE_ACCESS_DENIED, DB_ACCESS_DENIED);
	}

	@Test
	void migrationAccountCannotDropOrReachTheDevelopmentDatabase() {
		JdbcTemplate migrator = migratorTemplate();
		assertThat(mysqlErrorCode(catchThrowable(() -> migrator.execute(
				"DROP TABLE zz_privilege_probe_does_not_exist")))).isEqualTo(TABLE_ACCESS_DENIED);
		assertThat(mysqlErrorCode(catchThrowable(() -> migrator.queryForList(
				"SELECT 1 FROM medivault_dev.app_users")))).isIn(TABLE_ACCESS_DENIED, DB_ACCESS_DENIED);
	}

	@Test
	void noAccountHasGlobalPrivileges() {
		assertThat(jdbcTemplate.queryForList("SHOW GRANTS", String.class))
			.noneMatch(grant -> grant.contains(" ON *.* ") && !grant.startsWith("GRANT USAGE"))
			.noneMatch(grant -> grant.contains("WITH GRANT OPTION"));
		assertThat(migratorTemplate().queryForList("SHOW GRANTS", String.class))
			.noneMatch(grant -> grant.contains(" ON *.* ") && !grant.startsWith("GRANT USAGE"))
			.noneMatch(grant -> grant.contains("WITH GRANT OPTION"))
			.noneMatch(grant -> grant.contains("DROP"));
	}

}
