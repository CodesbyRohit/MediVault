package com.medivault.document.crypto;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;

/**
 * Offline management of the document key file, run before Spring starts:
 *
 * <pre>
 * java -jar medivault.jar document-key generate &lt;absolute path&gt;   create a new key file (never overwrites)
 * java -jar medivault.jar document-key rotate   &lt;absolute path&gt;   add a new primary key, keep the old ones
 * java -jar medivault.jar document-key inspect  &lt;absolute path&gt;   list key ids and status (no key material)
 * </pre>
 *
 * Key files are written owner-only. The tool prints key ids only, never key material. It is the only
 * code that creates keys: MediVault itself never generates a key, not even when the configured one
 * is missing.
 */
public final class DocumentKeyTool {

	public static final String COMMAND = "document-key";

	private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final PrintStream out;

	private final PrintStream err;

	DocumentKeyTool(PrintStream out, PrintStream err) {
		this.out = out;
		this.err = err;
	}

	/** @return the process exit code */
	public static int run(String[] args) {
		return new DocumentKeyTool(System.out, System.err).execute(args);
	}

	int execute(String[] args) {
		if (args.length != 2) {
			return usage();
		}
		Path file = Path.of(args[1]);
		if (!file.isAbsolute()) {
			err.println("The key file path must be absolute.");
			return 2;
		}
		try {
			return switch (args[0]) {
				case "generate" -> generate(file.normalize());
				case "rotate" -> rotate(file.normalize());
				case "inspect" -> inspect(file.normalize());
				default -> usage();
			};
		}
		catch (DocumentKeyException ex) {
			err.println(ex.getMessage());
			return 1;
		}
		catch (IOException | GeneralSecurityException ex) {
			err.println("The key file could not be written or read (" + ex.getClass().getSimpleName() + ").");
			return 1;
		}
	}

	private int usage() {
		err.println("Usage: " + COMMAND + " generate|rotate|inspect <absolute path of the key file>");
		return 2;
	}

	/** Creates a new key file with one fresh primary key. Refuses to replace an existing file. */
	int generate(Path file) throws IOException, GeneralSecurityException {
		KeysetHandle keyset = KeysetHandle.generateNew(DocumentKeyRing.PARAMETERS);
		DocumentKeyRing ring = DocumentKeyRing.fromKeyset(keyset);
		try {
			writeOwnerOnly(file, keyset);
		}
		catch (FileAlreadyExistsException ex) {
			err.println("A file already exists at that path; nothing was changed. MediVault never overwrites a key file.");
			return 1;
		}
		out.println("Created a document key file with primary key id " + Integer.toUnsignedString(ring.primaryKeyId())
				+ ".");
		out.println("Back it up now, separately from your data backups: without it, encrypted PDFs cannot be read.");
		return 0;
	}

	/**
	 * Adds a new primary key and keeps every existing key, so existing files can still be decrypted.
	 * The previous file is kept as {@code <name>.bak-<timestamp>} (owner-only) before the new one
	 * atomically replaces it.
	 */
	int rotate(Path file) throws IOException, GeneralSecurityException {
		DocumentKeyRing.load(file.toString(), null);
		KeysetHandle current = DocumentKeyRing.parse(Files.readAllBytes(file));
		KeysetHandle.Builder builder = KeysetHandle.newBuilder(current);
		builder.addEntry(KeysetHandle.generateEntryFromParameters(DocumentKeyRing.PARAMETERS).withRandomId().makePrimary());
		KeysetHandle rotated = builder.build();
		DocumentKeyRing ring = DocumentKeyRing.fromKeyset(rotated);

		Path backup = file.resolveSibling(file.getFileName() + ".bak-" + LocalDateTime.now().format(BACKUP_STAMP));
		writeOwnerOnly(backup, current);
		Path next = file.resolveSibling(file.getFileName() + ".new");
		Files.deleteIfExists(next);
		writeOwnerOnly(next, rotated);
		Files.move(next, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		out.println("Added primary key id " + Integer.toUnsignedString(ring.primaryKeyId()) + "; the file now holds "
				+ ring.keyCount() + " keys. The previous file was kept as " + backup.getFileName() + ".");
		out.println("New uploads use the new key. Existing documents stay readable with the older keys.");
		return 0;
	}

	int inspect(Path file) {
		DocumentKeyRing.load(file.toString(), null);
		KeysetHandle keyset;
		try {
			keyset = DocumentKeyRing.parse(Files.readAllBytes(file));
		}
		catch (IOException ex) {
			throw new DocumentKeyException(DocumentKeyException.Problem.NOT_FOUND);
		}
		out.println("Document key file: " + keyset.size() + " key(s), all AES256_GCM_HKDF_1MB.");
		for (int i = 0; i < keyset.size(); i++) {
			KeysetHandle.Entry entry = keyset.getAt(i);
			out.println("  key id " + Integer.toUnsignedString(entry.getId()) + "  " + entry.getStatus()
					+ (entry.isPrimary() ? "  (primary: used for new uploads)" : ""));
		}
		return 0;
	}

	/** Creates {@code file} (never replacing one), restricts it to the owner, then writes the keyset. */
	static void writeOwnerOnly(Path file, KeysetHandle keyset) throws IOException, GeneralSecurityException {
		byte[] json = TinkJsonProtoKeysetFormat.serializeKeyset(keyset, InsecureSecretKeyAccess.get())
			.getBytes(StandardCharsets.UTF_8);
		Files.createFile(file);
		try {
			KeyFilePermissions.restrictToOwner(file);
			try (OutputStream stream = Files.newOutputStream(file, StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS, StandardOpenOption.SYNC)) {
				stream.write(json);
			}
		}
		catch (IOException | RuntimeException ex) {
			Files.deleteIfExists(file);
			throw ex;
		}
		finally {
			java.util.Arrays.fill(json, (byte) 0);
		}
	}

}
