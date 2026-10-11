package com.medivault.record;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.medivault.document.DocumentSizes;
import com.medivault.document.RecordDocumentService;
import com.medivault.security.MediVaultUserDetails;

/**
 * Patient dashboard and records. The owner id always comes from the authenticated principal.
 * Access to these URLs is limited to PATIENT accounts in {@code SecurityConfig}.
 */
@Controller
public class RecordController {

	private final MedicalRecordService recordService;

	private final RecordDocumentService documentService;

	public RecordController(MedicalRecordService recordService, RecordDocumentService documentService) {
		this.recordService = recordService;
		this.documentService = documentService;
	}

	@GetMapping("/dashboard")
	public String dashboard(@AuthenticationPrincipal MediVaultUserDetails patient, Model model) {
		model.addAttribute("fullName", patient.getFullName());
		model.addAttribute("recordCount", recordService.countForOwner(patient.getId()));
		model.addAttribute("recentRecords", recordService.recentForOwner(patient.getId()));
		return "dashboard";
	}

	@GetMapping("/records")
	public String list(@AuthenticationPrincipal MediVaultUserDetails patient, Model model) {
		model.addAttribute("records", recordService.listForOwner(patient.getId()));
		return "records/list";
	}

	@GetMapping("/records/{id}")
	public String detail(@PathVariable Long id, @AuthenticationPrincipal MediVaultUserDetails patient, Model model) {
		model.addAttribute("record", recordService.getForOwner(id, patient.getId()));
		model.addAttribute("documents", documentService.listForOwner(id, patient.getId()));
		model.addAttribute("maxUploadSize", DocumentSizes.format(documentService.maxUploadBytes()));
		return "records/detail";
	}

}
