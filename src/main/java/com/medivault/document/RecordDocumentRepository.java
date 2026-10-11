package com.medivault.document;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every query used by patient-facing code is scoped by record id AND the record's owner id.
 * Patient flows must never use the inherited {@code findById}.
 */
public interface RecordDocumentRepository extends JpaRepository<RecordDocument, Long> {

	List<RecordDocument> findAllByRecordIdAndRecordOwnerIdOrderByUploadedAtDescIdDesc(Long recordId, Long ownerId);

	Optional<RecordDocument> findByIdAndRecordIdAndRecordOwnerId(Long id, Long recordId, Long ownerId);

}
