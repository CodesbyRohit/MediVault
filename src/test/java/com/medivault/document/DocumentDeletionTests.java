package com.medivault.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.medivault.IntegrationTestSupport;
import com.medivault.record.MedicalRecord;
import com.medivault.record.RecordController;
import com.medivault.user.User;

/**
 * Deleting single documents, and the per-patient document figures on the records list and the
 * dashboard: ownership (404), role separation (403), anonymous access and CSRF. Fictional data only.
 */
class DocumentDeletionTests extends IntegrationTestSupport {

	@Autowired
	private DocumentStorage storage;

	private RecordDocument attach(User owner, MedicalRecord record, String name) throws Exception {
		mockMvc.perform(multipart("/records/" + record.getId() + "/documents")
			.file(new MockMultipartFile("file", name, "application/pdf", FictionalPdf.bytes(name)))
			.with(csrf())
			.with(user(principal(owner)))).andExpect(flash().attribute("uploadSuccess", true));
		return documentRepository.findAll()
			.stream()
			.filter(d -> d.getOriginalFilename().equals(name))
			.findFirst()
			.orElseThrow();
	}

	private static String deleteUrl(MedicalRecord record, RecordDocument document) {
		return "/records/" + record.getId() + "/documents/" + document.getId() + "/delete";
	}

	@Test
	void theOwnerConfirmsAndDeletesOneDocumentAndItsFile() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument deleted = attach(asha, record, "fictional-delete-me.pdf");
		RecordDocument kept = attach(asha, record, "fictional-keep-me.pdf");

		mockMvc.perform(get(deleteUrl(record, deleted)).with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("Delete this document?"),
					containsString("fictional-delete-me.pdf"), containsString("Asha lipid profile"))));
		assertThat(documentRepository.count()).as("GET never deletes").isEqualTo(2);

		mockMvc.perform(post(deleteUrl(record, deleted)).with(csrf()).with(user(principal(asha))))
			.andExpect(redirectedUrl("/records/" + record.getId()))
			.andExpect(flash().attribute(RecordController.NOTICE,
					"Deleted the document “fictional-delete-me.pdf”."));

		assertThat(documentRepository.findAll()).extracting(RecordDocument::getId).containsExactly(kept.getId());
		assertThat(storage.exists(deleted.getStorageKey())).isFalse();
		assertThat(storage.exists(kept.getStorageKey())).isTrue();
		mockMvc.perform(get("/records/" + record.getId() + "/documents/" + deleted.getId()).with(user(principal(asha))))
			.andExpect(status().isNotFound());
	}

	@Test
	void anotherPatientCannotSeeOrDeleteTheDocument() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		MedicalRecord ravisRecord = createRecord(ravi, "Ravi private report");
		MedicalRecord ashasRecord = createRecord(asha, "Asha lipid profile");
		RecordDocument document = attach(ravi, ravisRecord, "fictional-ravi.pdf");

		mockMvc.perform(get(deleteUrl(ravisRecord, document)).with(user(principal(asha))))
			.andExpect(status().isNotFound())
			.andExpect(content().string(not(containsString("fictional-ravi.pdf"))));
		mockMvc.perform(post(deleteUrl(ravisRecord, document)).with(csrf()).with(user(principal(asha))))
			.andExpect(status().isNotFound());
		// Pairing Ravi's document with Asha's own record does not work either.
		mockMvc.perform(post("/records/" + ashasRecord.getId() + "/documents/" + document.getId() + "/delete")
			.with(csrf())
			.with(user(principal(asha)))).andExpect(status().isNotFound());

		assertThat(documentRepository.findById(document.getId())).isPresent();
		assertThat(storage.exists(document.getStorageKey())).isTrue();
	}

	@Test
	void adminsAnonymousUsersAndRequestsWithoutCsrfCannotDelete() throws Exception {
		User admin = createAdmin("Admin Fictional", "admin@example.test");
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument document = attach(asha, record, "fictional-report.pdf");

		mockMvc.perform(get(deleteUrl(record, document)).with(user(principal(admin)))).andExpect(status().isForbidden());
		mockMvc.perform(post(deleteUrl(record, document)).with(csrf()).with(user(principal(admin))))
			.andExpect(status().isForbidden());
		mockMvc.perform(post(deleteUrl(record, document)).with(csrf()))
			.andExpect(redirectedUrl("http://localhost/login"));
		mockMvc.perform(post(deleteUrl(record, document)).with(user(principal(asha)))).andExpect(status().isForbidden());

		assertThat(documentRepository.findById(document.getId())).isPresent();
		assertThat(storage.exists(document.getStorageKey())).isTrue();
	}

	@Test
	void theRecordsListAndDashboardShowOnlyTheOwnersDocumentFigures() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		MedicalRecord withTwo = createRecord(asha, "Asha two PDFs");
		MedicalRecord withNone = createRecord(asha, "Asha no PDFs");
		MedicalRecord ravisRecord = createRecord(ravi, "Ravi private report");
		RecordDocument first = attach(asha, withTwo, "fictional-one.pdf");
		RecordDocument second = attach(asha, withTwo, "fictional-two.pdf");
		attach(ravi, ravisRecord, "fictional-ravi-secret.pdf");

		mockMvc.perform(get("/records").with(user(principal(asha))))
			.andExpect(model().attribute("documentCounts", Map.of(withTwo.getId(), 2L)))
			.andExpect(content().string(containsString("2 PDFs")));
		assertThat(withNone.getId()).isNotNull();

		var usage = (RecordDocumentService.StorageUsage) mockMvc.perform(get("/dashboard").with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("fictional-two.pdf"), containsString("Asha two PDFs"),
					containsString("Records by type"), not(containsString("fictional-ravi-secret.pdf")),
					not(containsString("Ravi private report")))))
			.andReturn()
			.getModelAndView()
			.getModel()
			.get("storage");
		assertThat(usage.documentCount()).isEqualTo(2);
		assertThat(usage.usedBytes()).isEqualTo(first.getSizeBytes() + second.getSizeBytes());
	}

}
