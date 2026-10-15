package com.medivault.record;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medivault.document.RecordDocumentRepository;
import com.medivault.record.DashboardInsights.Completeness;
import com.medivault.record.DashboardInsights.TypeShare;
import com.medivault.user.User;
import com.medivault.user.UserRepository;

/**
 * Read-only figures for one patient's dashboard. Every query is scoped by the owner id, which
 * callers must take from the authenticated principal. Nothing here writes.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

	private final MedicalRecordRepository recordRepository;

	private final RecordDocumentRepository documentRepository;

	private final UserRepository userRepository;

	private final MedicalRecordService recordService;

	public DashboardService(MedicalRecordRepository recordRepository, RecordDocumentRepository documentRepository,
			UserRepository userRepository, MedicalRecordService recordService) {
		this.recordRepository = recordRepository;
		this.documentRepository = documentRepository;
		this.userRepository = userRepository;
		this.recordService = recordService;
	}

	/** The insights as of today, with weeks, dates and times in the server's time zone. */
	public DashboardInsights insightsFor(Long ownerId) {
		ZoneId zone = ZoneId.systemDefault();
		return insightsFor(ownerId, LocalDate.now(zone), zone);
	}

	/** The insights as of {@code today} in {@code zone}; tests use this to fix the date and zone. */
	public DashboardInsights insightsFor(Long ownerId, LocalDate today, ZoneId zone) {
		User owner = userRepository.findById(ownerId).orElseThrow(RecordNotFoundException::new);
		long total = recordRepository.countByOwnerId(ownerId);
		Completeness completeness = new Completeness(total, recordRepository.countWithProviderForOwner(ownerId),
				recordRepository.countWithNotesForOwner(ownerId),
				documentRepository.countRecordsWithDocumentsForOwner(ownerId));
		WeekWindow window = WeekWindow.endingOn(today, zone);
		Map<RecordType, Long> typeCounts = recordService.typeCountsForOwner(ownerId);
		List<TypeShare> types = Arrays.stream(RecordType.values())
			.map(type -> TypeShare.of(type, typeCounts.getOrDefault(type, 0L), total))
			.toList();
		int providerCount = (int) recordService.providerNamesForOwner(ownerId)
			.stream()
			.filter(name -> !name.isBlank())
			.count();
		return new DashboardInsights(DashboardInsights.initials(owner.getFullName()),
				LocalDate.ofInstant(owner.getCreatedAt(), zone), completeness,
				window.bars(recordRepository.findCreatedAtForOwnerBetween(ownerId, window.start(), window.end())),
				zone,
				recordRepository.findFirstByOwnerIdOrderByRecordDateAscIdAsc(ownerId)
					.map(MedicalRecord::getRecordDate)
					.orElse(null),
				recordRepository.findFirstByOwnerIdOrderByRecordDateDescCreatedAtDescIdDesc(ownerId).orElse(null),
				recordRepository.findFirstByOwnerIdOrderByCreatedAtDescIdDesc(ownerId)
					.map(record -> LocalDateTime.ofInstant(record.getCreatedAt(), zone))
					.orElse(null),
				documentRepository.findRecentForOwner(ownerId, PageRequest.of(0, 1))
					.stream()
					.findFirst()
					.map(upload -> LocalDateTime.ofInstant(upload.getUploadedAt(), zone))
					.orElse(null),
				providerCount, types);
	}

}
