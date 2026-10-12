-- =====================================================================
-- MediVault V3: optimistic locking for medical records
--   com.medivault.record.MedicalRecord#version -> medical_records.version
-- Existing rows start at version 0. An edit submitted from an outdated copy
-- of a record is then refused instead of silently overwriting a newer change.
-- Requires ALTER on medical_records for the migration account.
-- =====================================================================

ALTER TABLE medical_records ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
