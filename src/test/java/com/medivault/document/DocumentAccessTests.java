package com.medivault.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.medivault.IntegrationTestSupport;
import com.medivault.document.DocumentRejectedException.Reason;
import com.medivault.record.MedicalRecord;
import com.medivault.user.User;

/**
 * Upload and download through the web layer: validation, ownership (404 for anything not owned),
 * role separation (admins get 403), anonymous access and CSRF. All documents are fictional.
 */
class DocumentAccessTests extends IntegrationTestSupport {

	@Value("${medivault.documents.directory}")
	private Path documentsDirectory;

	private static final byte[] PDF = FictionalPdf.bytes("Lipid profile");

	private static MockMultipartFile pdf(String filename) {
		return new MockMultipartFile("file", filename, "application/pdf", PDF);
	}

	private static MockMultipartHttpServletRequestBuilder upload(MedicalRecord record) {
		return multipart("/records/" + record.getId() + "/documents");
	}

	private Set<String> storedFiles() throws IOException {
		Files.createDirectories(documentsDirectory);
		try (Stream<Path> files = Files.list(documentsDirectory)) {
			return files.map(file -> file.getFileName().toString()).collect(Collectors.toSet());
		}
	}

	private ResultActions uploadAs(User patient, MedicalRecord record, MockMultipartFile file) throws Exception {
		return mockMvc.perform(upload(record).file(file).with(csrf()).with(user(principal(patient))));
	}

	private RecordDocument uploadedDocument(User patient, MedicalRecord record) throws Exception {
		uploadAs(patient, record, pdf("fictional-lipid-profile.pdf"))
			.andExpect(redirectedUrl("/records/" + record.getId()))
			.andExpect(flash().attribute("uploadSuccess", true));
		return documentRepository.findAll().get(0);
	}

	private String downloadUrl(MedicalRecord record, RecordDocument document) {
		return "/records/" + record.getId() + "/documents/" + document.getId();
	}

	// --- Owner workflow ---------------------------------------------------------------

	@Test
	void ownerUploadsSeesAndDownloadsTheirPdf() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		Set<String> before = storedFiles();

		RecordDocument document = uploadedDocument(asha, record);

		assertThat(document.getOriginalFilename()).isEqualTo("fictional-lipid-profile.pdf");
		assertThat(document.getContentType()).isEqualTo("application/pdf");
		assertThat(document.getSizeBytes()).isEqualTo(PDF.length);
		assertThat(document.getSha256()).hasSize(64);
		Set<String> added = storedFiles();
		added.removeAll(before);
		assertThat(added).containsExactly(document.getStorageKey());

