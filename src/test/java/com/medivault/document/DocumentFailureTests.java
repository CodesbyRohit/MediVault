package com.medivault.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.medivault.IntegrationTestSupport;
import com.medivault.record.MedicalRecord;
import com.medivault.user.User;

/**
 * Failure handling: storage errors give safe messages, a failed database insert removes the file
 * that was just written (and nothing else), and a file missing on disk gives a generic error.
 */
class DocumentFailureTests extends IntegrationTestSupport {

	@MockitoSpyBean
	private DocumentStorage storage;

	@MockitoSpyBean
	private RecordDocumentRepository spiedRepository;

	@Value("${medivault.documents.directory}")
	private Path documentsDirectory;

	private static final MockMultipartFile PDF = new MockMultipartFile("file", "fictional-report.pdf",
			"application/pdf", FictionalPdf.bytes("Failure handling"));

	@AfterEach
	void resetSpies() {
		reset(storage, spiedRepository);
	}

	private Set<String> storedFiles() throws IOException {
		try (Stream<Path> files = Files.list(documentsDirectory)) {
			return files.map(file -> file.getFileName().toString()).collect(Collectors.toSet());
		}
	}

	@Test
	void aStorageFailureShowsAGenericMessageAndSavesNothing() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		doThrow(new DocumentStorageException("The document could not be written (AccessDeniedException)."))
			.when(storage)
			.store(any());

		mockMvc.perform(multipart("/records/" + record.getId() + "/documents").file(PDF).with(csrf())
			.with(user(principal(asha))))
			.andExpect(flash().attribute("uploadError", DocumentController.STORAGE_FAILURE_MESSAGE));

		assertThat(documentRepository.count()).isZero();
	}

	@Test
	void aFailedDatabaseInsertRemovesOnlyTheNewlyWrittenFile() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		// An earlier, successful upload must survive the cleanup.
		mockMvc.perform(multipart("/records/" + record.getId() + "/documents").file(PDF).with(csrf())
			.with(user(principal(asha))))
			.andExpect(flash().attribute("uploadSuccess", true));
		RecordDocument existing = documentRepository.findAll().get(0);
		Set<String> before = storedFiles();
		assertThat(before).contains(existing.getStorageKey());

		doThrow(new DataIntegrityViolationException("simulated constraint failure")).when(spiedRepository)
			.saveAndFlush(any());

		// Unhandled, so a real server renders the generic error page (MockMvc surfaces the exception instead).
		assertThatThrownBy(() -> mockMvc.perform(multipart("/records/" + record.getId() + "/documents").file(PDF)
			.with(csrf())
			.with(user(principal(asha))))).hasRootCauseInstanceOf(DataIntegrityViolationException.class);

		assertThat(documentRepository.count()).isEqualTo(1);
		assertThat(storedFiles()).as("the new file is removed; the earlier one is untouched").isEqualTo(before);
		try (var in = storage.open(existing.getStorageKey())) {
			assertThat(in.readAllBytes()).isEqualTo(PDF.getBytes());
		}
	}

	/**
	 * Regression (M4 audit): byte-range and HEAD requests on a download must neither fail nor leave
	 * the stored file open. Every stream handed out by the storage is tracked until it is closed.
	 */
	@Test
	void rangeAndHeadRequestsAnswerCleanlyAndCloseTheFile() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		mockMvc.perform(multipart("/records/" + record.getId() + "/documents").file(PDF).with(csrf())
			.with(user(principal(asha))))
			.andExpect(flash().attribute("uploadSuccess", true));
		RecordDocument document = documentRepository.findAll().get(0);
		String url = "/records/" + record.getId() + "/documents/" + document.getId();
		java.util.List<java.util.concurrent.atomic.AtomicBoolean> closed = new java.util.concurrent.CopyOnWriteArrayList<>();
		org.mockito.Mockito.doAnswer(invocation -> {
			java.io.InputStream real = (java.io.InputStream) invocation.callRealMethod();
			java.util.concurrent.atomic.AtomicBoolean flag = new java.util.concurrent.atomic.AtomicBoolean();
			closed.add(flag);
			return new java.io.FilterInputStream(real) {
				@Override
				public void close() throws IOException {
					flag.set(true);
					super.close();
				}
			};
		}).when(storage).open(any());

		mockMvc.perform(get(url).header("Range", "bytes=0-9").with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().bytes(PDF.getBytes()));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head(url)
			.with(user(principal(asha))))
			.andExpect(status().isOk());
		mockMvc.perform(get(url).with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().bytes(PDF.getBytes()));

		assertThat(closed).hasSize(3).allSatisfy(flag -> assertThat(flag).as("stream closed").isTrue());
	}

	@Test
	void aStoredFileMissingOnDiskGivesAGenericErrorWithoutPaths() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		mockMvc.perform(multipart("/records/" + record.getId() + "/documents").file(PDF).with(csrf())
			.with(user(principal(asha))))
			.andExpect(flash().attribute("uploadSuccess", true));
		RecordDocument document = documentRepository.findAll().get(0);
		Files.delete(documentsDirectory.resolve(document.getStorageKey()));

		mockMvc.perform(get("/records/" + record.getId() + "/documents/" + document.getId()).with(user(principal(asha))))
			.andExpect(status().isInternalServerError())
			.andExpect(content().string(allOf(not(containsString("test-storage")), not(containsString(
					document.getStorageKey())), not(containsString("NoSuchFile")))));
		// The record page still works.
		mockMvc.perform(get("/records/" + record.getId()).with(user(principal(asha)))).andExpect(status().isOk());
	}

}
