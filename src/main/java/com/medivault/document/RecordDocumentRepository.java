package com.medivault.document;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every query used by patient-facing code is scoped by record id AND the record's owner id (or by
 * the owner id alone for per-patient totals). Patient flows must never use the inherited
 * {@code findById}. {@link #findAllStorageKeys()} is only for the startup storage check.
 */
public interface RecordDocumentRepository extends JpaRepository<RecordDocument, Long> {

	/** A recent upload with the title of its record, for the dashboard. */
	interface RecentUpload {

		Long getDocumentId();

		String getFilename();

		long getSizeBytes();

		Instant getUploadedAt();

		Long getRecordId();

		String getRecordTitle();

	}

	/** How many documents one record has. */
	interface RecordDocumentCount {

		Long getRecordId();

		long getTotal();

	}

	List<RecordDocument> findAllByRecordIdAndRecordOwnerIdOrderByUploadedAtDescIdDesc(Long recordId, Long ownerId);

	Optional<RecordDocument> findByIdAndRecordIdAndRecordOwnerId(Long id, Long recordId, Long ownerId);

	/** Like {@link #findByIdAndRecordIdAndRecordOwnerId}, locking the row until the transaction ends. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from RecordDocument d where d.id = :id and d.record.id = :recordId and d.record.owner.id = :ownerId")
	Optional<RecordDocument> lockByIdAndRecordIdAndRecordOwnerId(@Param("id") Long id, @Param("recordId") Long recordId,
			@Param("ownerId") Long ownerId);

	/** All documents of one owned record, locking their rows until the transaction ends. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from RecordDocument d where d.record.id = :recordId and d.record.owner.id = :ownerId order by d.id")
	List<RecordDocument> lockAllByRecordIdAndRecordOwnerId(@Param("recordId") Long recordId,
			@Param("ownerId") Long ownerId);

	long countByRecordOwnerId(Long ownerId);

	@Query("select coalesce(sum(d.sizeBytes), 0) from RecordDocument d where d.record.owner.id = :ownerId")
	long sumSizeBytesForOwner(@Param("ownerId") Long ownerId);

	@Query("select d.id as documentId, d.originalFilename as filename, d.sizeBytes as sizeBytes, "
			+ "d.uploadedAt as uploadedAt, r.id as recordId, r.title as recordTitle "
			+ "from RecordDocument d join d.record r where r.owner.id = :ownerId order by d.uploadedAt desc, d.id desc")
	List<RecentUpload> findRecentForOwner(@Param("ownerId") Long ownerId, Pageable limit);

	@Query("select d.record.id as recordId, count(d) as total from RecordDocument d "
			+ "where d.record.owner.id = :ownerId and d.record.id in :recordIds group by d.record.id")
	List<RecordDocumentCount> countByRecordForOwner(@Param("ownerId") Long ownerId,
			@Param("recordIds") Collection<Long> recordIds);

	@Query("select d.storageKey from RecordDocument d")
	List<String> findAllStorageKeys();

}
