package com.medivault.record;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every query used by patient-facing code is scoped by owner id. Patient flows must never
 * use the inherited {@code findById}.
 */
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

	List<MedicalRecord> findAllByOwnerIdOrderByRecordDateDescIdDesc(Long ownerId);

	List<MedicalRecord> findTop3ByOwnerIdOrderByRecordDateDescIdDesc(Long ownerId);

	Optional<MedicalRecord> findByIdAndOwnerId(Long id, Long ownerId);

	long countByOwnerId(Long ownerId);

}
