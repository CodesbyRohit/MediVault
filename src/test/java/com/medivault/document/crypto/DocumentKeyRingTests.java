package com.medivault.document.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters;

import com.medivault.TestLinks;
import com.medivault.document.crypto.DocumentKeyException.Problem;

/**
 * Loading the key file named by {@code MEDIVAULT_DOCUMENT_KEY_FILE}: every unsafe or invalid file is
 * refused with a fixed message that never contains the path, and no key is ever generated.
 */
class DocumentKeyRingTests {

	@TempDir
	Path tempDir;

	private Path storageRoot() {
		return tempDir.resolve("storage");
	}

	private Path keyFile() {
		return TestKeys.writeKeyFile(tempDir.resolve("keys").resolve("document-key.json"));
	}

	private void assertRefused(String configured, Problem problem) {
		assertThatThrownBy(() -> DocumentKeyRing.load(configured, storageRoot()))
			.isInstanceOfSatisfying(DocumentKeyException.class, ex -> assertThat(ex.problem()).isEqualTo(problem))
			.hasMessageStartingWith("Refusing to start: ")
			.hasNoCause()
			.message()
			.doesNotContain(tempDir.toString())
			.doesNotContain("document-key.json");
	}

	/** Writes {@code content} owner-only, so only the content (not the permissions) is under test. */
	private Path ownerOnlyFile(String name, byte[] content) throws Exception {
		Path file = Files.createDirectories(tempDir.resolve("keys")).resolve(name);
		Files.createFile(file);
		KeyFilePermissions.restrictToOwner(file);
		Files.write(file, content);
		return file;
	}

	@Test
	void aValidKeyFileLoadsAndNamesOnlyKeyIds() {
		Path file = keyFile();
		DocumentKeyRing ring = DocumentKeyRing.load(file.toString(), storageRoot());

		assertThat(ring.keyCount()).isEqualTo(1);
		assertThat(ring.hasKey(ring.primaryKeyId())).isTrue();
		assertThat(ring.toString()).contains(Integer.toUnsignedString(ring.primaryKeyId())).doesNotContain("value");
	}

