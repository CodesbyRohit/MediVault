package com.medivault.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import com.medivault.IntegrationTestSupport;
import com.medivault.document.DocumentRejectedException.Reason;
import com.medivault.record.MedicalRecord;
import com.medivault.user.User;

/**
 * The per-patient storage quota: 2,000 bytes here, with 800-byte fictional PDFs, so two uploads fit
 * and a third does not. Other patients' documents never count, and deleting frees space.
 */
@TestPropertySource(properties = { "medivault.documents.max-size=1KB", "medivault.documents.quota-per-patient=2000B" })
class DocumentQuotaTests extends IntegrationTestSupport {

	private static final byte[] PDF = FictionalPdf.ofSize(800);

	@Autowired
	private DocumentStorage storage;

	@Autowired
	private RecordDocumentService documentService;

	private ResultActions upload(User owner, MedicalRecord record) throws Exception {
		return mockMvc.perform(multipart("/records/" + record.getId() + "/documents")
			.file(new MockMultipartFile("file", "fictional.pdf", "application/pdf", PDF))
			.with(csrf())
			.with(user(principal(owner))));
	}

	@Test
	void uploadsBeyondTheQuotaAreRefusedAndLeaveNoFile() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		upload(asha, record).andExpect(flash().attribute("uploadSuccess", true));
		upload(asha, record).andExpect(flash().attribute("uploadSuccess", true));
		Set<String> before = storage.listKeys();

		upload(asha, record).andExpect(flash().attribute("uploadError", documentService.rejectionMessage(Reason.QUOTA_EXCEEDED)));

		assertThat(documentRepository.count()).isEqualTo(2);
		assertThat(storage.listKeys()).isEqualTo(before);
		assertThat(documentService.rejectionMessage(Reason.QUOTA_EXCEEDED)).contains("2 KB");
	}

	@Test
	void otherPatientsDocumentsDoNotCountAndDeletingFreesSpace() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		MedicalRecord ashasRecord = createRecord(asha, "Asha lipid profile");
		MedicalRecord ravisRecord = createRecord(ravi, "Ravi report");
		upload(ravi, ravisRecord).andExpect(flash().attribute("uploadSuccess", true));
		upload(ravi, ravisRecord).andExpect(flash().attribute("uploadSuccess", true));

		upload(asha, ashasRecord).andExpect(flash().attribute("uploadSuccess", true));
		upload(asha, ashasRecord).andExpect(flash().attribute("uploadSuccess", true));
		upload(asha, ashasRecord).andExpect(flash().attribute("uploadError",
				documentService.rejectionMessage(Reason.QUOTA_EXCEEDED)));

		RecordDocument first = documentRepository.findAllByRecordIdAndRecordOwnerIdOrderByUploadedAtDescIdDesc(
				ashasRecord.getId(), asha.getId()).get(0);
		mockMvc.perform(post("/records/" + ashasRecord.getId() + "/documents/" + first.getId() + "/delete").with(csrf())
			.with(user(principal(asha))));
		upload(asha, ashasRecord).andExpect(flash().attribute("uploadSuccess", true));
	}

}
