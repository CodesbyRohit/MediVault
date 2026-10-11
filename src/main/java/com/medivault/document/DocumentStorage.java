package com.medivault.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
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
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import com.medivault.document.DocumentRejectedException.Reason;

/**
 * Private on-disk storage for uploaded PDF bytes, in one flat folder outside the web root.
 * <ul>
 * <li>Files are named only by a random UUID ({@code storage key}); the uploaded filename is never
 * used as a path. Keys are validated against the UUID format and resolved paths must stay directly
 * inside the folder; symbolic links are never followed, and the folder itself must not be a link
 * or junction.</li>
 * <li>Uploads are streamed (never held in memory) to a uniquely named {@code .part} file, checked,
 * then moved to their final name without replacing anything, so an existing file is never
 * overwritten. A crash can leave a {@code .part} file behind; those are counted (never deleted)
 * at startup.</li>
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

	/** The result of a successful {@link #store}. */
	public record StoredFile(String key, long size, String sha256) {
	}

	private final Path directory;

	private final long maxBytes;

	private final Supplier<UUID> keyGenerator;

	@Autowired
	public DocumentStorage(@Value("${medivault.documents.directory}") String directory,
			@Value("${medivault.documents.max-size}") DataSize maxSize) {
		this(Path.of(directory), maxSize.toBytes(), UUID::randomUUID);
	}

	DocumentStorage(Path directory, long maxBytes, Supplier<UUID> keyGenerator) {
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
		reportLeftovers();
	}

	public long maxBytes() {
		return maxBytes;
	}

	/**
	 * Streams {@code content} to storage and verifies it is a PDF. Closes {@code content}.
	 * @throws DocumentRejectedException if the content is empty, too large or not a PDF
	 * @throws DocumentStorageException if the file cannot be written
	 */
	public StoredFile store(InputStream content) {
		Path partFile = null;
		try (InputStream in = content) {
			partFile = Files.createTempFile(directory, "upload-", PART_SUFFIX);
			MessageDigest sha256 = sha256();
			long size = 0;
			try (OutputStream out = Files.newOutputStream(partFile, StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) != -1) {
					size += read;
					if (size > maxBytes) {
						throw new DocumentRejectedException(Reason.TOO_LARGE);
					}
					sha256.update(buffer, 0, read);
					out.write(buffer, 0, read);
				}
			}
			if (size == 0) {
				throw new DocumentRejectedException(Reason.EMPTY);
			}
			verifyPdfStructure(partFile, size);

			String key = keyGenerator.get().toString();
			// No REPLACE_EXISTING: the move fails instead of overwriting an existing file.
			Files.move(partFile, resolve(key));
			partFile = null;
			return new StoredFile(key, size, HexFormat.of().formatHex(sha256.digest()));
		}
		catch (FileAlreadyExistsException ex) {
			throw new DocumentStorageException("A stored document already uses the generated key; nothing was overwritten.");
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
	 * Opens a stored file for streaming. The caller must close the stream.
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

	public boolean exists(String key) {
		return Files.isRegularFile(resolve(key), LinkOption.NOFOLLOW_LINKS);
	}

	/**
	 * Deletes exactly the file named by {@code key}, if present. Only ever used to undo an upload
	 * whose database row was not committed.
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

	private static void verifyPdfStructure(Path file, long size) throws IOException {
		try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ,
				LinkOption.NOFOLLOW_LINKS)) {
			ByteBuffer header = ByteBuffer.allocate(HEADER_LENGTH);
			readFully(channel, header);
			String headerText = new String(header.array(), 0, header.position(), StandardCharsets.ISO_8859_1);
			if (headerText.length() < HEADER_LENGTH || !PDF_HEADER.matcher(headerText).matches()) {
				throw new DocumentRejectedException(Reason.NOT_PDF_CONTENT);
			}
			int window = (int) Math.min(TRAILER_WINDOW, size);
			channel.position(size - window);
			ByteBuffer trailer = ByteBuffer.allocate(window);
			readFully(channel, trailer);
			if (!contains(trailer.array(), trailer.position(), EOF_MARKER)) {
				throw new DocumentRejectedException(Reason.MALFORMED);
			}
		}
	}

	private static void readFully(SeekableByteChannel channel, ByteBuffer buffer) throws IOException {
		while (buffer.hasRemaining() && channel.read(buffer) > 0) {
			// keep reading
		}
	}

	private static boolean contains(byte[] data, int length, byte[] marker) {
		outer: for (int i = 0; i <= length - marker.length; i++) {
			for (int j = 0; j < marker.length; j++) {
				if (data[i + j] != marker[j]) {
					continue outer;
				}
			}
			return true;
		}
		return false;
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

}
