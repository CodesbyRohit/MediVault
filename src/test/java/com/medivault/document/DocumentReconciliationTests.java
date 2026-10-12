package com.medivault.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import com.medivault.IntegrationTestSupport;
import com.medivault.record.MedicalRecord;
import com.medivault.user.User;

/**
 * The read-only startup storage check. It uses its own folder (cleared before each test) so files
 * left by other test classes cannot affect the counts.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "medivault.documents.directory=${user.dir}/target/test-storage/reconciliation")
class DocumentReconciliationTests extends IntegrationTestSupport {

	@Autowired
	private DocumentReconciliation reconciliation;

	@Autowired
	private DocumentStorage storage;

	@Value("${medivault.documents.directory}")
	private Path directory;

	@BeforeEach
	void emptyTheFolder() throws IOException {
		try (Stream<Path> files = Files.list(directory)) {
			for (Path file : files.toList()) {
				Files.delete(file);
			}
		}
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
	void aConsistentStoreReportsNothingToReview() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		attach(asha, record, "fictional-a.pdf");
		attach(asha, record, "fictional-b.pdf");

		assertThat(reconciliation.check()).isEqualTo(new DocumentReconciliation.Report(2, 2, 0, 0));
		assertThat(reconciliation.check().consistent()).isTrue();
	}

	@Test
	void unreferencedAndMissingFilesAreCountedButNeverChanged(CapturedOutput output) throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument kept = attach(asha, record, "fictional-kept.pdf");
		RecordDocument missing = attach(asha, record, "fictional-missing.pdf");
		String orphan = storage.store(new ByteArrayInputStream(FictionalPdf.bytes("Orphan"))).key();
		Files.delete(directory.resolve(missing.getStorageKey()));
		// Unfinished uploads and unrelated names are not documents.
		Files.writeString(directory.resolve("upload-123.part"), "partial");
		Files.writeString(directory.resolve("notes.txt"), "not a document");

		assertThat(reconciliation.check()).isEqualTo(new DocumentReconciliation.Report(2, 2, 1, 1));
		reconciliation.checkAndLog();

		assertThat(storage.exists(orphan)).as("never deleted automatically").isTrue();
		assertThat(storage.exists(kept.getStorageKey())).isTrue();
		assertThat(documentRepository.findById(missing.getId())).as("rows are never changed").isPresent();
		assertThat(output).contains("1 unreferenced file(s)")
			.contains("1 document row(s) whose file is missing")
			.doesNotContain(orphan)
			.doesNotContain(missing.getStorageKey())
			.doesNotContain(directory.toString());
	}

}
