package com.medivault.record;

import java.time.Instant;
import java.time.LocalDate;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import com.medivault.user.User;

/**
 * Metadata for one medical record. The owner link is one-directional: {@link User} has no
 * collection of records, so loading a user never loads their medical data.
 */
@Entity
@Table(name = "medical_records", indexes = @Index(name = "idx_medical_records_owner", columnList = "owner_id"))
public class MedicalRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_medical_records_owner"))
	private User owner;

	@NotBlank
	@Size(min = 3, max = 150)
	@Column(nullable = false, length = 150)
	private String title;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "record_type", nullable = false, length = 30)
	private RecordType recordType;

	@NotNull
	@PastOrPresent
	@Column(name = "record_date", nullable = false)
	private LocalDate recordDate;

	@Size(max = 150)
	@Column(name = "provider_name", length = 150)
	private String providerName;

	@Size(max = 1000)
	@Column(length = 1000)
	private String notes;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected MedicalRecord() {
		// for JPA
	}

	public MedicalRecord(User owner, String title, RecordType recordType, LocalDate recordDate, String providerName,
			String notes) {
		this.owner = owner;
		this.title = title;
		this.recordType = recordType;
		this.recordDate = recordDate;
		this.providerName = providerName;
		this.notes = notes;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public RecordType getRecordType() {
		return recordType;
	}

	public LocalDate getRecordDate() {
		return recordDate;
	}

	public String getProviderName() {
		return providerName;
	}

	public String getNotes() {
		return notes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
