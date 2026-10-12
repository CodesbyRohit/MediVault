package com.medivault.document;

import java.io.ByteArrayInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import com.medivault.document.DocumentIntegrityException.Reason;
import com.medivault.document.crypto.DocumentCipher;
import com.medivault.document.crypto.DocumentCryptoException;

/**
 * Private on-disk storage for uploaded PDFs, in one flat folder outside the web root.
 * <ul>
 * <li>Files are named only by a random UUID ({@code storage key}); the uploaded filename is never
 * used as a path. Keys are validated against the UUID format and resolved paths must stay directly
 * inside the folder; symbolic links are never followed, and the folder itself must not be a link
 * or junction.</li>
 * <li>Every new file is <b>encrypted</b> ({@link DocumentCipher}): the upload is streamed (never held
 * in memory) through the PDF checks and the cipher into a uniquely named {@code .part} file, forced
 * to disk, read back and verified, then moved to its final name without replacing anything. No
 * plaintext is ever written to this folder. A crash can leave a {@code .part} file (ciphertext)
 * behind; those are counted (never deleted) at startup.</li>
 * <li>Reads are <b>verified</b>: {@link #openVerified} first reads the whole file, decrypting and
 * authenticating it (or, for {@link StorageFormat#PLAIN_V0} files, reading it), and compares its size
 * and SHA-256 with the expected values, before anything is returned; the returned stream checks
 * again while it is read.</li>
 * <li>Exceptions and log lines never contain paths: they would reveal the Windows user name.</li>
 * </ul>
 */
@Component
public class DocumentStorage {

	private static final Logger log = LoggerFactory.getLogger(DocumentStorage.class);

	static final String PART_SUFFIX = ".part";

	private static final Pattern STORAGE_KEY = Pattern
		.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

	/** "%PDF-" followed by a version such as 1.7 or 2.0. */
	private static final Pattern PDF_HEADER = Pattern.compile("%PDF-[12]\\.[0-9]");

	private static final int HEADER_LENGTH = 8;

	/** The end-of-file marker must appear in the last kilobyte (PDF 32000-1, 7.5.5). */
	private static final int TRAILER_WINDOW = 1024;

	private static final byte[] EOF_MARKER = "%%EOF".getBytes(StandardCharsets.US_ASCII);

	/** Every PDF, and so every plain legacy file, starts with this. */
	private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	/** The result of a successful {@link #store}: size and SHA-256 describe the plaintext PDF. */
	public record StoredFile(String key, long size, String sha256, int keyId) {
	}

	private final Path directory;

	private final long maxBytes;

	private final Supplier<UUID> keyGenerator;

	private final DocumentCipher cipher;

	@Autowired
	public DocumentStorage(@Value("${medivault.documents.directory}") String directory,
			@Value("${medivault.documents.max-size}") DataSize maxSize, DocumentCipher cipher) {
		this(Path.of(directory), maxSize.toBytes(), UUID::randomUUID, cipher);
	}

