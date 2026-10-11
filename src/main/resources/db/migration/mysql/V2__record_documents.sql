-- =====================================================================
-- MediVault V2: metadata for PDF documents attached to medical records
--   com.medivault.document.RecordDocument -> record_documents
--
-- The PDF bytes are never stored in the database, only an opaque storage
-- key, size and SHA-256 checksum. Types follow V1 (Instant -> DATETIME(6)
-- in UTC). NOT YET VERIFIED against MySQL 8.4: confirmed only by a real
-- run of the opt-in MySqlSchemaMigrationTests.
-- =====================================================================

CREATE TABLE record_documents (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    content_type      VARCHAR(50)  NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    sha256            VARCHAR(64)  NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    storage_key       VARCHAR(36)  NOT NULL,
    uploaded_at       DATETIME(6)  NOT NULL,
    record_id         BIGINT       NOT NULL,
    CONSTRAINT pk_record_documents PRIMARY KEY (id),
    CONSTRAINT uk_record_documents_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_record_documents_size CHECK (size_bytes > 0),
    INDEX idx_record_documents_record (record_id),
    CONSTRAINT fk_record_documents_record FOREIGN KEY (record_id) REFERENCES medical_records (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
