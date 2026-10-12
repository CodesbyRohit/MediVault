package com.medivault.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import com.medivault.IntegrationTestSupport;
import com.medivault.document.crypto.DocumentCipher;
import com.medivault.record.MedicalRecord;
import com.medivault.user.User;

/**
 * Encrypted storage and download integrity through the web layer. Every upload is stored encrypted;
 * every download is verified completely before a byte is sent; damaged, tampered, swapped or
 * substituted files are never served and the error page reveals nothing. All data is fictional.
 */
@ExtendWith(OutputCaptureExtension.class)
class DocumentEncryptionTests extends IntegrationTestSupport {

	private static final byte[] PDF = FictionalPdf.bytes("Fictional lipid profile ZZCONFIDENTIAL");

	@MockitoSpyBean
	private DocumentStorage storage;

	@Autowired
	private DocumentCipher cipher;

	@Autowired
	private JdbcTemplate jdbc;

	@Value("${medivault.documents.directory}")
	private Path directory;

	@AfterEach
	void resetSpy() {
		reset(storage);
	}

	private RecordDocument upload(User owner, MedicalRecord record, String name, byte[] pdf) throws Exception {
		mockMvc.perform(multipart("/records/" + record.getId() + "/documents")
			.file(new MockMultipartFile("file", name, "application/pdf", pdf))
			.with(csrf())
			.with(user(principal(owner)))).andExpect(flash().attribute("uploadSuccess", true));
		return documentRepository.findAll()
			.stream()
			.filter(d -> d.getOriginalFilename().equals(name))
			.findFirst()
			.orElseThrow();
	}

	private ResultActions download(User owner, MedicalRecord record, RecordDocument document) throws Exception {
		return mockMvc.perform(get("/records/" + record.getId() + "/documents/" + document.getId())
			.with(user(principal(owner))));
	}

	private Path fileOf(RecordDocument document) {
		return directory.resolve(document.getStorageKey());
	}

	private static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	/** A download that must fail: generic 500, nothing of the document, no internals. */
	private void assertRefusedSafely(ResultActions result, RecordDocument document) throws Exception {
		result.andExpect(status().isInternalServerError())
			.andExpect(content().string(allOf(not(containsString("ZZCONFIDENTIAL")), not(containsString("%PDF")),
					not(containsString(document.getStorageKey())), not(containsString("test-storage")),
					not(containsString("Exception")), not(containsString("MVDOC")), not(containsString("tag")))));
	}

	@Test
	void uploadsAreStoredEncryptedAndDownloadExactly() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument document = upload(asha, record, "fictional-lipid.pdf", PDF);

		assertThat(document.getStorageFormat()).isEqualTo(StorageFormat.ENCRYPTED_V1);
		assertThat(document.getKeyId()).isEqualTo((long) cipher.primaryKeyId());
		assertThat(document.getSizeBytes()).as("size describes the plaintext").isEqualTo(PDF.length);
		assertThat(document.getSha256()).as("checksum describes the plaintext").isEqualTo(sha256(PDF));
		String raw = new String(Files.readAllBytes(fileOf(document)), StandardCharsets.ISO_8859_1);
		assertThat(raw).startsWith("MVDOC").doesNotContain("%PDF").doesNotContain("ZZCONFIDENTIAL");
		try (var parts = Files.list(directory)) {
			assertThat(parts.map(p -> p.getFileName().toString())).noneMatch(name -> name.endsWith(".part"));
		}

