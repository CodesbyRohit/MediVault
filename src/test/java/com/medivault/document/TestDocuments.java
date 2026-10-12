package com.medivault.document;

/** Test access to package-private document details for tests in other packages. */
public final class TestDocuments {

	private TestDocuments() {
	}

	public static String storageKey(RecordDocument document) {
		return document.getStorageKey();
	}

}
