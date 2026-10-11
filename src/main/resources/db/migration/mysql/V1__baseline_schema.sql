-- =====================================================================
-- MediVault V1: baseline schema for the existing JPA entities
--   com.medivault.user.User            -> app_users
--   com.medivault.record.MedicalRecord -> medical_records
--
-- Column types, lengths, nullability and names mirror what Hibernate 6.6
-- (MySQLDialect, MySQL 8.4) expects, so `ddl-auto=validate` can pass:
--   Long @Id IDENTITY -> BIGINT AUTO_INCREMENT
--   boolean           -> BIT(1)
--   Instant           -> DATETIME(6)  (stored in UTC: hibernate.jdbc.time_zone=UTC)
--   LocalDate         -> DATE
--   @Enumerated(STRING) -> native ENUM (decision D4 candidate; confirmed only by a
--                          real MySQL 8.4 validation run, see MySqlSchemaMigrationTests)
--
-- Runs only under the "mysql" profile with the migration account. Never edit this
-- file after it has been applied to a shared database; add V2__... instead.
-- =====================================================================

CREATE TABLE app_users (
    id            BIGINT                    NOT NULL AUTO_INCREMENT,
    created_at    DATETIME(6)               NOT NULL,
    email         VARCHAR(254)              NOT NULL,
    enabled       BIT(1)                    NOT NULL,
    full_name     VARCHAR(100)              NOT NULL,
    password_hash VARCHAR(100)              NOT NULL,
    role          ENUM ('ADMIN', 'PATIENT') NOT NULL,
    updated_at    DATETIME(6)               NOT NULL,
    CONSTRAINT pk_app_users PRIMARY KEY (id),
    CONSTRAINT uk_app_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE medical_records (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    created_at    DATETIME(6)   NOT NULL,
    notes         VARCHAR(1000) NULL,
    provider_name VARCHAR(150)  NULL,
    record_date   DATE          NOT NULL,
    record_type   ENUM ('DISCHARGE_SUMMARY', 'IMAGING', 'LAB_REPORT', 'OTHER', 'PRESCRIPTION', 'VACCINATION') NOT NULL,
    title         VARCHAR(150)  NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    owner_id      BIGINT        NOT NULL,
    CONSTRAINT pk_medical_records PRIMARY KEY (id),
    INDEX idx_medical_records_owner (owner_id),
    CONSTRAINT fk_medical_records_owner FOREIGN KEY (owner_id) REFERENCES app_users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
