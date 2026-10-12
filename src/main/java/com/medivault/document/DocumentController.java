package com.medivault.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.medivault.document.RecordDocumentService.DocumentDownload;
import com.medivault.record.MedicalRecordService;
import com.medivault.record.RecordController;
import com.medivault.security.MediVaultUserDetails;

/**
 * PDF upload, download and deletion for a patient's own records. The owner id always comes from the
 * authenticated principal. Access to these URLs (under {@code /records/**}) is limited to PATIENT
 * accounts in {@code SecurityConfig}; uploads are CSRF-protected POSTs. There are no public PDF
 * URLs: every download re-checks ownership and streams the file from private storage.
 */
@Controller
public class DocumentController {

	private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

	static final String STORAGE_FAILURE_MESSAGE = "The document could not be saved. Please try again.";

	private final RecordDocumentService documentService;

	private final MedicalRecordService recordService;

	public DocumentController(RecordDocumentService documentService, MedicalRecordService recordService) {
		this.documentService = documentService;
		this.recordService = recordService;
	}

	@PostMapping("/records/{recordId}/documents")
	public String upload(@PathVariable Long recordId, @RequestParam(name = "file", required = false) MultipartFile file,
			@AuthenticationPrincipal MediVaultUserDetails patient, RedirectAttributes redirect) {
		try {
			documentService.upload(recordId, patient.getId(), file);
			redirect.addFlashAttribute("uploadSuccess", true);
		}
		catch (DocumentRejectedException ex) {
			redirect.addFlashAttribute("uploadError", documentService.rejectionMessage(ex.reason()));
		}
		catch (DocumentStorageException ex) {
			log.warn("Document upload failed: {}", ex.getMessage());
			redirect.addFlashAttribute("uploadError", STORAGE_FAILURE_MESSAGE);
		}
		return "redirect:/records/" + recordId;
	}

	@GetMapping("/records/{recordId}/documents/{documentId}")
	public ResponseEntity<InputStreamResource> download(@PathVariable Long recordId, @PathVariable Long documentId,
			@AuthenticationPrincipal MediVaultUserDetails patient) {
		DocumentDownload download = documentService.openForOwner(recordId, documentId, patient.getId());
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.contentLength(download.size())
			// "attachment": the browser saves the file instead of rendering it inside MediVault's origin.
			.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(download.filename()))
			.body(new InputStreamResource(download.content()));
	}

	@GetMapping("/records/{recordId}/documents/{documentId}/delete")
	public String confirmDelete(@PathVariable Long recordId, @PathVariable Long documentId,
			@AuthenticationPrincipal MediVaultUserDetails patient, Model model) {
		model.addAttribute("record", recordService.getForOwner(recordId, patient.getId()));
		model.addAttribute("document", documentService.getForOwner(recordId, documentId, patient.getId()));
		return "records/document-delete";
	}

	@PostMapping("/records/{recordId}/documents/{documentId}/delete")
	public String delete(@PathVariable Long recordId, @PathVariable Long documentId,
			@AuthenticationPrincipal MediVaultUserDetails patient, RedirectAttributes redirect) {
		String filename = documentService.delete(recordId, documentId, patient.getId());
		redirect.addFlashAttribute(RecordController.NOTICE, "Deleted the document “" + filename + "”.");
		return "redirect:/records/" + recordId;
	}

	/** Plain quoted name for ASCII filenames; RFC 5987 {@code filename*} only when needed. */
	static String contentDisposition(String filename) {
		ContentDisposition.Builder attachment = ContentDisposition.attachment();
		boolean ascii = StandardCharsets.US_ASCII.newEncoder().canEncode(filename);
		return (ascii ? attachment.filename(filename) : attachment.filename(filename, StandardCharsets.UTF_8)).build()
			.toString();
	}

	/** A stored file is missing or unreadable: generic error page, nothing about paths or causes. */
	@ExceptionHandler(DocumentStorageException.class)
	public void storageFailure(DocumentStorageException ex, HttpServletResponse response) throws IOException {
		log.warn("Document download failed: {}", ex.getMessage());
		response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value());
	}

}
