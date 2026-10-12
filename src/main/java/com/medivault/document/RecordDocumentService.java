package com.medivault.document;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import com.medivault.document.DocumentRejectedException.Reason;
import com.medivault.document.DocumentStorage.StoredFile;
import com.medivault.record.MedicalRecord;
import com.medivault.record.MedicalRecordRepository;
import com.medivault.record.RecordNotFoundException;
import com.medivault.user.UserRepository;

/**
 * Upload, listing, download and deletion of PDFs attached to medical records. Every method takes
 * the owner id, which callers must take from the authenticated principal, never from the request.
 * A record or document that does not exist or belongs to someone else raises
 * {@link RecordNotFoundException} (404), so responses never reveal another patient's data.
 * <p>
 * File operations are not part of the database transaction, so they are tied to its outcome with
 * transaction callbacks, always in the direction where a failure leaves an unreferenced file (never
 * served, counted by {@link DocumentReconciliation}) rather than a row without its file:
 * <ul>
 * <li>an upload writes its file first and deletes it again unless the metadata row commits;</li>
 * <li>a deletion removes the row first and deletes the file only after that commit.</li>
 * </ul>
 */
@Service
public class RecordDocumentService {

	private static final Logger log = LoggerFactory.getLogger(RecordDocumentService.class);

	static final int MAX_FILENAME_LENGTH = 255;

	static final String PDF_EXTENSION = ".pdf";

	static final int RECENT_UPLOADS = 5;

	/** Format (bidi overrides, zero-width characters, BOM) and line/paragraph separator categories. */
	private static final java.util.Set<Integer> INVISIBLE_TYPES = java.util.Set.of((int) Character.FORMAT,
			(int) Character.LINE_SEPARATOR, (int) Character.PARAGRAPH_SEPARATOR);

	/** A download: display filename, size and an open stream the caller must close. */
	public record DocumentDownload(String filename, long size, InputStream content) {
	}

	/** What the record page shows; storage key and checksum stay server-side. */
	public record DocumentSummary(Long id, String filename, long sizeBytes, String sizeLabel, LocalDateTime uploadedAt) {
	}

	/** A recent upload on the dashboard, with its record. */
	public record RecentDocument(Long recordId, String recordTitle, Long id, String filename, String sizeLabel,
			LocalDateTime uploadedAt) {
	}

	/** A patient's document storage use against the quota. */
	public record StorageUsage(long documentCount, long usedBytes, long quotaBytes) {

		public String usedLabel() {
			return DocumentSizes.format(usedBytes);
		}

		public String quotaLabel() {
			return DocumentSizes.format(quotaBytes);
		}

	}

	private final RecordDocumentRepository documentRepository;

	private final MedicalRecordRepository recordRepository;

	private final UserRepository userRepository;

	private final DocumentStorage storage;

	private final long quotaBytes;

	public RecordDocumentService(RecordDocumentRepository documentRepository, MedicalRecordRepository recordRepository,
			UserRepository userRepository, DocumentStorage storage,
			@Value("${medivault.documents.quota-per-patient}") DataSize quota) {
		if (quota.toBytes() < storage.maxBytes()) {
			throw new IllegalStateException("Refusing to start: medivault.documents.quota-per-patient must be at least "
					+ "medivault.documents.max-size.");
		}
		this.documentRepository = documentRepository;
		this.recordRepository = recordRepository;
		this.userRepository = userRepository;
		this.storage = storage;
		this.quotaBytes = quota.toBytes();
	}

	public long maxUploadBytes() {
		return storage.maxBytes();
	}

	/** The message shown to the patient for a rejected upload. */
	public String rejectionMessage(Reason reason) {
		return switch (reason) {
			case TOO_LARGE -> reason.message() + " The limit is " + DocumentSizes.format(storage.maxBytes()) + ".";
			case QUOTA_EXCEEDED -> reason.message() + " Each patient can store up to " + DocumentSizes.format(quotaBytes)
					+ ". Delete documents you no longer need to make room.";
			default -> reason.message();
		};
	}