	DocumentStorage(Path directory, long maxBytes, Supplier<UUID> keyGenerator, DocumentCipher cipher) {
		if (!directory.isAbsolute()) {
			throw new IllegalStateException("Refusing to start: medivault.documents.directory must be an absolute path.");
		}
		requirePlainDirectoryIfPresent(directory);
		try {
			Files.createDirectories(directory);
			this.directory = directory.toRealPath();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Refusing to start: the document storage folder cannot be created or "
					+ "opened (" + ex.getClass().getSimpleName() + ").");
		}
		this.maxBytes = maxBytes;
		this.keyGenerator = keyGenerator;
		this.cipher = cipher;
		reportLeftovers();
	}

	public long maxBytes() {
		return maxBytes;
	}

	/**
	 * Streams {@code content} to storage, verifies it is a PDF, and stores it encrypted. Closes
	 * {@code content}.
	 * @throws DocumentRejectedException if the content is empty, too large or not a PDF
	 * @throws DocumentStorageException if the file cannot be written or does not verify after writing
	 */
	public StoredFile store(InputStream content) {
		Path partFile = null;
		String key = keyGenerator.get().toString();
		try (InputStream in = content) {
			partFile = Files.createTempFile(directory, "upload-", PART_SUFFIX);
			PdfInspector pdf = new PdfInspector();
			try (FileChannel channel = FileChannel.open(partFile, StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
				try (OutputStream encrypted = cipher.encrypt(new KeepOpen(Channels.newOutputStream(channel)), key)) {
					byte[] buffer = new byte[8192];
					int read;
					while ((read = in.read(buffer)) != -1) {
						if (pdf.size() + read > maxBytes) {
							throw new DocumentRejectedException(DocumentRejectedException.Reason.TOO_LARGE);
						}
						pdf.update(buffer, read);
						encrypted.write(buffer, 0, read);
					}
				}
				// Durable before the file gets its final name and a database row can point at it.
				channel.force(true);
			}
			pdf.verifyStructure();
			// Read back: a row may only ever reference a file that decrypts to exactly what was uploaded.
			verifyFile(StorageFormat.ENCRYPTED_V1, partFile, key, pdf.size(), pdf.sha256Hex());

			// No REPLACE_EXISTING: the move fails instead of overwriting an existing file.
			Files.move(partFile, resolve(key));
			partFile = null;
			return new StoredFile(key, pdf.size(), pdf.sha256Hex(), cipher.primaryKeyId());
		}
		catch (FileAlreadyExistsException ex) {
			throw new DocumentStorageException("A stored document already uses the generated key; nothing was overwritten.");
		}
		catch (DocumentIntegrityException ex) {
			throw new DocumentStorageException("The document could not be stored: it did not verify after writing ("
					+ ex.reason() + ").");
		}
		catch (IOException ex) {
			throw new DocumentStorageException("The document could not be written (" + ex.getClass().getSimpleName() + ").");
		}
		finally {
			if (partFile != null) {
				deleteQuietly(partFile);
			}
		}
	}

	/**
	 * Opens a stored file's raw bytes (ciphertext for encrypted files). Callers that need the document
	 * itself must use {@link #openVerified}. The caller must close the stream.
	 * @throws DocumentStorageException if the key is invalid or the file is missing or unreadable
	 */
	public InputStream open(String key) {
		Path file = resolve(key);
		try {
			BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class,
					LinkOption.NOFOLLOW_LINKS);
			if (!attributes.isRegularFile()) {
				throw new DocumentStorageException("A stored document is not a regular file.");
			}
			return Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
		}
		catch (IOException ex) {
			throw new DocumentStorageException("A stored document could not be opened (" + ex.getClass().getSimpleName()
					+ ").");
		}
	}

	/**
	 * Verifies the stored document completely, then returns a stream of its content (decrypted for
	 * encrypted files). Nothing is returned unless the whole file authenticated (for encrypted files)
	 * and matched {@code expectedSize} and {@code expectedSha256}. The returned stream checks the
	 * content again while it is read and fails at the end if the file changed in the meantime. The
	 * caller must close the stream.
	 * @throws DocumentIntegrityException if the document is damaged, tampered with, in the wrong format,
	 * or cannot be decrypted
	 * @throws DocumentStorageException if the file is missing or unreadable
	 */
	public InputStream openVerified(StorageFormat format, String key, long expectedSize, String expectedSha256) {
		verify(format, key, expectedSize, expectedSha256);
		return new VerifyingInputStream(openContent(format, key), expectedSize, expectedSha256);
	}

	/**
	 * Reads the whole stored document and checks it, without returning any of it.
	 * @throws DocumentIntegrityException if the check fails
	 * @throws DocumentStorageException if the file is missing or unreadable
	 */
	public void verify(StorageFormat format, String key, long expectedSize, String expectedSha256) {
		verifyFile(format, resolve(key), key, expectedSize, expectedSha256);
	}

	private void verifyFile(StorageFormat format, Path file, String key, long expectedSize, String expectedSha256) {
		MessageDigest digest = sha256();
		long size = 0;
		try (InputStream in = content(format, file, key)) {
			byte[] buffer = new byte[64 * 1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				size += read;
				if (size > expectedSize) {
					throw new DocumentIntegrityException(Reason.SIZE_MISMATCH);
				}
				digest.update(buffer, 0, read);
			}
		}
		catch (DocumentCryptoException ex) {
			throw new DocumentIntegrityException(reasonFor(ex));
		}
		catch (IOException ex) {
			throw new DocumentIntegrityException(Reason.UNREADABLE);
		}
		if (size != expectedSize) {
			throw new DocumentIntegrityException(Reason.SIZE_MISMATCH);
		}
		if (!MessageDigest.isEqual(HexFormat.of().formatHex(digest.digest()).getBytes(StandardCharsets.US_ASCII),
				expectedSha256.getBytes(StandardCharsets.US_ASCII))) {
			throw new DocumentIntegrityException(Reason.CHECKSUM_MISMATCH);
		}
	}

	/** The content stream of a stored file in {@code format}; header problems are integrity failures. */
	private InputStream openContent(StorageFormat format, String key) {
		Path file = resolve(key);
		try {
			return content(format, file, key);
		}
		catch (DocumentCryptoException ex) {
			throw new DocumentIntegrityException(reasonFor(ex));
		}
		catch (IOException ex) {
			throw new DocumentIntegrityException(Reason.UNREADABLE);
		}
	}

	private InputStream content(StorageFormat format, Path file, String key) throws IOException {
		InputStream raw = file.equals(resolveOrNull(key)) ? open(key) : openRegularFile(file);
		try {
			if (format == StorageFormat.ENCRYPTED_V1) {
				return cipher.decrypt(raw, key);
			}
			byte[] magic = raw.readNBytes(PDF_MAGIC.length);
			if (!Arrays.equals(magic, PDF_MAGIC)) {
				throw new DocumentIntegrityException(Reason.FORMAT_MISMATCH);
			}
			return new SequenceInputStream(new ByteArrayInputStream(magic), raw);
		}
		catch (IOException | RuntimeException ex) {
			raw.close();
			throw ex;
		}
	}

	private static InputStream openRegularFile(Path file) throws IOException {
		return Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
	}

	private static Reason reasonFor(DocumentCryptoException ex) {
		return switch (ex.reason()) {
			case NOT_ENCRYPTED, UNSUPPORTED_VERSION -> Reason.FORMAT_MISMATCH;
			case KEY_UNAVAILABLE -> Reason.KEY_UNAVAILABLE;
			case AUTHENTICATION_FAILED -> Reason.AUTHENTICATION_FAILED;
			case ENCRYPTION_FAILED -> Reason.UNREADABLE;
		};
	}

	public boolean exists(String key) {
		return Files.isRegularFile(resolve(key), LinkOption.NOFOLLOW_LINKS);
	}

	/**
	 * Deletes exactly the file named by {@code key}, if present. Used only to undo an upload whose
	 * database row was not committed, and to remove the file of a document whose row was deleted
	 * and committed.
	 * @return whether a file was deleted
	 */
	public boolean delete(String key) {
		Path file = resolve(key);
		try {
			return Files.deleteIfExists(file);
		}
		catch (IOException ex) {
			throw new DocumentStorageException("A stored document could not be deleted (" + ex.getClass().getSimpleName()
					+ ").");
		}
	}

	/**
	 * The keys of all stored documents: regular files (never links) directly in the folder whose name
	 * is a valid storage key. Unfinished {@code .part} files and anything else are ignored.
	 * @throws DocumentStorageException if the folder cannot be listed
	 */
	public Set<String> listKeys() {
		Set<String> keys = new HashSet<>();
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
			for (Path entry : entries) {
				String name = entry.getFileName().toString();
				if (STORAGE_KEY.matcher(name).matches() && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
					keys.add(name);
				}
			}
		}
		catch (IOException ex) {
			throw new DocumentStorageException("The document storage folder could not be listed ("
					+ ex.getClass().getSimpleName() + ").");
		}
		return keys;
	}

	/** Maps a storage key to its file, refusing anything that is not a plain UUID inside the folder. */
	Path resolve(String key) {
		if (key == null || !STORAGE_KEY.matcher(key).matches()) {
			throw new DocumentStorageException("Invalid storage key.");
		}
		Path file = directory.resolve(key).normalize();
		if (!directory.equals(file.getParent())) {
			throw new DocumentStorageException("Invalid storage key.");
		}
		return file;
	}

	private Path resolveOrNull(String key) {
		try {
			return resolve(key);
		}
		catch (DocumentStorageException ex) {
			return null;
		}
	}

	/**
	 * An existing storage folder must be a real directory, never a file, symbolic link or junction
	 * (on Windows a junction reports isDirectory() and isOther()), in every profile.
	 */
	private static void requirePlainDirectoryIfPresent(Path directory) {
		BasicFileAttributes attributes;
		try {
			attributes = Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		}
		catch (java.nio.file.NoSuchFileException ex) {
			return;
		}
		catch (IOException | SecurityException ex) {
			throw new IllegalStateException("Refusing to start: the document storage folder cannot be inspected ("
					+ ex.getClass().getSimpleName() + ").");
		}
		if (!attributes.isDirectory() || attributes.isOther() || attributes.isSymbolicLink()) {
			throw new IllegalStateException("Refusing to start: the document storage folder exists but is not a plain "
					+ "directory (files, symbolic links and junctions are refused).");
		}
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

	private static void deleteQuietly(Path file) {
		try {
			Files.deleteIfExists(file);
		}
		catch (IOException ex) {
			log.warn("A temporary upload file could not be removed ({}).", ex.getClass().getSimpleName());
		}
	}

	private void reportLeftovers() {
		int leftovers = 0;
		try (DirectoryStream<Path> parts = Files.newDirectoryStream(directory, "*" + PART_SUFFIX)) {
			for (Path ignored : parts) {
				leftovers++;
			}
		}
		catch (IOException ex) {
			log.warn("The document storage folder could not be listed ({}).", ex.getClass().getSimpleName());
			return;
		}
		if (leftovers > 0) {
			log.warn("Document storage: {} unfinished upload file(s) (*{}) from an interrupted upload were found. "
					+ "They are not used by MediVault and can be deleted while the app is stopped (see README).",
					leftovers, PART_SUFFIX);
		}
	}

	/**
	 * Size, SHA-256 and the PDF structure checks of an upload, computed in the same single pass that
	 * encrypts it: the first 8 bytes are kept for the header check, and the last kilobyte in a ring
	 * buffer for the end-of-file marker. The plaintext is never written anywhere else.
	 */
	private static final class PdfInspector {

		private final MessageDigest digest = sha256();

		private final byte[] header = new byte[HEADER_LENGTH];

		private final byte[] trailer = new byte[TRAILER_WINDOW];

		private long size;

		private String sha256Hex;

		void update(byte[] buffer, int length) {
			digest.update(buffer, 0, length);
			for (int i = 0; i < length; i++) {
				long position = size + i;
				if (position < HEADER_LENGTH) {
					header[(int) position] = buffer[i];
				}
				trailer[(int) (position % TRAILER_WINDOW)] = buffer[i];
			}
			size += length;
		}

		long size() {
			return size;
		}

		/** Hex SHA-256 of everything seen; call once, after the last {@link #update}. */
		String sha256Hex() {
			if (sha256Hex == null) {
				sha256Hex = HexFormat.of().formatHex(digest.digest());
			}
			return sha256Hex;
		}

		void verifyStructure() {
			if (size == 0) {
				throw new DocumentRejectedException(DocumentRejectedException.Reason.EMPTY);
			}
			String headerText = new String(header, 0, (int) Math.min(size, HEADER_LENGTH), StandardCharsets.ISO_8859_1);
			if (headerText.length() < HEADER_LENGTH || !PDF_HEADER.matcher(headerText).matches()) {
				throw new DocumentRejectedException(DocumentRejectedException.Reason.NOT_PDF_CONTENT);
			}
			int window = (int) Math.min(TRAILER_WINDOW, size);
			byte[] last = new byte[window];
			for (int i = 0; i < window; i++) {
				last[i] = trailer[(int) ((size - window + i) % TRAILER_WINDOW)];
			}
			if (!contains(last, EOF_MARKER)) {
				throw new DocumentRejectedException(DocumentRejectedException.Reason.MALFORMED);
			}
		}

		private static boolean contains(byte[] data, byte[] marker) {
			outer: for (int i = 0; i <= data.length - marker.length; i++) {
				for (int j = 0; j < marker.length; j++) {
					if (data[i + j] != marker[j]) {
						continue outer;
					}
				}
				return true;
			}
			return false;
		}

	}

	/** Lets the cipher finish its stream without closing the file channel, which still has to be forced to disk. */
	private static final class KeepOpen extends FilterOutputStream {

		KeepOpen(OutputStream out) {
			super(out);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			out.write(b, off, len);
		}

		@Override
		public void close() throws IOException {
			flush();
		}

	}

	/**
	 * Pass two of a verified read: re-computes size and SHA-256 while the content is read, and fails
	 * at the end (or as soon as there is too much) if the file changed after pass one. For encrypted
	 * files every byte returned has also been authenticated by the cipher.
	 */
	static final class VerifyingInputStream extends InputStream {

		private final InputStream in;

		private final long expectedSize;

		private final String expectedSha256;

		private final MessageDigest digest = sha256();

		private long size;

		private boolean checked;

		VerifyingInputStream(InputStream in, long expectedSize, String expectedSha256) {
			this.in = in;
			this.expectedSize = expectedSize;
			this.expectedSha256 = expectedSha256;
		}

		@Override
		public int read() throws IOException {
			byte[] one = new byte[1];
			int read = read(one, 0, 1);
			return read == -1 ? -1 : one[0] & 0xff;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int read;
			try {
				read = in.read(b, off, len);
			}
			catch (IOException ex) {
				throw new IOException("A stored document failed verification while it was being read.");
			}
			if (read == -1) {
				checkAtEnd();
				return -1;
			}
			size += read;
			if (size > expectedSize) {
				throw new IOException("A stored document changed while it was being read.");
			}
			digest.update(b, off, read);
			return read;
		}

		private void checkAtEnd() throws IOException {
			if (checked) {
				return;
			}
			checked = true;
			if (size != expectedSize || !HexFormat.of().formatHex(digest.digest()).equals(expectedSha256)) {
				throw new IOException("A stored document changed while it was being read.");
			}
		}

		@Override
		public void close() throws IOException {
			in.close();
		}

	}

}
