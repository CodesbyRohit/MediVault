package com.medivault.document.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.Parameters;

/**
 * Throwaway document keys for tests. Keys are generated with the same code path as the key tool and
 * written only to temporary folders or {@code target/test-keys}; none is ever committed.
 */
public final class TestKeys {

	/** The key file every test JVM uses (see {@code TestKeyFileListener}); under {@code target/}, never committed. */
	public static final Path SHARED_KEY_FILE = Path.of(System.getProperty("user.dir"), "target", "test-keys",
			"test-document-key.json").toAbsolutePath();

	private TestKeys() {
	}

	/** A fresh in-memory cipher with one new key. */
	public static DocumentCipher newCipher() {
		return new DocumentCipher(newKeyRing());
	}

	public static DocumentKeyRing newKeyRing() {
		try {
			return DocumentKeyRing.fromKeyset(KeysetHandle.generateNew(DocumentKeyRing.PARAMETERS));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Writes a new owner-only key file at {@code file} (which must not exist) and returns it. */
	public static Path writeKeyFile(Path file) {
		return writeKeyFile(file, DocumentKeyRing.PARAMETERS);
	}

	static Path writeKeyFile(Path file, Parameters parameters) {
		try {
			Files.createDirectories(file.getParent());
			DocumentKeyTool.writeOwnerOnly(file, KeysetHandle.generateNew(parameters));
			return file;
		}
		catch (IOException | GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** The shared key file for the whole test run, created once if it does not exist yet. */
	public static synchronized Path sharedKeyFile() {
		if (!Files.exists(SHARED_KEY_FILE)) {
			writeKeyFile(SHARED_KEY_FILE);
		}
		return SHARED_KEY_FILE;
	}

	/** The cipher for {@link #sharedKeyFile()}, as the application loads it. */
	public static DocumentCipher sharedCipher() {
		return new DocumentCipher(DocumentKeyRing.load(sharedKeyFile().toString(), null));
	}

}
