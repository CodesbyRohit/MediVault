package com.medivault.record;

import java.time.Instant;
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

	/** The three most recently added records; the id breaks ties between identical timestamps. */
	List<MedicalRecord> findTop3ByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);

	Optional<MedicalRecord> findByIdAndOwnerId(Long id, Long ownerId);

	long countByOwnerId(Long ownerId);

	@Query("select r.recordType as type, count(r) as total from MedicalRecord r where r.owner.id = :ownerId "
			+ "group by r.recordType")
	List<TypeCount> countByTypeForOwner(@Param("ownerId") Long ownerId);

	/** Records naming a provider; a name of spaces only does not count. */
	@Query("select count(r) from MedicalRecord r where r.owner.id = :ownerId "
			+ "and r.providerName is not null and trim(r.providerName) <> ''")
	long countWithProviderForOwner(@Param("ownerId") Long ownerId);

	/** Records with notes; notes of spaces only do not count. */
	@Query("select count(r) from MedicalRecord r where r.owner.id = :ownerId "
			+ "and r.notes is not null and trim(r.notes) <> ''")
	long countWithNotesForOwner(@Param("ownerId") Long ownerId);

	/** When each of the owner's records added in {@code [from, to)} was added. */
	@Query("select r.createdAt from MedicalRecord r where r.owner.id = :ownerId "
			+ "and r.createdAt >= :from and r.createdAt < :to")
	List<Instant> findCreatedAtForOwnerBetween(@Param("ownerId") Long ownerId, @Param("from") Instant from,
			@Param("to") Instant to);

	/** The record with the earliest record date; the id breaks ties. */
	Optional<MedicalRecord> findFirstByOwnerIdOrderByRecordDateAscIdAsc(Long ownerId);

	/** The record with the latest record date; ties go to the most recently added, then the highest id. */
	Optional<MedicalRecord> findFirstByOwnerIdOrderByRecordDateDescCreatedAtDescIdDesc(Long ownerId);

	/** The most recently added record; the id breaks ties between identical timestamps. */
	Optional<MedicalRecord> findFirstByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);

	@Query("select distinct r.providerName from MedicalRecord r where r.owner.id = :ownerId "
			+ "and r.providerName is not null order by r.providerName")
	List<String> findProviderNamesForOwner(@Param("ownerId") Long ownerId);

}
