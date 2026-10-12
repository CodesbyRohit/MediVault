package com.medivault.document.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Owner-only permissions for document key files.
 * <ul>
 * <li>POSIX file systems: no group or other permission bit may be set.</li>
 * <li>Windows (ACL file systems): an allow-list. Only the file's owner, {@code NT AUTHORITY\SYSTEM} and
 * {@code BUILTIN\Administrators} may have read access; an entry granting read access to anyone else
 * (Everyone, Users, Authenticated Users, other accounts or groups, unresolved SIDs) refuses the file.
 * The system and administrator names are compared in English, so on a localised Windows those
 * entries are refused too: that fails closed, and the key tool's owner-only ACL always passes.</li>
 * </ul>
 */
final class KeyFilePermissions {

	private static final Set<String> TRUSTED_PRINCIPALS = Set.of("nt authority\\system", "builtin\\administrators");

	private static final Set<PosixFilePermission> NOT_OWNER = EnumSet.of(PosixFilePermission.GROUP_READ,
			PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
			PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE);

	private KeyFilePermissions() {
	}

	/** Whether only the owner (plus, on Windows, the system and administrators) can read {@code file}. */
	static boolean isOwnerOnly(Path file) throws IOException {
		PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class,
				LinkOption.NOFOLLOW_LINKS);
		if (posix != null && posix.readAttributes().permissions().stream().anyMatch(NOT_OWNER::contains)) {
			return false;
		}
		AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class,
				LinkOption.NOFOLLOW_LINKS);
		if (acl != null) {
			UserPrincipal owner = acl.getOwner();
			for (AclEntry entry : acl.getAcl()) {
				if (entry.type() == AclEntryType.ALLOW && entry.permissions().contains(AclEntryPermission.READ_DATA)
						&& !entry.principal().equals(owner)
						&& !TRUSTED_PRINCIPALS.contains(entry.principal().getName().toLowerCase(Locale.ROOT))) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Restricts {@code file} to its owner: permissions {@code rw-------} on POSIX, or an access-control
	 * list with a single entry for the owner on Windows (this also removes inherited entries).
	 */
	static void restrictToOwner(Path file) throws IOException {
		PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class,
				LinkOption.NOFOLLOW_LINKS);
		if (posix != null) {
			posix.setPermissions(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
		}
		AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class,
				LinkOption.NOFOLLOW_LINKS);
		if (acl != null) {
			AclEntry ownerOnly = AclEntry.newBuilder()
				.setType(AclEntryType.ALLOW)
				.setPrincipal(acl.getOwner())
				.setPermissions(EnumSet.allOf(AclEntryPermission.class))
				.build();
			acl.setAcl(List.of(ownerOnly));
		}
	}

}
