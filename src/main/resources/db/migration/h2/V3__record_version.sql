-- =====================================================================
-- MediVault V3 (H2): optimistic locking for medical records
--   com.medivault.record.MedicalRecord#version -> medical_records.version
-- Mirrors db/migration/mysql/V3__record_version.sql. Existing rows start at
-- version 0. An edit submitted from an outdated copy of a record is then
-- refused instead of silently overwriting a newer change.
-- =====================================================================

ALTER TABLE medical_records ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
