package com.medivault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Creates directory links for "links are never followed" tests. */
public final class TestLinks {

	private TestLinks() {
	}

	/**
	 * A symbolic link where allowed; otherwise, on Windows, a directory junction (which needs no
	 * Developer Mode or elevation). Command output is discarded (it contains paths).
	 * @return whether a link now exists at {@code link}
	 */
	public static boolean createDirectoryLink(Path link, Path target) throws IOException, InterruptedException {
		try {
			Files.createSymbolicLink(link, target);
			return true;
		}
		catch (IOException | UnsupportedOperationException | SecurityException ex) {
			if (!System.getProperty("os.name", "").startsWith("Windows")) {
				return false;
			}
		}
		Process mklink = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
			.redirectErrorStream(true)
			.start();
		mklink.getInputStream().readAllBytes();
		return mklink.waitFor() == 0 && Files.exists(link, LinkOption.NOFOLLOW_LINKS);
	}

}
