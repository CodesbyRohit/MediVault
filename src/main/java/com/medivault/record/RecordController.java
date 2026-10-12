package com.medivault.record;

import java.util.Arrays;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.medivault.document.DocumentSizes;
import com.medivault.document.RecordDocumentService;
import com.medivault.record.MedicalRecordService.DeletedRecord;
import com.medivault.security.MediVaultUserDetails;

/**
 * Patient dashboard and records: list with search, detail, create, edit and delete. The owner id
 * always comes from the authenticated principal, never from the request. Access to these URLs is
 * limited to PATIENT accounts in {@code SecurityConfig}; every change is a CSRF-protected POST.
 */
@Controller
public class RecordController {

	static final String SEARCH_FORM = "search";

	static final String RECORD_FORM = "recordForm";

	/** Flash attribute for the confirmation shown after a change. */
	public static final String NOTICE = "notice";

	private final MedicalRecordService recordService;

	private final RecordDocumentService documentService;

	public RecordController(MedicalRecordService recordService, RecordDocumentService documentService) {
		this.recordService = recordService;
		this.documentService = documentService;
	}

	/** Only these fields can ever be bound from a record form: never an id, owner or timestamp. */
	@InitBinder(RECORD_FORM)
	void allowRecordFields(WebDataBinder binder) {
		binder.setAllowedFields("title", "recordType", "recordDate", "providerName", "notes", "version");
	}

	@InitBinder(SEARCH_FORM)
	void allowSearchFields(WebDataBinder binder) {
		binder.setAllowedFields("q", "type", "provider", "from", "to", "sort", "page");
	}

	@ModelAttribute("recordTypes")
	List<RecordType> recordTypes() {
		return Arrays.asList(RecordType.values());
	}

	@GetMapping("/dashboard")
	public String dashboard(@AuthenticationPrincipal MediVaultUserDetails patient, Model model) {
		Long ownerId = patient.getId();
		model.addAttribute("fullName", patient.getFullName());
		model.addAttribute("recordCount", recordService.countForOwner(ownerId));
		model.addAttribute("recentRecords", recordService.recentForOwner(ownerId));
		model.addAttribute("typeCounts", recordService.typeCountsForOwner(ownerId));
		model.addAttribute("storage", documentService.usageForOwner(ownerId));
		model.addAttribute("recentDocuments", documentService.recentForOwner(ownerId));
		return "dashboard";
	}

	@GetMapping("/records")
	public String list(@Valid @ModelAttribute(SEARCH_FORM) RecordSearchForm search, BindingResult result,
			@AuthenticationPrincipal MediVaultUserDetails patient, Model model) {
		// An invalid option is reported and ignored, so the page always shows something useful.
		result.getFieldErrors().stream().map(FieldError::getField).distinct().forEach(search::discard);
		if (search.hasInvertedDateRange()) {
			result.rejectValue("to", "range", "The end date must be on or after the start date.");
			search.discard("from");
			search.discard("to");
		}
		Long ownerId = patient.getId();
		var page = recordService.search(ownerId, search);
		model.addAttribute("page", page);
		model.addAttribute("records", page.getContent());
		model.addAttribute("documentCounts", documentService.countsForRecords(ownerId,
				page.getContent().stream().map(MedicalRecord::getId).toList()));
		model.addAttribute("hasAnyRecords", search.isFiltered() ? recordService.countForOwner(ownerId) > 0
				: page.getTotalElements() > 0);
		model.addAttribute("providers", recordService.providerNamesForOwner(ownerId));
		model.addAttribute("sorts", RecordSort.values());
		return "records/list";
	}

	@GetMapping("/records/new")
	public String newRecord(Model model) {
		model.addAttribute(RECORD_FORM, new RecordForm());
		return "records/form";
	}

	@PostMapping("/records")
	public String create(@Valid @ModelAttribute(RECORD_FORM) RecordForm form, BindingResult result,
			@AuthenticationPrincipal MediVaultUserDetails patient, RedirectAttributes redirect) {
		rejectTooEarlyDate(form, result);
		if (result.hasErrors()) {
			return "records/form";
		}
		MedicalRecord record = recordService.create(patient.getId(), form);
		redirect.addFlashAttribute(NOTICE, "Record created.");
		return "redirect:/records/" + record.getId();
	}

	@GetMapping("/records/{id}")
	public String detail(@PathVariable Long id, @AuthenticationPrincipal MediVaultUserDetails patient, Model model) {
		model.addAttribute("record", recordService.getForOwner(id, patient.getId()));
		model.addAttribute("documents", documentService.listForOwner(id, patient.getId()));
		model.addAttribute("maxUploadSize", DocumentSizes.format(documentService.maxUploadBytes()));
		return "records/detail";
	}

	@GetMapping("/records/{id}/edit")
	public String edit(@PathVariable Long id, @AuthenticationPrincipal MediVaultUserDetails patient, Model model) {
		MedicalRecord record = recordService.getForOwner(id, patient.getId());
		model.addAttribute("record", record);
		model.addAttribute(RECORD_FORM, RecordForm.from(record));
		return "records/form";
	}

	@PostMapping("/records/{id}/edit")
	public String update(@PathVariable Long id, @Valid @ModelAttribute(RECORD_FORM) RecordForm form,
			BindingResult result, @AuthenticationPrincipal MediVaultUserDetails patient, Model model,
			RedirectAttributes redirect) {
		// Ownership first, so a foreign id is a plain 404 whatever was submitted.
		MedicalRecord record = recordService.getForOwner(id, patient.getId());
		model.addAttribute("record", record);
		rejectTooEarlyDate(form, result);
		if (result.hasErrors()) {
			return "records/form";
		}
		try {
			recordService.update(id, patient.getId(), form);
		}
		catch (RecordChangedException | ObjectOptimisticLockingFailureException ex) {
			MedicalRecord current = recordService.getForOwner(id, patient.getId());
			model.addAttribute("record", current);
			model.addAttribute(RECORD_FORM, RecordForm.from(current));
			model.addAttribute("conflict", RecordChangedException.MESSAGE);
			return "records/form";
		}
		redirect.addFlashAttribute(NOTICE, "Changes saved.");
		return "redirect:/records/" + id;
	}

	@GetMapping("/records/{id}/delete")
	public String confirmDelete(@PathVariable Long id, @AuthenticationPrincipal MediVaultUserDetails patient,
			Model model) {
		model.addAttribute("record", recordService.getForOwner(id, patient.getId()));
		model.addAttribute("documentCount", documentService.listForOwner(id, patient.getId()).size());
		return "records/delete";
	}

	@PostMapping("/records/{id}/delete")
	public String delete(@PathVariable Long id, @AuthenticationPrincipal MediVaultUserDetails patient,
			RedirectAttributes redirect) {
		DeletedRecord deleted = recordService.delete(id, patient.getId());
		String documents = switch (deleted.documentCount()) {
			case 0 -> "";
			case 1 -> " and its document";
			default -> " and its " + deleted.documentCount() + " documents";
		};
		redirect.addFlashAttribute(NOTICE, "Deleted “" + deleted.title() + "”" + documents + ".");
		return "redirect:/records";
	}

	private static void rejectTooEarlyDate(RecordForm form, BindingResult result) {
		if (!result.hasFieldErrors("recordDate") && form.isDateTooEarly()) {
			result.rejectValue("recordDate", "tooEarly", RecordForm.TOO_EARLY_MESSAGE);
		}
	}

}
