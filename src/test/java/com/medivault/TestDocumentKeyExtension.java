package com.medivault;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.medivault.document.crypto.TestKeys;

/**
 * Registered for every test class (see {@code junit-platform.properties} and
 * {@code META-INF/services}): creates one throwaway document key under {@code target/test-keys} and
 * points {@code medivault.documents.key-file} at it, because MediVault refuses to start without a
 * key. It never touches {@code MEDIVAULT_DOCUMENT_KEY_FILE} or any real key. Tests that check the
 * refusal override the property with {@code --medivault.documents.key-file=}.
 */
public class TestDocumentKeyExtension implements BeforeAllCallback {

	public static final String PROPERTY = "medivault.documents.key-file";

	@Override
	public void beforeAll(ExtensionContext context) {
		if (System.getProperty(PROPERTY) == null) {
			System.setProperty(PROPERTY, TestKeys.sharedKeyFile().toString());
		}
	}

}
