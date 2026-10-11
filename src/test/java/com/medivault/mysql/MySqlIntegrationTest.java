package com.medivault.mysql;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Marks an opt-in test that runs against the isolated MySQL 8.4 integration database.
 * <p>
 * Skipped (reported as skipped, never as passed) unless {@code MEDIVAULT_IT_DB_URL} is set and has
 * the approved shape {@code jdbc:mysql://127.0.0.1:<port other than 3306>/medivault_it?...}. At
 * startup {@code DatabaseSafetyGuard} re-validates the full configuration before any connection,
 * including that only the {@code medivault_it_*} accounts are used.
 * <p>
 * Credentials come only from environment variables of the shell that runs Maven:
 * {@code MEDIVAULT_IT_DB_URL}, {@code MEDIVAULT_IT_DB_USERNAME}, {@code MEDIVAULT_IT_DB_PASSWORD},
 * {@code MEDIVAULT_IT_DB_MIGRATION_USERNAME}, {@code MEDIVAULT_IT_DB_MIGRATION_PASSWORD}.
 * These tests never drop or recreate databases or tables.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("mysql")
@EnabledIfEnvironmentVariable(named = "MEDIVAULT_IT_DB_URL",
		matches = "jdbc:mysql://127\\.0\\.0\\.1:(?!3306\\b)[0-9]{1,5}/medivault_it\\?.*",
		disabledReason = "MySQL 8.4 integration database not configured (MEDIVAULT_IT_DB_URL)")
@SpringBootTest
@ActiveProfiles("mysql")
@TestPropertySource(properties = { "spring.datasource.url=${MEDIVAULT_IT_DB_URL}",
		"spring.datasource.username=${MEDIVAULT_IT_DB_USERNAME}",
		"spring.datasource.password=${MEDIVAULT_IT_DB_PASSWORD}",
		"spring.flyway.user=${MEDIVAULT_IT_DB_MIGRATION_USERNAME}",
		"spring.flyway.password=${MEDIVAULT_IT_DB_MIGRATION_PASSWORD}", "medivault.admin.email=",
		"medivault.admin.password=" })
public @interface MySqlIntegrationTest {

}