	@Transactional(readOnly = true)
	public List<DocumentSummary> listForOwner(Long recordId, Long ownerId) {
		return documentRepository.findAllByRecordIdAndRecordOwnerIdOrderByUploadedAtDescIdDesc(recordId, ownerId)
			.stream()
			.map(RecordDocumentService::summary)
			.toList();
	}

	/** @throws RecordNotFoundException unless the document belongs to {@code recordId} owned by {@code ownerId} */
	@Transactional(readOnly = true)
	public DocumentSummary getForOwner(Long recordId, Long documentId, Long ownerId) {
		return summary(findOwned(recordId, documentId, ownerId));
	}

	@Transactional(readOnly = true)
	public StorageUsage usageForOwner(Long ownerId) {
		return new StorageUsage(documentRepository.countByRecordOwnerId(ownerId),
				documentRepository.sumSizeBytesForOwner(ownerId), quotaBytes);
	}

	@Transactional(readOnly = true)
	public List<RecentDocument> recentForOwner(Long ownerId) {
		ZoneId zone = ZoneId.systemDefault();
		return documentRepository.findRecentForOwner(ownerId, PageRequest.of(0, RECENT_UPLOADS))
			.stream()
			.map(d -> new RecentDocument(d.getRecordId(), d.getRecordTitle(), d.getDocumentId(), d.getFilename(),
					DocumentSizes.format(d.getSizeBytes()), LocalDateTime.ofInstant(d.getUploadedAt(), zone)))
			.toList();
	}

	/** Number of documents per record id, for records owned by {@code ownerId}; records without documents are absent. */
	@Transactional(readOnly = true)
	public Map<Long, Long> countsForRecords(Long ownerId, Collection<Long> recordIds) {
		Map<Long, Long> counts = new HashMap<>();
		if (!recordIds.isEmpty()) {
			documentRepository.countByRecordForOwner(ownerId, recordIds)
				.forEach(row -> counts.put(row.getRecordId(), row.getTotal()));
		}
		return counts;
	}

	/**
	 * @throws RecordNotFoundException if the record doesn't exist or isn't owned by {@code ownerId}
	 * @throws DocumentRejectedException if the upload is not an acceptable PDF or exceeds the quota
	 * @throws DocumentStorageException if the file cannot be stored
	 */
	@Transactional
	public RecordDocument upload(Long recordId, Long ownerId, MultipartFile file) {
		// Ownership first, so a foreign record id is a plain 404 whatever was uploaded.
		MedicalRecord record = recordRepository.findByIdAndOwnerId(recordId, ownerId)
			.orElseThrow(RecordNotFoundException::new);
		if (file == null) {
			throw new DocumentRejectedException(Reason.MISSING);
		}
		if (file.isEmpty()) {
			throw new DocumentRejectedException(Reason.EMPTY);
		}
		String filename = displayFilename(file.getOriginalFilename());
		if (!filename.toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION)) {
			throw new DocumentRejectedException(Reason.NOT_PDF_EXTENSION);
		}
		if (!isDeclaredPdf(file.getContentType())) {
			throw new DocumentRejectedException(Reason.NOT_PDF_TYPE);
		}
		if (file.getSize() > storage.maxBytes()) {
			throw new DocumentRejectedException(Reason.TOO_LARGE);
		}
		// Serialises this patient's uploads until commit, so concurrent uploads cannot exceed the quota.
		userRepository.lockById(ownerId).orElseThrow(RecordNotFoundException::new);
		long used = documentRepository.sumSizeBytesForOwner(ownerId);
		if (used + file.getSize() > quotaBytes) {
			throw new DocumentRejectedException(Reason.QUOTA_EXCEEDED);
		}

