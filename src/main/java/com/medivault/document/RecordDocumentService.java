package com.medivault.document;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.medivault.document.DocumentRejectedException.Reason;
import com.medivault.document.DocumentStorage.StoredFile;
import com.medivault.record.MedicalRecord;
import com.medivault.record.MedicalRecordService;
import com.medivault.record.RecordNotFoundException;

/**
 * Upload, listing and download of PDFs attached to medical records. Every method takes the owner
 * id, which callers must take from the authenticated principal, never from the request. A record
 * or document that does not exist or belongs to someone else raises {@link RecordNotFoundException}
 * (404), so responses never reveal another patient's data.
 * <p>
 * File writes are not part of the database transaction. Each upload therefore registers a
 * transaction callback that deletes the newly written file (and only that file) unless the
 * metadata row commits. A crash between the file move and the commit can still leave an
 * unreferenced file behind; it is never served, because downloads only follow committed rows.
 */
@Service
public class RecordDocumentService {

	private static final Logger log = LoggerFactory.getLogger(RecordDocumentService.class);

	static final int MAX_FILENAME_LENGTH = 255;

	static final String PDF_EXTENSION = ".pdf";

	/** Format (bidi overrides, zero-width characters, BOM) and line/paragraph separator categories. */
	private static final java.util.Set<Integer> INVISIBLE_TYPES = java.util.Set.of((int) Character.FORMAT,
			(int) Character.LINE_SEPARATOR, (int) Character.PARAGRAPH_SEPARATOR);

	/** A download: display filename, size and an open stream the caller must close. */
	public record DocumentDownload(String filename, long size, InputStream content) {
	}

	/** What the record page shows; storage key and checksum stay server-side. */
	public record DocumentSummary(Long id, String filename, long sizeBytes, String sizeLabel, LocalDateTime uploadedAt) {
	}

	private final RecordDocumentRepository documentRepository;

	private final MedicalRecordService recordService;

	private final DocumentStorage storage;

	public RecordDocumentService(RecordDocumentRepository documentRepository, MedicalRecordService recordService,
			DocumentStorage storage) {
		this.documentRepository = documentRepository;
		this.recordService = recordService;
		this.storage = storage;
	}

	public long maxUploadBytes() {
		return storage.maxBytes();
	}

	/** The message shown to the patient for a rejected upload. */
	public String rejectionMessage(Reason reason) {
		return reason == Reason.TOO_LARGE
				? reason.message() + " The limit is " + DocumentSizes.format(storage.maxBytes()) + "."
				: reason.message();
	}

	@Transactional(readOnly = true)
	public List<DocumentSummary> listForOwner(Long recordId, Long ownerId) {
		ZoneId zone = ZoneId.systemDefault();
		return documentRepository.findAllByRecordIdAndRecordOwnerIdOrderByUploadedAtDescIdDesc(recordId, ownerId)
			.stream()
			.map(d -> new DocumentSummary(d.getId(), d.getOriginalFilename(), d.getSizeBytes(),
					DocumentSizes.format(d.getSizeBytes()), LocalDateTime.ofInstant(d.getUploadedAt(), zone)))
			.toList();
	}

	/**
	 * @throws RecordNotFoundException if the record doesn't exist or isn't owned by {@code ownerId}
	 * @throws DocumentRejectedException if the upload is not an acceptable PDF
	 * @throws DocumentStorageException if the file cannot be stored
	 */
	@Transactional
	public RecordDocument upload(Long recordId, Long ownerId, MultipartFile file) {
		// Ownership first, so a foreign record id is a plain 404 whatever was uploaded.
		MedicalRecord record = recordService.getForOwner(recordId, ownerId);
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

		StoredFile stored;
		try {
			// The content itself is verified while streaming; the declared type is never trusted alone.
			stored = storage.store(file.getInputStream());
		}
		catch (IOException ex) {
			throw new DocumentStorageException("The upload could not be read (" + ex.getClass().getSimpleName() + ").");
		}
		deleteUnlessCommitted(stored.key());
		return documentRepository.saveAndFlush(new RecordDocument(record, filename, stored.key(),
				MediaType.APPLICATION_PDF_VALUE, stored.size(), stored.sha256()));
	}

	/**
	 * @throws RecordNotFoundException if the document doesn't exist, isn't attached to
	 * {@code recordId}, or the record isn't owned by {@code ownerId}
	 */
	@Transactional(readOnly = true)
	public DocumentDownload openForOwner(Long recordId, Long documentId, Long ownerId) {
		RecordDocument document = documentRepository.findByIdAndRecordIdAndRecordOwnerId(documentId, recordId, ownerId)
			.orElseThrow(RecordNotFoundException::new);
		return new DocumentDownload(document.getOriginalFilename(), document.getSizeBytes(),
				storage.open(document.getStorageKey()));
	}

	private void deleteUnlessCommitted(String key) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			storage.delete(key);
			throw new IllegalStateException("Uploads must run inside a transaction");
		}
		TransactionSynchronizationManager.registerSynchronization(new DeleteUnlessCommitted(storage, key));
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
