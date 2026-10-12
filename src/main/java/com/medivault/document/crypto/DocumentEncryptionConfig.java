package com.medivault.document.crypto;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the document key at startup. Encryption is required in every profile: without a valid key
 * file the application refuses to start (fail closed), and it never generates a key itself.
 * <p>
 * The key file path comes from {@code MEDIVAULT_DOCUMENT_KEY_FILE}, bound to the property
 * {@code medivault.documents.key-file} in {@code application.properties} (tests set that property to
 * a throwaway key under {@code target/}). The key itself only ever comes from that file.
 */
@Configuration
public class DocumentEncryptionConfig {

	private static final Logger log = LoggerFactory.getLogger(DocumentEncryptionConfig.class);

	@Bean
	DocumentCipher documentCipher(@Value("${medivault.documents.key-file:}") String keyFile,
			@Value("${medivault.documents.directory}") String documentsDirectory) {
		// The key must not live in the storage root (the parent of the PDF folder), so a copy of the data
		// never contains the key that unlocks it.
		Path storageRoot = Path.of(documentsDirectory).toAbsolutePath().normalize().getParent();
		DocumentKeyRing keys = DocumentKeyRing.load(keyFile, storageRoot);
		log.info("Document encryption: key file loaded ({} enabled key(s); new uploads use key id {}).",
				keys.keyCount(), Integer.toUnsignedString(keys.primaryKeyId()));
		return new DocumentCipher(keys);
	}

}
