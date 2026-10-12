package com.medivault.document;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.medivault.record.MedicalRecord;

/**
 * Metadata for one PDF attached to a medical record. The PDF bytes are never stored here: they
 * live in private file storage ({@link DocumentStorage}) under the opaque {@code storageKey}.
 * Ownership is inherited through the record. Rows are never updated after upload.
 */
@Entity
@Table(name = "record_documents",
		indexes = @Index(name = "idx_record_documents_record", columnList = "record_id"),
		uniqueConstraints = { @UniqueConstraint(name = "uk_record_documents_storage_key", columnNames = "storage_key"),
				@UniqueConstraint(name = "uk_record_documents_legacy_storage_key", columnNames = "legacy_storage_key") })
public class RecordDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "record_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_record_documents_record"))
	private MedicalRecord record;

	/** Sanitised name for display and downloads only; never used as a filesystem path. */
	@Column(name = "original_filename", nullable = false, length = 255, updatable = false)
	private String originalFilename;

	@Column(name = "storage_key", nullable = false, length = 36, updatable = false)
	private String storageKey;

	@Column(name = "content_type", nullable = false, length = 50, updatable = false)
	private String contentType;

	@Column(name = "size_bytes", nullable = false, updatable = false)
	private long sizeBytes;

	@Column(nullable = false, length = 64, updatable = false)
	private String sha256;

	@Column(name = "uploaded_at", nullable = false, updatable = false)
	private Instant uploadedAt;

	/** How the file is stored; authoritative when the file is read (see {@link StorageFormat}). */
	@Enumerated(EnumType.STRING)
	@Column(name = "storage_format", nullable = false, length = 20)
	private StorageFormat storageFormat;

	/** Tink id of the key that encrypted the file; {@code null} for {@link StorageFormat#PLAIN_V0}. */
	@Column(name = "key_id")
	private Long keyId;

	/** Storage key of the plaintext original while a conversion's clean-up is pending; never served. */
	@Column(name = "legacy_storage_key", length = 36)
	private String legacyStorageKey;

	protected RecordDocument() {
		// for JPA
	}

	/** A newly uploaded, encrypted document. */
	public RecordDocument(MedicalRecord record, String originalFilename, String storageKey, String contentType,
			long sizeBytes, String sha256, int keyId) {
		this.record = record;
		this.originalFilename = originalFilename;
		this.storageKey = storageKey;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.sha256 = sha256;
		this.storageFormat = StorageFormat.ENCRYPTED_V1;
		this.keyId = (long) keyId;
	}

	@PrePersist
	void onCreate() {
		this.uploadedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	String getStorageKey() {
		return storageKey;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getSha256() {
		return sha256;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

	public StorageFormat getStorageFormat() {
		return storageFormat;
	}

	public Long getKeyId() {
		return keyId;
	}

	String getLegacyStorageKey() {
		return legacyStorageKey;
	}

}
