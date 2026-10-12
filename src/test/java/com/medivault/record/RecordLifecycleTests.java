package com.medivault.record;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.medivault.IntegrationTestSupport;
import com.medivault.document.DocumentStorage;
import com.medivault.document.FictionalPdf;
import com.medivault.document.RecordDocument;
import com.medivault.document.TestDocuments;
import com.medivault.user.User;

/**
 * Creating, editing and deleting records through the web layer: validation, normalisation, mass
 * assignment, ownership (404 for anything not owned), role separation (admins get 403), anonymous
 * access, CSRF and optimistic locking. All data is fictional.
 */
class RecordLifecycleTests extends IntegrationTestSupport {

	@Autowired
	private DocumentStorage storage;

	/** A valid create request; {@code overrides} are name/value pairs that replace (never add to) a value. */
	private static MockHttpServletRequestBuilder validCreate(String... overrides) {
		return withParams(post("/records").with(csrf()), overrides, "title", "Fictional thyroid panel", "recordType",
				"LAB_REPORT", "recordDate", LocalDate.now().minusDays(3).toString(), "providerName",
				"Fictional Diagnostics", "notes", "Fictional notes.");
	}

	private static MockHttpServletRequestBuilder validEdit(MedicalRecord record, String... overrides) {
		return withParams(post("/records/" + record.getId() + "/edit").with(csrf()), overrides, "title",
				"Edited fictional title", "recordType", "IMAGING", "recordDate", LocalDate.now().minusDays(10).toString(),
				"providerName", "Edited Fictional Clinic", "notes", "Edited fictional notes.", "version",
				String.valueOf(record.getVersion()));
	}

	private static MockHttpServletRequestBuilder withParams(MockHttpServletRequestBuilder request, String[] overrides,
			String... defaults) {
		Map<String, String> values = new LinkedHashMap<>();
		for (int i = 0; i < defaults.length; i += 2) {
			values.put(defaults[i], defaults[i + 1]);
		}
		for (int i = 0; i < overrides.length; i += 2) {
			values.put(overrides[i], overrides[i + 1]);
		}
		values.forEach(request::param);
		return request;
	}