		StoredFile stored;
		try {
			// The content itself is verified while streaming; the declared type is never trusted alone.
			stored = storage.store(file.getInputStream());
		}
		catch (IOException ex) {
			throw new DocumentStorageException("The upload could not be read (" + ex.getClass().getSimpleName() + ").");
		}
		deleteUnlessCommitted(stored.key());
		if (used + stored.size() > quotaBytes) {
			// The stored size differs from the declared one; the rollback removes the file again.
			throw new DocumentRejectedException(Reason.QUOTA_EXCEEDED);
		}
		return documentRepository.saveAndFlush(new RecordDocument(record, filename, stored.key(),
				MediaType.APPLICATION_PDF_VALUE, stored.size(), stored.sha256(), stored.keyId()));
	}

	/**
	 * Opens a document for download. The stored file is verified completely first (decrypted and
	 * authenticated, size and SHA-256 compared); nothing is returned for a damaged or tampered file.
	 * @throws RecordNotFoundException if the document doesn't exist, isn't attached to
	 * {@code recordId}, or the record isn't owned by {@code ownerId}
	 * @throws DocumentIntegrityException if the stored file fails verification
	 * @throws DocumentStorageException if the stored file is missing or unreadable
	 */
	@Transactional(readOnly = true)
	public DocumentDownload openForOwner(Long recordId, Long documentId, Long ownerId) {
		RecordDocument document = findOwned(recordId, documentId, ownerId);
		try {
			return new DocumentDownload(document.getOriginalFilename(), document.getSizeBytes(),
					storage.openVerified(document.getStorageFormat(), document.getStorageKey(), document.getSizeBytes(),
							document.getSha256()));
		}
		catch (DocumentIntegrityException ex) {
			log.warn("Document integrity check failed: document={} reason={}", documentId, ex.reason());
			throw ex;
		}
	}

	/**
	 * Deletes one document. Its file is removed only after the deletion commits.
	 * @return the display filename of the deleted document
	 * @throws RecordNotFoundException unless the document belongs to {@code recordId} owned by {@code ownerId}
	 */
	@Transactional
	public String delete(Long recordId, Long documentId, Long ownerId) {
		requireTransaction();
		// Locked, so the storage key cannot change underneath (for example by a concurrent conversion).
		RecordDocument document = documentRepository.lockByIdAndRecordIdAndRecordOwnerId(documentId, recordId, ownerId)
			.orElseThrow(RecordNotFoundException::new);
		documentRepository.delete(document);
		documentRepository.flush();
		deleteAfterCommit(List.of(document.getStorageKey()));
		log.info("Document deleted: id={} record={} owner={}", documentId, recordId, ownerId);
		return document.getOriginalFilename();
	}

	/**
	 * Deletes every document of a record that is being deleted, inside the caller's transaction. The
	 * files are removed only after that transaction commits.
	 * @return the number of documents deleted
	 */
	@Transactional
	public int removeAllFromRecord(Long recordId, Long ownerId) {
		requireTransaction();
		List<RecordDocument> documents = documentRepository.lockAllByRecordIdAndRecordOwnerId(recordId, ownerId);
		if (documents.isEmpty()) {
			return 0;
		}
		documentRepository.deleteAll(documents);
		documentRepository.flush();
		deleteAfterCommit(documents.stream().map(RecordDocument::getStorageKey).toList());
		return documents.size();
	}

	private RecordDocument findOwned(Long recordId, Long documentId, Long ownerId) {
		return documentRepository.findByIdAndRecordIdAndRecordOwnerId(documentId, recordId, ownerId)
			.orElseThrow(RecordNotFoundException::new);
	}

	private static DocumentSummary summary(RecordDocument d) {
		return new DocumentSummary(d.getId(), d.getOriginalFilename(), d.getSizeBytes(),
				DocumentSizes.format(d.getSizeBytes()), LocalDateTime.ofInstant(d.getUploadedAt(), ZoneId.systemDefault()));
	}

	private static void requireTransaction() {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			throw new IllegalStateException("Document changes must run inside a transaction");
		}
	}

	private void deleteUnlessCommitted(String key) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			storage.delete(key);
			throw new IllegalStateException("Uploads must run inside a transaction");
		}
		TransactionSynchronizationManager.registerSynchronization(new DeleteUnlessCommitted(storage, key));
	}

	private void deleteAfterCommit(List<String> keys) {
		TransactionSynchronizationManager.registerSynchronization(new DeleteAfterCommit(storage, keys));
	}

	/**
	 * Removes exactly one newly stored file after a definite rollback of its metadata row. When the
	 * outcome is unknown the row may exist, so the file is kept: an unreferenced file is never served,
	 * whereas a committed row without its file would be a broken document.
	 */
	static final class DeleteUnlessCommitted implements TransactionSynchronization {

		private final DocumentStorage storage;

		private final String key;

		DeleteUnlessCommitted(DocumentStorage storage, String key) {
			this.storage = storage;
			this.key = key;
		}

		@Override
		public void afterCompletion(int status) {
			if (status == STATUS_COMMITTED) {
				return;
			}
			if (status != STATUS_ROLLED_BACK) {
				log.warn("Upload outcome unknown; its stored file was kept in case the metadata was saved.");
				return;
			}
			try {
				storage.delete(key);
				log.info("Upload not saved; its stored file was removed.");
			}
			catch (RuntimeException ex) {
				log.warn("Upload not saved, and its stored file could not be removed ({}).",
						ex.getClass().getSimpleName());
			}
		}

	}

	/**
	 * Removes the files of deleted documents once their rows are definitely gone. After a rollback or
	 * an unknown outcome the rows may still exist, so the files are kept. A file that cannot be removed
	 * stays behind unreferenced: it is never served, and the startup storage check counts it.
	 */
	static final class DeleteAfterCommit implements TransactionSynchronization {

		private final DocumentStorage storage;

		private final List<String> keys;

		DeleteAfterCommit(DocumentStorage storage, List<String> keys) {
			this.storage = storage;
			this.keys = List.copyOf(keys);
		}

		@Override
		public void afterCompletion(int status) {
			if (status != STATUS_COMMITTED) {
				return;
			}
			int failed = 0;
			for (String key : keys) {
				try {
					storage.delete(key);
				}
				catch (RuntimeException ex) {
					failed++;
				}
			}
			if (failed > 0) {
				log.warn("{} of {} file(s) of deleted documents could not be removed. They are never served; the "
						+ "startup storage check reports them (see README).", failed, keys.size());
			}
		}

	}

	/**
	 * Display name only: the last path segment, without control, reserved, invisible or
	 * text-direction formatting characters (which could make a name look like something else), at
	 * most 255 characters (keeping the extension). Never used to build a path.
	 */
	static String displayFilename(String original) {
		if (original == null) {
			return "";
		}
		String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
		StringBuilder clean = new StringBuilder(name.length());
		name.codePoints()
			.filter(c -> !Character.isISOControl(c) && "<>:\"|?*".indexOf(c) < 0 && Character.isDefined(c)
					&& !INVISIBLE_TYPES.contains(Character.getType(c)))
			.forEach(clean::appendCodePoint);
		String result = clean.toString().strip();
		if (result.length() > MAX_FILENAME_LENGTH) {
			int dot = result.lastIndexOf('.');
			String extension = dot > 0 && result.length() - dot <= 10 ? result.substring(dot) : "";
			int cut = MAX_FILENAME_LENGTH - extension.length();
			if (Character.isHighSurrogate(result.charAt(cut - 1))) {
				cut--; // never split an emoji or other supplementary character
			}
			result = result.substring(0, cut) + extension;
		}
		return result;
	}

	private static boolean isDeclaredPdf(String contentType) {
		if (contentType == null) {
			return false;
		}
		try {
			return MediaType.APPLICATION_PDF.equalsTypeAndSubtype(MediaType.parseMediaType(contentType));
		}
		catch (InvalidMediaTypeException ex) {
			return false;
		}
	}

}
