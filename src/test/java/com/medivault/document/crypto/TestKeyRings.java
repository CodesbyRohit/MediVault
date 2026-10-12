package com.medivault.document.crypto;

import java.security.GeneralSecurityException;

import com.google.crypto.tink.KeysetHandle;

/** Key rings with chosen key ids, for tampering tests. */
final class TestKeyRings {

	private TestKeyRings() {
	}

	/** A ring whose only key has {@code keyId} but fresh, unrelated key material. */
	static DocumentKeyRing withFixedId(int keyId) {
		try {
			return DocumentKeyRing.fromKeyset(KeysetHandle.newBuilder()
				.addEntry(KeysetHandle.generateEntryFromParameters(DocumentKeyRing.PARAMETERS).withFixedId(keyId).makePrimary())
				.build());
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** A ring with two enabled keys (the second is primary). */
	static DocumentKeyRing withTwoKeys() {
		return DocumentKeyRing.fromKeyset(twoKeys());
	}

	static KeysetHandle twoKeys() {
		try {
			KeysetHandle one = KeysetHandle.generateNew(DocumentKeyRing.PARAMETERS);
			return KeysetHandle.newBuilder(one)
				.addEntry(KeysetHandle.generateEntryFromParameters(DocumentKeyRing.PARAMETERS).withRandomId().makePrimary())
				.build();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** An enabled key of {@code ring} that is not the primary one. */
	static int otherKeyId(DocumentKeyRing ring) {
		for (int candidate : ring.enabledKeyIds()) {
			if (candidate != ring.primaryKeyId()) {
				return candidate;
			}
		}
		throw new IllegalStateException("only one key");
	}

}
