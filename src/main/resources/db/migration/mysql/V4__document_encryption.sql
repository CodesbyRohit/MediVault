-- =====================================================================
-- MediVault V4: encrypted document storage
--   com.medivault.document.RecordDocument -> record_documents
--   storage_format      how the file is stored; authoritative, so a file is
--                       never read in another format. Existing rows were
--                       written before encryption and become PLAIN_V0.
--   key_id              Tink key id of an encrypted file (inventory and
--                       re-encryption after key rotation); required when
--                       the file is encrypted.
--   legacy_storage_key  the plaintext original of a converted document,
--                       kept until its deletion is complete (conversion
--                       clean-up); never served.
-- sha256 and size_bytes keep describing the plaintext PDF.
-- Requires ALTER on record_documents for the migration account.
-- NOT YET VERIFIED against MySQL 8.4.
-- =====================================================================

ALTER TABLE record_documents
    ADD COLUMN storage_format ENUM ('ENCRYPTED_V1', 'PLAIN_V0') NOT NULL DEFAULT 'PLAIN_V0',
    ADD COLUMN key_id BIGINT NULL,
    ADD COLUMN legacy_storage_key VARCHAR(36) NULL,
    ADD CONSTRAINT uk_record_documents_legacy_storage_key UNIQUE (legacy_storage_key),
    ADD CONSTRAINT ck_record_documents_key_id CHECK (storage_format <> 'ENCRYPTED_V1' OR key_id IS NOT NULL);