		mockMvc.perform(get("/records/" + record.getId()).with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("fictional-lipid-profile.pdf"),
					containsString(downloadUrl(record, document)), not(containsString(document.getStorageKey())),
					not(containsString(document.getSha256())))));

		mockMvc.perform(get(downloadUrl(record, document)).with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/pdf"))
			.andExpect(header().string("Content-Disposition", "attachment; filename=\"fictional-lipid-profile.pdf\""))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("Cache-Control", containsString("no-store")))
			.andExpect(header().longValue("Content-Length", PDF.length))
			.andExpect(content().bytes(PDF));
	}

	@Test
	void uploadedFilenamesAreSanitisedAndNeverUsedAsPaths() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		Path outside = documentsDirectory.getParent().resolve("evil.pdf");
		Files.deleteIfExists(outside);

		uploadAs(asha, record, pdf("..\\..\\evil.pdf")).andExpect(flash().attribute("uploadSuccess", true));
		uploadAs(asha, record, pdf("../../evil.pdf")).andExpect(flash().attribute("uploadSuccess", true));
		uploadAs(asha, record, pdf("C:\\Windows\\re\"port\u0007<x>.pdf")).andExpect(flash().attribute("uploadSuccess", true));

		assertThat(documentRepository.findAll()).extracting(RecordDocument::getOriginalFilename)
			.containsExactlyInAnyOrder("evil.pdf", "evil.pdf", "reportx.pdf");
		assertThat(outside).doesNotExist();
		assertThat(documentRepository.findAll()).extracting(RecordDocument::getStorageKey)
			.allMatch(key -> Files.isRegularFile(documentsDirectory.resolve(key)));
	}

	@Test
	void downloadFilenamesAreQuotedSafely() {
		assertThat(DocumentController.contentDisposition("report \"final\".pdf"))
			.isEqualTo("attachment; filename=\"report \\\"final\\\".pdf\"");
		assertThat(DocumentController.contentDisposition("rapport-médical.pdf"))
			.startsWith("attachment; filename=")
			.contains("filename*=UTF-8''rapport-m%C3%A9dical.pdf")
			.doesNotContain("\r")
			.doesNotContain("\n");
	}

	@Test
	void documentsAreListedOnlyOnTheirOwnRecord() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord withDocument = createRecord(asha, "Asha lipid profile");
		MedicalRecord withoutDocument = createRecord(asha, "Asha vaccination");
		uploadedDocument(asha, withDocument);

		mockMvc.perform(get("/records/" + withoutDocument.getId()).with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(not(containsString("fictional-lipid-profile.pdf")),
					containsString("No documents are attached to this record yet."))));
	}

	// --- Validation -------------------------------------------------------------------

	static Stream<Arguments> rejectedUploads() {
		byte[] executable = "MZ\u0090 fictional program %%EOF".getBytes(StandardCharsets.ISO_8859_1);
		byte[] truncated = java.util.Arrays.copyOf(PDF, PDF.length - 10);
		return Stream.of(
				Arguments.of("empty file", new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]),
						Reason.EMPTY),
				Arguments.of("wrong extension", new MockMultipartFile("file", "report.exe", "application/pdf", PDF),
						Reason.NOT_PDF_EXTENSION),
				Arguments.of("no extension", new MockMultipartFile("file", "report", "application/pdf", PDF),
						Reason.NOT_PDF_EXTENSION),
				Arguments.of("double extension", new MockMultipartFile("file", "report.pdf.html", "application/pdf", PDF),
						Reason.NOT_PDF_EXTENSION),
				Arguments.of("declared as text", new MockMultipartFile("file", "report.pdf", "text/plain", PDF),
						Reason.NOT_PDF_TYPE),
				Arguments.of("no declared type", new MockMultipartFile("file", "report.pdf", null, PDF),
						Reason.NOT_PDF_TYPE),
				Arguments.of("invalid declared type", new MockMultipartFile("file", "report.pdf", "pdf", PDF),
						Reason.NOT_PDF_TYPE),
				Arguments.of("MIME spoofing: executable named .pdf",
						new MockMultipartFile("file", "invoice.pdf", "application/pdf", executable), Reason.NOT_PDF_CONTENT),
				Arguments.of("HTML named .pdf", new MockMultipartFile("file", "page.pdf", "application/pdf",
						"<html><script>alert(1)</script></html>".getBytes(StandardCharsets.US_ASCII)),
						Reason.NOT_PDF_CONTENT),
				Arguments.of("truncated PDF", new MockMultipartFile("file", "cut.pdf", "application/pdf", truncated),
						Reason.MALFORMED),
				Arguments.of("over 10 MB", new MockMultipartFile("file", "big.pdf", "application/pdf",
						FictionalPdf.ofSize(10 * 1024 * 1024 + 1)), Reason.TOO_LARGE));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("rejectedUploads")
	void invalidUploadsAreRejectedWithASafeMessageAndStoreNothing(String name, MockMultipartFile file, Reason reason)
			throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		Set<String> before = storedFiles();

		uploadAs(asha, record, file).andExpect(redirectedUrl("/records/" + record.getId()))
			.andExpect(flash().attribute("uploadError", startsWith(reason.message())));

		assertThat(documentRepository.count()).as(name).isZero();
		assertThat(storedFiles()).as(name + ": no file may be left behind").isEqualTo(before);
	}

	@Test
	void theUploadErrorIsShownOnTheRecordPage() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		var result = uploadAs(asha, record, new MockMultipartFile("file", "x.txt", "text/plain", PDF)).andReturn();

		mockMvc.perform(get("/records/" + record.getId()).with(user(principal(asha)))
			.flashAttrs(result.getFlashMap()))
			.andExpect(content().string(containsString(Reason.NOT_PDF_EXTENSION.message())));
	}

	@Test
	void anOversizedUploadNamesTheLimit() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		uploadAs(asha, record, new MockMultipartFile("file", "big.pdf", "application/pdf",
				FictionalPdf.ofSize(10 * 1024 * 1024 + 1)))
			.andExpect(flash().attribute("uploadError", "The file is too large. The limit is 10 MB."));
	}

	@Test
	void exactlyTenMegabytesIsAccepted() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		uploadAs(asha, record, new MockMultipartFile("file", "big.pdf", "application/pdf",
				FictionalPdf.ofSize(10 * 1024 * 1024)))
			.andExpect(flash().attribute("uploadSuccess", true));
	}

	@Test
	void aMissingFilePartIsRejected() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		mockMvc.perform(upload(record).with(csrf()).with(user(principal(asha))))
			.andExpect(flash().attribute("uploadError", Reason.MISSING.message()));
	}

	// --- Ownership ----------------------------------------------------------------------

	@Test
	void anotherPatientCannotUploadToARecordTheyDoNotOwn() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		MedicalRecord ashasRecord = createRecord(asha, "Asha lipid profile");
		Set<String> before = storedFiles();

		uploadAs(ravi, ashasRecord, pdf("planted.pdf")).andExpect(status().isNotFound());
		mockMvc.perform(multipart("/records/987654/documents").file(pdf("x.pdf")).with(csrf()).with(user(principal(ravi))))
			.andExpect(status().isNotFound());

		assertThat(documentRepository.count()).isZero();
		assertThat(storedFiles()).isEqualTo(before);
	}

	@Test
	void anotherPatientCannotDownloadByChangingIds() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		MedicalRecord ashasRecord = createRecord(asha, "Asha lipid profile");
		MedicalRecord ravisRecord = createRecord(ravi, "Ravi own record");
		RecordDocument ashasDocument = uploadedDocument(asha, ashasRecord);

		// Asha's record + Asha's document, requested by Ravi.
		mockMvc.perform(get(downloadUrl(ashasRecord, ashasDocument)).with(user(principal(ravi))))
			.andExpect(status().isNotFound())
			.andExpect(content().string(not(containsString("FICTIONAL SAMPLE"))));
		// Ravi's own record id with Asha's document id.
		mockMvc.perform(get(downloadUrl(ravisRecord, ashasDocument)).with(user(principal(ravi))))
			.andExpect(status().isNotFound());
		// Ravi cannot open Asha's record page either.
		mockMvc.perform(get("/records/" + ashasRecord.getId()).with(user(principal(ravi))))
			.andExpect(status().isNotFound())
			.andExpect(content().string(not(containsString("fictional-lipid-profile.pdf"))));
	}

	@Test
	void aDocumentIsOnlyReachableThroughItsOwnRecord() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord first = createRecord(asha, "Asha lipid profile");
		MedicalRecord second = createRecord(asha, "Asha vaccination");
		RecordDocument document = uploadedDocument(asha, first);

		mockMvc.perform(get(downloadUrl(second, document)).with(user(principal(asha)))).andExpect(status().isNotFound());
		mockMvc.perform(get("/records/" + first.getId() + "/documents/987654").with(user(principal(asha))))
			.andExpect(status().isNotFound());
	}

	@Test
	void storageKeysAreNotUsableAsUrls() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument document = uploadedDocument(asha, record);
		String key = document.getStorageKey();

		for (String path : List.of("/" + key, "/records/" + key, "/records/" + record.getId() + "/documents/" + key,
				"/records/" + key + ".pdf", "/css/" + key, "/images/../" + key)) {
			mockMvc.perform(get(path).with(user(principal(asha))))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).as(path).isGreaterThanOrEqualTo(400))
				.andExpect(content().string(not(containsString("%PDF"))));
		}
	}

	// --- Roles, anonymous users and CSRF -------------------------------------------------

	@Test
	void adminsCannotUploadOrDownloadPatientDocuments() throws Exception {
		User admin = createAdmin("Admin Fictional", "admin@example.test");
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument document = uploadedDocument(asha, record);

		mockMvc.perform(get(downloadUrl(record, document)).with(user(principal(admin))))
			.andExpect(status().isForbidden())
			.andExpect(content().string(not(containsString("FICTIONAL SAMPLE"))));
		uploadAs(admin, record, pdf("admin.pdf")).andExpect(status().isForbidden());
		mockMvc.perform(get("/admin").with(user(principal(admin))))
			.andExpect(content().string(allOf(not(containsString("fictional-lipid-profile.pdf")),
					not(containsString(document.getSha256())))));
		assertThat(documentRepository.count()).isEqualTo(1);
	}

	@Test
	void anonymousUsersCannotUploadOrDownload() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument document = uploadedDocument(asha, record);

		mockMvc.perform(get(downloadUrl(record, document)))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("http://localhost/login"));
		mockMvc.perform(upload(record).file(pdf("anon.pdf")).with(csrf()))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("http://localhost/login"));
		mockMvc.perform(upload(record).file(pdf("anon.pdf"))).andExpect(status().isForbidden());
		assertThat(documentRepository.count()).isEqualTo(1);
	}

	@Test
	void uploadsWithoutAValidCsrfTokenAreRefused() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		Set<String> before = storedFiles();

		mockMvc.perform(upload(record).file(pdf("x.pdf")).with(user(principal(asha)))).andExpect(status().isForbidden());
		mockMvc.perform(upload(record).file(pdf("x.pdf")).with(csrf().useInvalidToken()).with(user(principal(asha))))
			.andExpect(status().isForbidden());

		assertThat(documentRepository.count()).isZero();
		assertThat(storedFiles()).isEqualTo(before);
	}

	@Test
	void theUploadFormCarriesACsrfToken() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		mockMvc.perform(get("/records/" + record.getId()).with(user(principal(asha))))
			.andExpect(content().string(allOf(containsString("enctype=\"multipart/form-data\""),
					containsString("name=\"_csrf\""))));
	}

}