		download(asha, record, document).andExpect(status().isOk()).andExpect(content().bytes(PDF));
	}

	@Test
	void aFlippedByteMakesTheDownloadFailWithoutLeakingAnything(CapturedOutput output) throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument document = upload(asha, record, "fictional-lipid.pdf", PDF);
		byte[] stored = Files.readAllBytes(fileOf(document));
		stored[stored.length - 40] ^= 0x01;
		Files.write(fileOf(document), stored);

		assertRefusedSafely(download(asha, record, document), document);
		assertThat(output).contains("Document integrity check failed: document=" + document.getId()
				+ " reason=AUTHENTICATION_FAILED")
			.doesNotContain(document.getStorageKey())
			.doesNotContain(directory.toString());
		// The record page still works.
		mockMvc.perform(get("/records/" + record.getId()).with(user(principal(asha)))).andExpect(status().isOk());
	}

	@Test
	void aTruncatedFileIsNeverServed() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument document = upload(asha, record, "fictional-lipid.pdf", PDF);
		byte[] stored = Files.readAllBytes(fileOf(document));
		Files.write(fileOf(document), java.util.Arrays.copyOf(stored, stored.length - 1));

		assertRefusedSafely(download(asha, record, document), document);
	}

	@Test
	void anotherDocumentsFileSwappedInIsNeverServed(CapturedOutput output) throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument target = upload(asha, record, "fictional-target.pdf", PDF);
		RecordDocument other = upload(asha, record, "fictional-other.pdf", FictionalPdf.bytes("Other ZZCONFIDENTIAL"));
		// A valid ciphertext under the same key, but bound to another storage key.
		Files.copy(fileOf(other), fileOf(target), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

		assertRefusedSafely(download(asha, record, target), target);
		assertThat(output).contains("reason=AUTHENTICATION_FAILED");
	}

	@Test
	void plaintextSubstitutedForAnEncryptedFileIsNeverServed(CapturedOutput output) throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument document = upload(asha, record, "fictional-lipid.pdf", PDF);
		// Even the exact original PDF is refused: the row says the file is encrypted.
		Files.write(fileOf(document), PDF);

		assertRefusedSafely(download(asha, record, document), document);
		assertThat(output).contains("reason=FORMAT_MISMATCH");
	}

	@Test
	void legacyPlaintextDocumentsStayReadableAndAreVerifiedToo(CapturedOutput output) throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument document = legacyDocument(record, PDF);

		download(asha, record, document).andExpect(status().isOk()).andExpect(content().bytes(PDF));

		// A changed legacy file fails the checksum and is not served.
		byte[] changed = PDF.clone();
		changed[20] ^= 0x01;
		Files.write(fileOf(document), changed);
		assertRefusedSafely(download(asha, record, document), document);
		assertThat(output).contains("reason=CHECKSUM_MISMATCH");
	}

	@Test
	void aLegacyRowWhoseFileIsEncryptedIsAFormatMismatch(CapturedOutput output) throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument encrypted = upload(asha, record, "fictional-lipid.pdf", PDF);
		jdbc.update("update record_documents set storage_format = 'PLAIN_V0', key_id = null where id = ?",
				encrypted.getId());

		assertRefusedSafely(download(asha, record, encrypted), encrypted);
		assertThat(output).contains("reason=FORMAT_MISMATCH");
	}

	@Test
	void aSizeOrChecksumThatDisagreesWithTheRowIsRefused(CapturedOutput output) throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument bySize = upload(asha, record, "fictional-size.pdf", PDF);
		RecordDocument byChecksum = upload(asha, record, "fictional-checksum.pdf", PDF);
		jdbc.update("update record_documents set size_bytes = size_bytes + 1 where id = ?", bySize.getId());
		jdbc.update("update record_documents set sha256 = ? where id = ?", "0".repeat(64), byChecksum.getId());

		assertRefusedSafely(download(asha, record, bySize), bySize);
		assertRefusedSafely(download(asha, record, byChecksum), byChecksum);
		assertThat(output).contains("reason=SIZE_MISMATCH").contains("reason=CHECKSUM_MISMATCH");
	}

	@Test
	void aFileChangedBetweenVerificationAndStreamingAbortsTheDownload() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument document = upload(asha, record, "fictional-lipid.pdf", PDF);
		byte[] tampered = Files.readAllBytes(fileOf(document));
		tampered[tampered.length - 40] ^= 0x01;
		AtomicInteger opens = new AtomicInteger();
		// Pass one sees the genuine file; the streamed pass gets a tampered copy.
		doAnswer(invocation -> opens.incrementAndGet() == 1 ? invocation.callRealMethod()
				: new ByteArrayInputStream(tampered)).when(storage).open(anyString());

		// The streamed pass fails on the first tampered segment; the response is aborted, never completed.
		assertThatThrownBy(() -> download(asha, record, document)).isInstanceOf(java.io.IOException.class)
			.hasMessage("A stored document failed verification while it was being read.");
		assertThat(opens).hasValue(2);
	}

	@Test
	void anotherPatientStillGets404ForAnEncryptedDocument() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument document = upload(asha, record, "fictional-lipid.pdf", PDF);

		download(ravi, record, document).andExpect(status().isNotFound())
			.andExpect(content().string(not(containsString("ZZCONFIDENTIAL"))));
	}

	/** A row and file exactly as written before encryption existed (V4 marks such rows PLAIN_V0). */
	private RecordDocument legacyDocument(MedicalRecord record, byte[] pdf) throws Exception {
		String key = java.util.UUID.randomUUID().toString();
		Files.write(directory.resolve(key), pdf);
		jdbc.update("insert into record_documents (record_id, original_filename, storage_key, content_type, size_bytes, "
				+ "sha256, uploaded_at, storage_format) values (?, ?, ?, 'application/pdf', ?, ?, ?, 'PLAIN_V0')",
				record.getId(), "fictional-legacy.pdf", key, pdf.length, sha256(pdf),
				java.sql.Timestamp.from(java.time.Instant.now()));
		return documentRepository.findAll()
			.stream()
			.filter(d -> d.getStorageKey().equals(key))
			.findFirst()
			.orElseThrow();
	}

	@Test
	void verifiedStreamsAreReadOnlyAfterTheWholeFileAuthenticated() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		RecordDocument document = upload(asha, record, "fictional-lipid.pdf", PDF);
		byte[] stored = Files.readAllBytes(fileOf(document));
		stored[stored.length - 1] ^= 0x01;
		Files.write(fileOf(document), stored);

		assertThatThrownBy(() -> storage.openVerified(document.getStorageFormat(), document.getStorageKey(),
				document.getSizeBytes(), document.getSha256()))
			// openVerified throws instead of returning a stream, so no plaintext is ever handed out.
			.isInstanceOfSatisfying(DocumentIntegrityException.class,
					ex -> assertThat(ex.reason()).isEqualTo(DocumentIntegrityException.Reason.AUTHENTICATION_FAILED));
	}

}
