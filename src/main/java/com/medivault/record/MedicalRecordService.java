package com.medivault.record;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medivault.document.RecordDocumentService;
import com.medivault.user.UserRepository;

/**
 * Medical records of one owner. Every method takes the owner id, which callers must take from the
 * authenticated principal, never from the request. A record that does not exist or belongs to
 * someone else raises {@link RecordNotFoundException} (404).
 */
@Service
@Transactional(readOnly = true)
public class MedicalRecordService {

	private static final Logger log = LoggerFactory.getLogger(MedicalRecordService.class);

	public static final int PAGE_SIZE = 20;

	/** What a deletion removed, for the confirmation message. */
	public record DeletedRecord(String title, int documentCount) {
	}

	private final MedicalRecordRepository recordRepository;

	private final UserRepository userRepository;

	private final RecordDocumentService documentService;

	public MedicalRecordService(MedicalRecordRepository recordRepository, UserRepository userRepository,
			RecordDocumentService documentService) {
		this.recordRepository = recordRepository;
		this.userRepository = userRepository;
		this.documentService = documentService;
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

	/**
	 * One page of the owner's records matching {@code criteria}. A page number past the end returns the
	 * last page instead of an empty one.
	 */
	public Page<MedicalRecord> search(Long ownerId, RecordSearchForm criteria) {
		var spec = RecordSpecifications.search(ownerId, criteria);
		PageRequest request = PageRequest.of(criteria.getPage(), PAGE_SIZE, criteria.getSort().sort());
		Page<MedicalRecord> page = recordRepository.findAll(spec, request);
		if (page.isEmpty() && page.getTotalPages() > 0 && criteria.getPage() >= page.getTotalPages()) {
			page = recordRepository.findAll(spec, request.withPage(page.getTotalPages() - 1));
		}
		return page;
	}

	/** The distinct provider names the owner has used, for the provider filter. */
	public List<String> providerNamesForOwner(Long ownerId) {
		return recordRepository.findProviderNamesForOwner(ownerId);
	}

	/** Record counts per type, in the order of {@link RecordType}; types without records are left out. */
	public Map<RecordType, Long> typeCountsForOwner(Long ownerId) {
		Map<RecordType, Long> counts = new EnumMap<>(RecordType.class);
		recordRepository.countByTypeForOwner(ownerId).forEach(row -> counts.put(row.getType(), row.getTotal()));
		return counts;
	}

	@Transactional
	public MedicalRecord create(Long ownerId, RecordForm form) {
		MedicalRecord record = recordRepository.saveAndFlush(new MedicalRecord(userRepository.getReferenceById(ownerId),
				form.getTitle(), form.getRecordType(), form.getRecordDate(), form.getProviderName(), form.getNotes()));
		log.info("Record created: id={} owner={}", record.getId(), ownerId);
		return record;
	}

	/**
	 * @throws RecordNotFoundException if the record doesn't exist or isn't owned by {@code ownerId}
	 * @throws RecordChangedException if the form was based on an older version of the record
	 * @throws org.springframework.orm.ObjectOptimisticLockingFailureException if a concurrent edit won
	 */
	@Transactional
	public MedicalRecord update(Long recordId, Long ownerId, RecordForm form) {
		MedicalRecord record = getForOwner(recordId, ownerId);
		if (form.getVersion() == null || form.getVersion() != record.getVersion()) {
			throw new RecordChangedException();
		}
		record.revise(form.getTitle(), form.getRecordType(), form.getRecordDate(), form.getProviderName(),
				form.getNotes());
		recordRepository.flush();
		log.info("Record updated: id={} owner={}", record.getId(), ownerId);
		return record;
	}

	/**
	 * Deletes the record and its documents. Their files are removed only after the deletion commits
	 * (see {@link RecordDocumentService#removeAllFromRecord}).
	 * @throws RecordNotFoundException if the record doesn't exist or isn't owned by {@code ownerId}
	 */
	@Transactional
	public DeletedRecord delete(Long recordId, Long ownerId) {
		MedicalRecord record = getForOwner(recordId, ownerId);
		int documents = documentService.removeAllFromRecord(recordId, ownerId);
		recordRepository.delete(record);
		recordRepository.flush();
		log.info("Record deleted: id={} owner={} documents={}", recordId, ownerId, documents);
		return new DeletedRecord(record.getTitle(), documents);
	}

}
