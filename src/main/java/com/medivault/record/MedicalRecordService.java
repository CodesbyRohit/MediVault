package com.medivault.record;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access to medical records. Every method takes the owner id, which callers must take
 * from the authenticated principal, never from the request.
 */
@Service
@Transactional(readOnly = true)
public class MedicalRecordService {

	private final MedicalRecordRepository recordRepository;

	public MedicalRecordService(MedicalRecordRepository recordRepository) {
		this.recordRepository = recordRepository;
	}

	public List<MedicalRecord> listForOwner(Long ownerId) {
		return recordRepository.findAllByOwnerIdOrderByRecordDateDescIdDesc(ownerId);
	}

	public List<MedicalRecord> recentForOwner(Long ownerId) {
		return recordRepository.findTop3ByOwnerIdOrderByRecordDateDescIdDesc(ownerId);
	}

	public long countForOwner(Long ownerId) {
		return recordRepository.countByOwnerId(ownerId);
	}

	/** @throws RecordNotFoundException if the record doesn't exist or isn't owned by {@code ownerId} */
	public MedicalRecord getForOwner(Long recordId, Long ownerId) {
		return recordRepository.findByIdAndOwnerId(recordId, ownerId).orElseThrow(RecordNotFoundException::new);
	}

}
