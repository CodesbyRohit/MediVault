package com.medivault.document.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeyStatus;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.StreamingAead;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingKey;
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters;
import com.google.crypto.tink.streamingaead.StreamingAeadConfig;

import com.medivault.document.crypto.DocumentKeyException.Problem;

/**
 * The validated document keys, loaded once from the key file named by
 * {@code MEDIVAULT_DOCUMENT_KEY_FILE}. The file must be a Tink JSON keyset (written by
 * {@link DocumentKeyTool}) in which every key is an {@code AES256_GCM_HKDF_1MB} streaming key; a raw
 * AES key or any other format is refused. Loading fails closed with a {@link DocumentKeyException}
 * whose message never contains the path or key material.
 * <p>
 * The key material stays inside Tink's objects: this class never exposes the keyset, and its
 * {@code toString()} names only key ids.
 */
public final class DocumentKeyRing {

	/** The only accepted key parameters: 32-byte keys, HKDF-SHA256, 32-byte derived keys, 1 MiB segments. */
	static final com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingParameters PARAMETERS =
			PredefinedStreamingAeadParameters.AES256_GCM_HKDF_1MB;

	static final int MAX_FILE_BYTES = 64 * 1024;

	private static final byte[] SELF_TEST_PLAINTEXT = "MediVault document key self-test (fictional)"
		.getBytes(StandardCharsets.US_ASCII);

	private static final byte[] SELF_TEST_AAD = "medivault-key-self-test".getBytes(StandardCharsets.US_ASCII);

	static {
		try {
			StreamingAeadConfig.register();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Tink streaming AEAD could not be registered", ex);
		}
	}

	private final int primaryKeyId;

	/** One primitive per enabled key, so each file is decrypted with exactly the key its header names. */
	private final Map<Integer, StreamingAead> enabledKeys;

	private DocumentKeyRing(int primaryKeyId, Map<Integer, StreamingAead> enabledKeys) {
		this.primaryKeyId = primaryKeyId;
		this.enabledKeys = Collections.unmodifiableMap(enabledKeys);
	}

