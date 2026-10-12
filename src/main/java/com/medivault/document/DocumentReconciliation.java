package com.medivault.document;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only consistency check between document rows and stored files, run once at startup
 * ({@code medivault.documents.reconcile-on-startup}, on by default). It only counts and logs:
 * <ul>
 * <li><b>unreferenced files</b>: a stored file without a row, left by a crash between an upload's
 * file write and its commit, or by a file that could not be removed after a deletion. They are never
 * served. MediVault never deletes them automatically; the README describes how to review them.</li>
 * <li><b>missing files</b>: a row whose file is gone. Downloading that document fails with a generic
 * error page. This should not happen; restore the file from a backup.</li>
 * </ul>
 * Log lines contain counts only, never storage keys, filenames or paths.
 */
@Component
public class DocumentReconciliation implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DocumentReconciliation.class);

	/** The outcome of one check. */
	public record Report(int storedFiles, int documentRows, int unreferencedFiles, int missingFiles) {

		public boolean consistent() {
			return unreferencedFiles == 0 && missingFiles == 0;
		}

	}

	private final RecordDocumentRepository documentRepository;

	private final DocumentStorage storage;

	private final boolean runOnStartup;

	public DocumentReconciliation(RecordDocumentRepository documentRepository, DocumentStorage storage,
			@Value("${medivault.documents.reconcile-on-startup:true}") boolean runOnStartup) {
		this.documentRepository = documentRepository;
		this.storage = storage;
		this.runOnStartup = runOnStartup;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (runOnStartup) {
			checkAndLog();
		}
	}

	/** Runs {@link #check()} and logs the counts; a failure to run is logged, never thrown. */
	public void checkAndLog() {
		try {
			Report report = check();
			if (report.consistent()) {
				log.info("Document storage check: {} document(s), every row has its file and every file its row.",
						report.documentRows());
			}
			else {
				log.warn("Document storage check: {} unreferenced file(s) (never served; see README, "
						+ "\"Document storage check\") and {} document row(s) whose file is missing.",
						report.unreferencedFiles(), report.missingFiles());
			}
		}
		catch (RuntimeException ex) {
			log.warn("Document storage check could not run ({}).", ex.getClass().getSimpleName());
		}
	}

	@Transactional(readOnly = true)
	public Report check() {
		Set<String> files = storage.listKeys();
		Set<String> rows = new HashSet<>(documentRepository.findAllStorageKeys());
		Set<String> unreferenced = new HashSet<>(files);
		unreferenced.removeAll(rows);
		Set<String> missing = new HashSet<>(rows);
		missing.removeAll(files);
		return new Report(files.size(), rows.size(), unreferenced.size(), missing.size());
	}

}
