package com.medivault.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronization;

import com.medivault.IntegrationTestSupport;
import com.medivault.record.MedicalRecord;
import com.medivault.record.MedicalRecordRepository;
import com.medivault.user.User;

/**
 * Deletions when the file system or the database fails. The direction is always the same: a failure
 * may leave an unreferenced file (never served), but never a document row without its file.
 */
@ExtendWith(OutputCaptureExtension.class)
class DocumentDeletionFailureTests extends IntegrationTestSupport {

	@MockitoSpyBean
	private DocumentStorage storage;

	@MockitoSpyBean
	private MedicalRecordRepository spiedRecordRepository;

	@AfterEach
	void resetSpies() {
		reset(storage, spiedRecordRepository);
	}

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

	@Test
	void aFileThatCannotBeRemovedAfterCommitStaysUnreferencedAndIsLoggedWithoutDetails(CapturedOutput output)
			throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument document = attach(asha, record, "fictional-locked.pdf");
		doThrow(new DocumentStorageException("A stored document could not be deleted (AccessDeniedException)."))
			.when(storage)
			.delete(anyString());

		mockMvc.perform(post("/records/" + record.getId() + "/documents/" + document.getId() + "/delete").with(csrf())
			.with(user(principal(asha)))).andExpect(redirectedUrl("/records/" + record.getId()));

		assertThat(documentRepository.findById(document.getId())).as("the deletion itself committed").isEmpty();
		assertThat(storage.exists(document.getStorageKey())).as("the file stays behind, unreferenced").isTrue();
		assertThat(output).contains("1 of 1 file(s) of deleted documents could not be removed")
			.doesNotContain(document.getStorageKey())
			.doesNotContain("fictional-locked.pdf");
	}

	@Test
	void aFailedRecordDeletionRollsBackAndKeepsEveryRowAndFile() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument first = attach(asha, record, "fictional-a.pdf");
		RecordDocument second = attach(asha, record, "fictional-b.pdf");
		doThrow(new DataIntegrityViolationException("simulated failure")).when(spiedRecordRepository).delete(any(MedicalRecord.class));

		// Unhandled, so a real server renders the generic error page (MockMvc surfaces the exception instead).
		assertThatThrownBy(() -> mockMvc.perform(post("/records/" + record.getId() + "/delete").with(csrf())
			.with(user(principal(asha))))).hasRootCauseInstanceOf(DataIntegrityViolationException.class);

		assertThat(recordRepository.findById(record.getId())).isPresent();
		assertThat(documentRepository.findAll()).hasSize(2);
		assertThat(storage.exists(first.getStorageKey())).isTrue();
		assertThat(storage.exists(second.getStorageKey())).isTrue();
	}

	@Test
	void filesAreRemovedOnlyAfterADefiniteCommit() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		String rolledBack = attach(asha, record, "fictional-rolled-back.pdf").getStorageKey();
		String unknown = attach(asha, record, "fictional-unknown.pdf").getStorageKey();
		String committed = attach(asha, record, "fictional-committed.pdf").getStorageKey();

		new RecordDocumentService.DeleteAfterCommit(storage, java.util.List.of(rolledBack))
			.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
		new RecordDocumentService.DeleteAfterCommit(storage, java.util.List.of(unknown))
			.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
		new RecordDocumentService.DeleteAfterCommit(storage, java.util.List.of(committed))
			.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

		assertThat(storage.exists(rolledBack)).isTrue();
		assertThat(storage.exists(unknown)).isTrue();
		assertThat(storage.exists(committed)).isFalse();
	}

}