	/**
	 * Loads and validates the key file.
	 * @param configuredPath the value of {@code MEDIVAULT_DOCUMENT_KEY_FILE} (may be {@code null} or blank)
	 * @param storageRoot the folder that holds MediVault's data; the key file must not be inside it
	 * @throws DocumentKeyException if the file is missing, unsafe or not a valid document keyset
	 */
	public static DocumentKeyRing load(String configuredPath, Path storageRoot) {
		if (configuredPath == null || configuredPath.isBlank()) {
			throw new DocumentKeyException(Problem.NOT_CONFIGURED);
		}
		Path file;
		try {
			file = Path.of(configuredPath.strip());
		}
		catch (InvalidPathException ex) {
			throw new DocumentKeyException(Problem.NOT_FOUND);
		}
		if (!file.isAbsolute()) {
			throw new DocumentKeyException(Problem.NOT_ABSOLUTE);
		}
		file = file.normalize();
		BasicFileAttributes attributes;
		try {
			attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		}
		catch (IOException | SecurityException ex) {
			throw new DocumentKeyException(Problem.NOT_FOUND);
		}
		if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.isOther()) {
			throw new DocumentKeyException(Problem.NOT_REGULAR_FILE);
		}
		if (attributes.size() > MAX_FILE_BYTES) {
			throw new DocumentKeyException(Problem.TOO_LARGE);
		}
		if (isInside(file, storageRoot)) {
			throw new DocumentKeyException(Problem.INSIDE_STORAGE);
		}
		try {
			if (!KeyFilePermissions.isOwnerOnly(file)) {
				throw new DocumentKeyException(Problem.TOO_PERMISSIVE);
			}
		}
		catch (IOException | SecurityException ex) {
			throw new DocumentKeyException(Problem.NOT_FOUND);
		}
		byte[] json = read(file);
		try {
			return fromKeyset(parse(json));
		}
		finally {
			Arrays.fill(json, (byte) 0);
		}
	}

	/** Validates an already parsed keyset (used by the key tool and by tests). */
	static DocumentKeyRing fromKeyset(KeysetHandle handle) {
		if (handle.size() == 0) {
			throw new DocumentKeyException(Problem.NO_ENABLED_PRIMARY);
		}
		KeysetHandle.Entry primary;
		try {
			primary = handle.getPrimary();
		}
		catch (IllegalStateException ex) {
			throw new DocumentKeyException(Problem.NO_ENABLED_PRIMARY);
		}
		if (primary.getStatus() != KeyStatus.ENABLED) {
			throw new DocumentKeyException(Problem.NO_ENABLED_PRIMARY);
		}
		Map<Integer, StreamingAead> enabled = new LinkedHashMap<>();
		for (int i = 0; i < handle.size(); i++) {
			KeysetHandle.Entry entry = handle.getAt(i);
			if (!(entry.getKey() instanceof AesGcmHkdfStreamingKey key) || !PARAMETERS.equals(key.getParameters())) {
				throw new DocumentKeyException(Problem.UNSUPPORTED_KEY);
			}
			if (entry.getStatus() == KeyStatus.ENABLED) {
				enabled.put(entry.getId(), selfTested(primitiveFor(entry)));
			}
		}
		return new DocumentKeyRing(primary.getId(), enabled);
	}

	static KeysetHandle parse(byte[] json) {
		try {
			return TinkJsonProtoKeysetFormat.parseKeyset(new String(json, StandardCharsets.UTF_8),
					InsecureSecretKeyAccess.get());
		}
		catch (GeneralSecurityException | RuntimeException ex) {
			// Never chain or repeat the parser's message: it can quote parts of the file.
			throw new DocumentKeyException(Problem.INVALID_KEYSET);
		}
	}

	public int primaryKeyId() {
		return primaryKeyId;
	}

	public int keyCount() {
		return enabledKeys.size();
	}

	public boolean hasKey(int keyId) {
		return enabledKeys.containsKey(keyId);
	}

	/** The ids of the enabled keys (never key material). */
	public java.util.Set<Integer> enabledKeyIds() {
		return enabledKeys.keySet();
	}

	StreamingAead primary() {
		return enabledKeys.get(primaryKeyId);
	}

	/** @return the primitive for {@code keyId}, or {@code null} if that key is not in the file or not enabled */
	StreamingAead key(int keyId) {
		return enabledKeys.get(keyId);
	}

	@Override
	public String toString() {
		return "DocumentKeyRing[primaryKeyId=" + Integer.toUnsignedString(primaryKeyId) + ", enabledKeys="
				+ enabledKeys.size() + "]";
	}

	private static StreamingAead primitiveFor(KeysetHandle.Entry entry) {
		try {
			return KeysetHandle.newBuilder()
				.addEntry(KeysetHandle.importKey(entry.getKey()).withFixedId(entry.getId()).makePrimary())
				.build()
				.getPrimitive(RegistryConfiguration.get(), StreamingAead.class);
		}
		catch (GeneralSecurityException ex) {
			throw new DocumentKeyException(Problem.UNSUPPORTED_KEY);
		}
	}

	private static StreamingAead selfTested(StreamingAead key) {
		try {
			var ciphertext = new java.io.ByteArrayOutputStream();
			try (var out = key.newEncryptingStream(ciphertext, SELF_TEST_AAD)) {
				out.write(SELF_TEST_PLAINTEXT);
			}
			try (InputStream in = key.newDecryptingStream(new java.io.ByteArrayInputStream(ciphertext.toByteArray()),
					SELF_TEST_AAD)) {
				if (!Arrays.equals(in.readAllBytes(), SELF_TEST_PLAINTEXT)) {
					throw new DocumentKeyException(Problem.SELF_TEST_FAILED);
				}
			}
			return key;
		}
		catch (GeneralSecurityException | IOException ex) {
			throw new DocumentKeyException(Problem.SELF_TEST_FAILED);
		}
	}

	private static byte[] read(Path file) {
		try (InputStream in = Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
			byte[] json = in.readNBytes(MAX_FILE_BYTES + 1);
			if (json.length > MAX_FILE_BYTES) {
				throw new DocumentKeyException(Problem.TOO_LARGE);
			}
			return json;
		}
		catch (NoSuchFileException ex) {
			throw new DocumentKeyException(Problem.NOT_FOUND);
		}
		catch (IOException | SecurityException ex) {
			throw new DocumentKeyException(Problem.NOT_FOUND);
		}
	}

	private static boolean isInside(Path file, Path storageRoot) {
		if (storageRoot == null) {
			return false;
		}
		Path root = storageRoot.toAbsolutePath().normalize();
		if (file.startsWith(root)) {
			return true;
		}
		// Also compare real paths, so a link or junction on the way cannot hide the containment.
		try {
			return Files.exists(root) && file.toRealPath().startsWith(root.toRealPath());
		}
		catch (IOException ex) {
			return false;
		}
	}

}