	private RecordDocument attachPdf(User owner, MedicalRecord record, String name) throws Exception {
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

	// --- create ---

	@Test
	void theNewRecordFormRendersForAPatient() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(get("/records/new").with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("Add a record"), containsString("name=\"_csrf\""),
					containsString("Discharge summary"))));
	}

	@Test
	void creatingARecordStoresItForTheLoggedInPatientAndShowsIt() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");

		var result = mockMvc.perform(validCreate().with(user(principal(asha))))
			.andExpect(status().isFound())
			.andExpect(redirectedUrlPattern("/records/*"))
			.andExpect(flash().attribute(RecordController.NOTICE, "Record created."))
			.andReturn();

		MedicalRecord saved = recordRepository.findAll().get(0);
		assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/records/" + saved.getId());
		assertThat(recordRepository.findByIdAndOwnerId(saved.getId(), asha.getId())).isPresent();
		assertThat(saved.getTitle()).isEqualTo("Fictional thyroid panel");
		assertThat(saved.getRecordType()).isEqualTo(RecordType.LAB_REPORT);
		assertThat(saved.getProviderName()).isEqualTo("Fictional Diagnostics");

		mockMvc.perform(get("/records/" + saved.getId()).with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Fictional thyroid panel")));
	}

	@Test
	void inputIsNormalisedOnSave() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(validCreate("title", "   Fictional CBC   ", "providerName", "   ", "notes", "  line one\r\nline two  ")
			.with(user(principal(asha)))).andExpect(status().isFound());

		MedicalRecord saved = recordRepository.findAll().get(0);
		assertThat(saved.getTitle()).isEqualTo("Fictional CBC");
		assertThat(saved.getProviderName()).isNull();
		assertThat(saved.getNotes()).isEqualTo("line one\nline two");
	}

	@Test
	void ownerIdAndTimestampParametersAreIgnored() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");

		mockMvc.perform(validCreate("id", "424242")
			.param("owner", String.valueOf(ravi.getId()))
			.param("owner.id", String.valueOf(ravi.getId()))
			.param("ownerId", String.valueOf(ravi.getId()))
			.param("createdAt", "2001-01-01T00:00:00Z")
			.with(user(principal(asha)))).andExpect(status().isFound());

		MedicalRecord saved = recordRepository.findAll().get(0);
		assertThat(saved.getId()).isNotEqualTo(424242L);
		assertThat(recordRepository.findByIdAndOwnerId(saved.getId(), asha.getId())).isPresent();
		assertThat(recordRepository.countByOwnerId(ravi.getId())).isZero();
		assertThat(saved.getCreatedAt()).isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z"));
	}

	@Test
	void invalidInputShowsFieldErrorsAndSavesNothing() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(post("/records").with(csrf())
			.param("title", " x ")
			.param("recordType", "")
			.param("recordDate", LocalDate.now().plusDays(1).toString())
			.param("providerName", "p".repeat(151))
			.param("notes", "n".repeat(1001))
			.with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors(RecordController.RECORD_FORM, "title", "recordType",
					"recordDate", "providerName", "notes"))
			.andExpect(content().string(allOf(containsString("Title must be between 3 and 150 characters."),
					containsString("Choose a record type."), containsString("The record date cannot be in the future."),
					containsString("Provider must be 150 characters or fewer."),
					containsString("Notes must be 1000 characters or fewer."), containsString("aria-invalid=\"true\""))));

		assertThat(recordRepository.count()).isZero();
	}

	@Test
	void malformedValuesGetAFriendlyMessageWithoutInternals() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(validCreate("recordType", "SECRET_ADMIN_TYPE", "recordDate", "31/02/2026")
			.with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("Choose a record type from the list."),
					containsString("Enter a valid date."), not(containsString("Failed to convert")),
					not(containsString("java.")))));

		assertThat(recordRepository.count()).isZero();
	}

	@Test
	void datesBefore1900AreRefused() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(validCreate("recordDate", "0202-05-01").with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(RecordForm.TOO_EARLY_MESSAGE)));

		assertThat(recordRepository.count()).isZero();
	}

	@Test
	void todayIsAnAcceptedRecordDate() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(validCreate("recordDate", LocalDate.now().toString()).with(user(principal(asha))))
			.andExpect(status().isFound());

		assertThat(recordRepository.count()).isEqualTo(1);
	}

	@Test
	void creatingWithoutACsrfTokenIsForbidden() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(post("/records").param("title", "Fictional CBC")
			.param("recordType", "LAB_REPORT")
			.param("recordDate", LocalDate.now().toString())
			.with(user(principal(asha)))).andExpect(status().isForbidden());

		assertThat(recordRepository.count()).isZero();
	}

	// --- edit ---

	@Test
	void theEditFormIsPrefilledForTheOwner() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");

		mockMvc.perform(get("/records/" + record.getId() + "/edit").with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("Edit record"), containsString("value=\"Asha lipid profile\""),
					containsString("Fictional Clinic"), containsString("name=\"version\""))));
	}

	@Test
	void editingSavesTheChangesAndKeepsOwnerAndCreationTime() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		// Reloaded, so the timestamps carry the precision the database stores (microseconds).
		MedicalRecord record = recordRepository.findById(createRecord(asha, "Asha lipid profile").getId()).orElseThrow();

		mockMvc.perform(validEdit(record).with(user(principal(asha))))
			.andExpect(redirectedUrl("/records/" + record.getId()))
			.andExpect(flash().attribute(RecordController.NOTICE, "Changes saved."));

		MedicalRecord edited = recordRepository.findByIdAndOwnerId(record.getId(), asha.getId()).orElseThrow();
		assertThat(edited.getTitle()).isEqualTo("Edited fictional title");
		assertThat(edited.getRecordType()).isEqualTo(RecordType.IMAGING);
		assertThat(edited.getProviderName()).isEqualTo("Edited Fictional Clinic");
		assertThat(edited.getVersion()).isEqualTo(record.getVersion() + 1);
		assertThat(edited.getCreatedAt()).isEqualTo(record.getCreatedAt());
		assertThat(edited.getUpdatedAt()).isAfterOrEqualTo(record.getUpdatedAt());
	}

	@Test
	void anEditBasedOnAnOutdatedVersionIsRefusedAndChangesNothing() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		// Another tab saved first.
		mockMvc.perform(validEdit(record, "title", "Saved in the other tab").with(user(principal(asha))))
			.andExpect(status().isFound());

		mockMvc.perform(validEdit(record, "title", "Stale tab edit").with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("This record was changed in another window"),
					containsString("value=\"Saved in the other tab\""))));

		assertThat(recordRepository.findById(record.getId()).orElseThrow().getTitle())
			.isEqualTo("Saved in the other tab");
	}

	@Test
	void anEditWithoutAVersionIsRefused() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");

		mockMvc.perform(post("/records/" + record.getId() + "/edit").with(csrf())
			.param("title", "No version")
			.param("recordType", "OTHER")
			.param("recordDate", LocalDate.now().toString())
			.with(user(principal(asha)))).andExpect(content().string(containsString("changed in another window")));

		assertThat(recordRepository.findById(record.getId()).orElseThrow().getTitle()).isEqualTo("Asha lipid profile");
	}

	@Test
	void invalidEditsShowErrorsAndChangeNothing() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");

		mockMvc.perform(validEdit(record, "title", "").with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors(RecordController.RECORD_FORM, "title"));

		assertThat(recordRepository.findById(record.getId()).orElseThrow().getTitle()).isEqualTo("Asha lipid profile");
	}

	@Test
	void anotherPatientCanNeitherOpenNorSubmitTheEditForm() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		MedicalRecord ravisRecord = createRecord(ravi, "Ravi private report");

		mockMvc.perform(get("/records/" + ravisRecord.getId() + "/edit").with(user(principal(asha))))
			.andExpect(status().isNotFound())
			.andExpect(content().string(not(containsString("Ravi private report"))));
		mockMvc.perform(validEdit(ravisRecord).with(user(principal(asha)))).andExpect(status().isNotFound());
		// Even an invalid submission reveals nothing: ownership is checked before validation.
		mockMvc.perform(validEdit(ravisRecord, "title", "").with(user(principal(asha))))
			.andExpect(status().isNotFound());

		assertThat(recordRepository.findById(ravisRecord.getId()).orElseThrow().getTitle())
			.isEqualTo("Ravi private report");
	}

	@Test
	void editingWithoutACsrfTokenIsForbidden() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");

		mockMvc.perform(post("/records/" + record.getId() + "/edit").param("title", "No token")
			.param("recordType", "OTHER")
			.param("recordDate", LocalDate.now().toString())
			.param("version", "0")
			.with(user(principal(asha)))).andExpect(status().isForbidden());

		assertThat(recordRepository.findById(record.getId()).orElseThrow().getTitle()).isEqualTo("Asha lipid profile");
	}

	// --- delete ---

	@Test
	void theDeleteConfirmationNamesTheRecordAndItsDocuments() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		attachPdf(asha, record, "fictional-a.pdf");
		attachPdf(asha, record, "fictional-b.pdf");

		mockMvc.perform(get("/records/" + record.getId() + "/delete").with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("Delete this record?"),
					containsString("Asha lipid profile"), containsString("All 2 attached PDF documents"),
					containsString("name=\"_csrf\""))));

		assertThat(recordRepository.count()).as("GET never deletes").isEqualTo(1);
	}

	@Test
	void deletingARecordRemovesItsDocumentsAndTheirFiles() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		MedicalRecord kept = createRecord(asha, "Asha kept record");
		RecordDocument first = attachPdf(asha, record, "fictional-a.pdf");
		RecordDocument second = attachPdf(asha, record, "fictional-b.pdf");
		RecordDocument other = attachPdf(asha, kept, "fictional-kept.pdf");

		mockMvc.perform(post("/records/" + record.getId() + "/delete").with(csrf()).with(user(principal(asha))))
			.andExpect(redirectedUrl("/records"))
			.andExpect(flash().attribute(RecordController.NOTICE,
					"Deleted “Asha lipid profile” and its 2 documents."));

		assertThat(recordRepository.findById(record.getId())).isEmpty();
		assertThat(documentRepository.findAll()).extracting(RecordDocument::getId).containsExactly(other.getId());
		assertThat(storage.exists(TestDocuments.storageKey(first))).isFalse();
		assertThat(storage.exists(TestDocuments.storageKey(second))).isFalse();
		assertThat(storage.exists(TestDocuments.storageKey(other))).isTrue();
		assertThat(recordRepository.findById(kept.getId())).isPresent();
	}

	@Test
	void anotherPatientCannotDeleteARecord() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		MedicalRecord ravisRecord = createRecord(ravi, "Ravi private report");
		RecordDocument document = attachPdf(ravi, ravisRecord, "fictional-ravi.pdf");

		mockMvc.perform(get("/records/" + ravisRecord.getId() + "/delete").with(user(principal(asha))))
			.andExpect(status().isNotFound())
			.andExpect(content().string(not(containsString("Ravi private report"))));
		mockMvc.perform(post("/records/" + ravisRecord.getId() + "/delete").with(csrf()).with(user(principal(asha))))
			.andExpect(status().isNotFound());

		assertThat(recordRepository.findById(ravisRecord.getId())).isPresent();
		assertThat(documentRepository.findById(document.getId())).isPresent();
		assertThat(storage.exists(TestDocuments.storageKey(document))).isTrue();
	}

	@Test
	void deletingWithoutACsrfTokenIsForbidden() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");

		mockMvc.perform(post("/records/" + record.getId() + "/delete").with(user(principal(asha))))
			.andExpect(status().isForbidden());

		assertThat(recordRepository.findById(record.getId())).isPresent();
	}

	// --- roles ---

	@ParameterizedTest
	@ValueSource(strings = { "/records/new", "/records/1/edit", "/records/1/delete", "/records/1/documents/1/delete" })
	void anonymousUsersAreSentToLogin(String path) throws Exception {
		mockMvc.perform(get(path)).andExpect(redirectedUrl("http://localhost/login"));
	}

	@Test
	void anonymousPostsAreRefusedAndChangeNothing() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");

		mockMvc.perform(validCreate()).andExpect(redirectedUrl("http://localhost/login"));
		mockMvc.perform(validEdit(record)).andExpect(redirectedUrl("http://localhost/login"));
		mockMvc.perform(post("/records/" + record.getId() + "/delete").with(csrf()))
			.andExpect(redirectedUrl("http://localhost/login"));

		assertThat(recordRepository.findAll()).extracting(MedicalRecord::getTitle).containsExactly("Asha lipid profile");
	}

	@Test
	void adminsCannotCreateEditOrDeleteRecords() throws Exception {
		User admin = createAdmin("Admin Fictional", "admin@example.test");
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");

		mockMvc.perform(get("/records/new").with(user(principal(admin)))).andExpect(status().isForbidden());
		mockMvc.perform(validCreate().with(user(principal(admin)))).andExpect(status().isForbidden());
		mockMvc.perform(get("/records/" + record.getId() + "/edit").with(user(principal(admin))))
			.andExpect(status().isForbidden());
		mockMvc.perform(validEdit(record).with(user(principal(admin)))).andExpect(status().isForbidden());
		mockMvc.perform(post("/records/" + record.getId() + "/delete").with(csrf()).with(user(principal(admin))))
			.andExpect(status().isForbidden());

		assertThat(recordRepository.findAll()).extracting(MedicalRecord::getTitle).containsExactly("Asha lipid profile");
	}

}
