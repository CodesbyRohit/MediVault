package com.medivault.document;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds small, structurally valid one-page PDFs with clearly FICTIONAL text, so tests never need
 * real documents. Uses ASCII only, so character offsets equal byte offsets in the xref table.
 */
public final class FictionalPdf {

	private FictionalPdf() {
	}

	public static byte[] bytes(String text) {
		String safe = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
		String content = "BT /F1 12 Tf 72 720 Td (FICTIONAL SAMPLE - not real medical data: " + safe + ") Tj ET";
		List<String> objects = List.of("<< /Type /Catalog /Pages 2 0 R >>",
				"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
				"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
						+ "/Resources << /Font << /F1 5 0 R >> >> >>",
				"<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream",
				"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

		StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
		List<Integer> offsets = new ArrayList<>();
		for (int i = 0; i < objects.size(); i++) {
			offsets.add(pdf.length());
			pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
		}
		int xref = pdf.length();
		pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
		for (int offset : offsets) {
			pdf.append(String.format("%010d 00000 n \n", offset));
		}
		pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
			.append(xref)
			.append("\n%%EOF\n");
		return pdf.toString().getBytes(StandardCharsets.US_ASCII);
	}

	/**
	 * Exactly {@code size} bytes with a PDF header at the start and {@code %%EOF} at the end (padded
	 * with spaces in between), for size-limit tests. The xref offsets are not adjusted.
	 */
	public static byte[] ofSize(int size) {
		byte[] base = bytes("padded");
		if (size < base.length) {
			throw new IllegalArgumentException("size too small");
		}
		byte[] result = new byte[size];
		// Keep the header at the start and the %%EOF marker at the very end; pad with spaces between.
		int headerEnd = "%PDF-1.4\n".length();
		System.arraycopy(base, 0, result, 0, headerEnd);
		java.util.Arrays.fill(result, headerEnd, size - (base.length - headerEnd), (byte) ' ');
		System.arraycopy(base, headerEnd, result, size - (base.length - headerEnd), base.length - headerEnd);
		return result;
	}

}