	@Test
	void theKeyFileIsTinkJsonWithOneStreamingKey() throws Exception {
		String json = Files.readString(keyFile());
		assertThat(json).contains("\"primaryKeyId\"")
			.contains("type.googleapis.com/google.crypto.tink.AesGcmHkdfStreamingKey")
			.contains("\"ENABLED\"");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void aMissingSettingRefusesToStartAndNeverGeneratesAKey(String configured) throws Exception {
		assertRefused(configured, Problem.NOT_CONFIGURED);
		assertRefused(null, Problem.NOT_CONFIGURED);
		try (var files = Files.list(tempDir)) {
			assertThat(files.toList()).as("nothing was created").isEmpty();
		}
	}

	@Test
	void aRelativePathIsRefused() {
		assertRefused("keys/document-key.json", Problem.NOT_ABSOLUTE);
	}

	@Test
	void aMissingFileIsRefusedAndNotCreated() {
		Path missing = tempDir.resolve("keys").resolve("document-key.json");
		assertRefused(missing.toString(), Problem.NOT_FOUND);
		assertThat(missing).doesNotExist();
	}

	@Test
	void aFolderIsRefused() throws Exception {
		Path folder = Files.createDirectories(tempDir.resolve("keys").resolve("document-key.json"));
		assertRefused(folder.toString(), Problem.NOT_REGULAR_FILE);
	}

	@Test
	void aLinkIsRefused() throws Exception {
		Path target = keyFile();
		Path link = tempDir.resolve("linked-document-key.json");
		try {
			Files.createSymbolicLink(link, target);
		}
		catch (Exception ex) {
			assumeTrue(false, "file symbolic links cannot be created here: " + ex.getClass().getSimpleName());
		}
		assertRefused(link.toString(), Problem.NOT_REGULAR_FILE);
	}

	@Test
	void aFileReachedThroughALinkedFolderInsideTheStorageRootIsRefused() throws Exception {
		Path insideRoot = Files.createDirectories(storageRoot().resolve("keys"));
		Path key = TestKeys.writeKeyFile(insideRoot.resolve("document-key.json"));
		Path linkedFolder = tempDir.resolve("elsewhere");
		assumeTrue(TestLinks.createDirectoryLink(linkedFolder, insideRoot), "no directory link could be created");

		assertRefused(linkedFolder.resolve("document-key.json").toString(), Problem.INSIDE_STORAGE);
		assertThat(key).exists();
	}

	@Test
	void aKeyFileInsideTheStorageRootIsRefused() throws Exception {
		Path inside = TestKeys.writeKeyFile(storageRoot().resolve("data").resolve("document-key.json"));
		assertRefused(inside.toString(), Problem.INSIDE_STORAGE);
	}

	@Test
	void anOversizedFileIsRefused() throws Exception {
		Path big = ownerOnlyFile("document-key.json", new byte[DocumentKeyRing.MAX_FILE_BYTES + 1]);
		assertRefused(big.toString(), Problem.TOO_LARGE);
	}

	@Test
	void malformedJsonIsRefused() throws Exception {
		Path file = ownerOnlyFile("document-key.json", "{\"primaryKeyId\": 1, \"key\": [".getBytes(StandardCharsets.UTF_8));
		assertRefused(file.toString(), Problem.INVALID_KEYSET);
	}

	@Test
	void aRawAesKeyIsNotAKeyFile() throws Exception {
		byte[] raw = new byte[32];
		new java.security.SecureRandom().nextBytes(raw);
		assertRefused(ownerOnlyFile("document-key.json", raw).toString(), Problem.INVALID_KEYSET);
		assertRefused(ownerOnlyFile("document-key-b64.json", Base64.getEncoder().encode(raw)).toString(),
				Problem.INVALID_KEYSET);
	}

	@Test
	void otherKeyTypesAndParametersAreRefused() throws Exception {
		AeadConfig.register();
		Path aead = tempDir.resolve("keys").resolve("aead-key.json");
		TestKeys.writeKeyFile(aead, PredefinedAeadParameters.AES256_GCM);
		assertRefused(aead.toString(), Problem.UNSUPPORTED_KEY);

		Path small = TestKeys.writeKeyFile(tempDir.resolve("keys").resolve("aes128-key.json"),
				PredefinedStreamingAeadParameters.AES128_GCM_HKDF_1MB);
		assertRefused(small.toString(), Problem.UNSUPPORTED_KEY);

		Path segments = TestKeys.writeKeyFile(tempDir.resolve("keys").resolve("4kb-key.json"),
				PredefinedStreamingAeadParameters.AES256_GCM_HKDF_4KB);
		assertRefused(segments.toString(), Problem.UNSUPPORTED_KEY);
	}

	@ParameterizedTest
	@ValueSource(strings = { "Everyone", "Users", "Authenticated Users" })
	void aKeyFileReadableByOtherUsersIsRefused(String group) throws Exception {
		Path file = keyFile();
		PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
		AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
		if (posix != null) {
			posix.setPermissions(PosixFilePermissions.fromString("rw-r--r--"));
		}
		else {
			assumeTrue(acl != null, "neither POSIX permissions nor ACLs are supported");
			UserPrincipal principal;
			try {
				principal = file.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(group);
			}
			catch (Exception ex) {
				assumeTrue(false, "the group " + group + " has another name on this Windows installation");
				return;
			}
			List<AclEntry> entries = new ArrayList<>(acl.getAcl());
			entries.add(AclEntry.newBuilder()
				.setType(AclEntryType.ALLOW)
				.setPrincipal(principal)
				.setPermissions(EnumSet.of(AclEntryPermission.READ_DATA))
				.build());
			acl.setAcl(entries);
		}
		assertRefused(file.toString(), Problem.TOO_PERMISSIVE);
	}

	@Test
	void keyFilesWrittenByTheToolAreOwnerOnly() throws Exception {
		Path file = keyFile();
		assertThat(KeyFilePermissions.isOwnerOnly(file)).isTrue();
		AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
		if (acl != null) {
			assertThat(acl.getAcl()).as("a single entry for the owner; inherited entries are removed")
				.singleElement()
				.satisfies(entry -> assertThat(entry.principal()).isEqualTo(acl.getOwner()));
		}
		PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
		if (posix != null) {
			assertThat(PosixFilePermissions.toString(posix.readAttributes().permissions())).isEqualTo("rw-------");
		}
	}

	@Test
	void anotherKeyFileCannotDecryptDocumentsOfTheFirst() throws Exception {
		DocumentCipher first = new DocumentCipher(DocumentKeyRing.load(keyFile().toString(), storageRoot()));
		DocumentCipher second = new DocumentCipher(DocumentKeyRing.load(
				TestKeys.writeKeyFile(tempDir.resolve("other").resolve("document-key.json")).toString(), storageRoot()));
		ByteArrayOutputStream raw = new ByteArrayOutputStream();
		try (OutputStream out = first.encrypt(raw, "8e3c2a4f-1b2d-4c5e-9f60-7a8b9c0d1e2f")) {
			out.write("fictional".getBytes(StandardCharsets.US_ASCII));
		}
		assertThatThrownBy(() -> {
			try (InputStream in = second.decrypt(new ByteArrayInputStream(raw.toByteArray()),
					"8e3c2a4f-1b2d-4c5e-9f60-7a8b9c0d1e2f")) {
				in.readAllBytes();
			}
		}).isInstanceOf(DocumentCryptoException.class);
	}

	@Test
	void disabledKeysAreNotUsedForDecryption() throws Exception {
		KeysetHandle two = TestKeyRings.twoKeys();
		int older = two.getAt(0).getId();
		KeysetHandle.Builder builder = KeysetHandle.newBuilder(two);
		builder.getAt(0).setStatus(com.google.crypto.tink.KeyStatus.DISABLED);
		DocumentKeyRing ring = DocumentKeyRing.fromKeyset(builder.build());

		assertThat(ring.hasKey(older)).isFalse();
		assertThat(ring.keyCount()).isEqualTo(1);
	}

}
