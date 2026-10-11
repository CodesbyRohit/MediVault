package com.medivault.document;

import java.util.Locale;

/** Human-readable file sizes for the record page ("850 bytes", "12.4 KB", "10 MB"). */
public final class DocumentSizes {

	private static final long KB = 1024;

	private static final long MB = KB * 1024;

	private DocumentSizes() {
	}

	public static String format(long bytes) {
		if (bytes < KB) {
			return bytes + (bytes == 1 ? " byte" : " bytes");
		}
		if (bytes < MB) {
			return trim(bytes / (double) KB) + " KB";
		}
		return trim(bytes / (double) MB) + " MB";
	}

	private static String trim(double value) {
		String text = String.format(Locale.ROOT, "%.1f", value);
		return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
	}

}
