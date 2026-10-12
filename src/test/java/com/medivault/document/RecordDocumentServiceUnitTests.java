package com.medivault.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;

/** Unit-level rules of {@link RecordDocumentService} that need no Spring context. */
class RecordDocumentServiceUnitTests {

	@TempDir
	Path tempDir;

	@Test
	void displayNamesDropPathSegmentsAndControlCharacters() {
		assertThat(RecordDocumentService.displayFilename("..\\..\\evil.pdf")).isEqualTo("evil.pdf");
		assertThat(RecordDocumentService.displayFilename("a/b/c.pdf")).isEqualTo("c.pdf");
		assertThat(RecordDocumentService.displayFilename("re\"port\u0007<x>.pdf")).isEqualTo("reportx.pdf");
		assertThat(RecordDocumentService.displayFilename(null)).isEmpty();
	}

	/**
	 * Regression (M4 audit): bidirectional overrides and invisible characters could make a document
	 * appear under a misleading name on the record page and in the saved download.
	 */
	@Test
	void displayNamesDropInvisibleAndDirectionalFormattingCharacters() {
		assertThat(RecordDocumentService.displayFilename("invoice‮exe.pdf")).isEqualTo("invoiceexe.pdf");
		assertThat(RecordDocumentService.displayFilename("lab​‌‍⁠﻿report.pdf"))
			.isEqualTo("labreport.pdf");
		assertThat(RecordDocumentService.displayFilename("⁦scan⁩.pdf")).isEqualTo("scan.pdf");
		// Ordinary non-ASCII names are kept.
		assertThat(RecordDocumentService.displayFilename("rapport-médical-血液.pdf")).isEqualTo("rapport-médical-血液.pdf");
	}

	@Test
	void longNamesKeepTheirExtensionAndNeverSplitACharacter() {
		String longName = "😀".repeat(200) + ".pdf";
		String display = RecordDocumentService.displayFilename(longName);
		assertThat(display).hasSizeLessThanOrEqualTo(RecordDocumentService.MAX_FILENAME_LENGTH).endsWith(".pdf");
		// A paired surrogate becomes one supplementary code point; only a split pair stays a lone surrogate.
		assertThat(display.codePoints().noneMatch(cp -> cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE))
			.isTrue();
	}

	/**
	 * Regression (M4 audit): when the commit outcome is unknown the row may exist, so the file must
	 * be kept (an unreferenced file is harmless; a row without its file is a broken document).
	 */
	@Test
	void theUploadedFileIsDeletedOnlyAfterADefiniteRollback() {
		DocumentStorage storage = new DocumentStorage(tempDir.resolve("records"), 1024 * 1024, UUID::randomUUID,
				com.medivault.document.crypto.TestKeys.newCipher());
		String unknown = storage.store(new ByteArrayInputStream(FictionalPdf.bytes("Unknown outcome"))).key();
		String committed = storage.store(new ByteArrayInputStream(FictionalPdf.bytes("Committed"))).key();
		String rolledBack = storage.store(new ByteArrayInputStream(FictionalPdf.bytes("Rolled back"))).key();

		new RecordDocumentService.DeleteUnlessCommitted(storage, unknown)
			.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
		new RecordDocumentService.DeleteUnlessCommitted(storage, committed)
			.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
		new RecordDocumentService.DeleteUnlessCommitted(storage, rolledBack)
			.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

		assertThat(storage.exists(unknown)).isTrue();
		assertThat(storage.exists(committed)).isTrue();
		assertThat(storage.exists(rolledBack)).isFalse();
	}

}
