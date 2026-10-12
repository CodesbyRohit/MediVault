package com.medivault.record;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every query used by patient-facing code is scoped by owner id. Patient flows must never
 * use the inherited {@code findById}, and specification queries must start from
 * {@link RecordSpecifications#ownedBy(Long)}.
 */
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long>,
		JpaSpecificationExecutor<MedicalRecord> {

	/** How many of one owner's records have one type. */
	interface TypeCount {

		RecordType getType();

		long getTotal();

	}

	List<MedicalRecord> findAllByOwnerIdOrderByRecordDateDescIdDesc(Long ownerId);

	List<MedicalRecord> findTop3ByOwnerIdOrderByRecordDateDescIdDesc(Long ownerId);

	Optional<MedicalRecord> findByIdAndOwnerId(Long id, Long ownerId);

	long countByOwnerId(Long ownerId);

	@Query("select r.recordType as type, count(r) as total from MedicalRecord r where r.owner.id = :ownerId "
			+ "group by r.recordType")
	List<TypeCount> countByTypeForOwner(@Param("ownerId") Long ownerId);

	@Query("select distinct r.providerName from MedicalRecord r where r.owner.id = :ownerId "
			+ "and r.providerName is not null order by r.providerName")
	List<String> findProviderNamesForOwner(@Param("ownerId") Long ownerId);

}
