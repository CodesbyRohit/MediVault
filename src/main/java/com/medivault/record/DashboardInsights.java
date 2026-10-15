package com.medivault.record;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Read-only dashboard figures for one patient, computed only from that patient's saved rows. None
 * of it is a measurement or a health score: completeness describes paperwork only. Values that do
 * not exist yet (no records, no uploads) are {@code null}.
 *
 * @param latestRecord greatest record date, then most recently added, then highest id
 * @param lastAddedAt when the most recently added record was added, in {@code zone}
 * @param lastUploadAt when the most recent PDF was uploaded, in {@code zone}
 * @param types all record types in enum order, including those without records
 */
public record DashboardInsights(String initials, LocalDate memberSince, Completeness completeness,
		List<WeekBar> weeks, ZoneId zone, LocalDate earliestRecordDate, MedicalRecord latestRecord,
		LocalDateTime lastAddedAt, LocalDateTime lastUploadAt, int providerCount, List<TypeShare> types) {

	/** How many record types have at least one record. */
	public long typesOnFile() {
		return types.stream().filter(share -> share.count() > 0).count();
	}

	/** Up to two initials: the first letters of the first and last name parts, upper-cased. */
	static String initials(String fullName) {
		String[] parts = fullName == null ? new String[] { "" } : fullName.strip().split("\\s+");
		if (parts[0].isEmpty()) {
			return "?";
		}
		String first = firstLetter(parts[0]);
		return parts.length == 1 ? first : first + firstLetter(parts[parts.length - 1]);
	}

	private static String firstLetter(String part) {
		return new String(Character.toChars(part.codePointAt(0))).toUpperCase(Locale.ROOT);
	}

	/**
	 * Per record: is a provider named, are there notes, is at least one PDF attached. Each count is a
	 * number of records, so a record with several PDFs counts once.
	 */
	public record Completeness(long total, long withProvider, long withNotes, long withDocument) {

		/**
		 * (withProvider + withNotes + withDocument) × 100 ÷ (3 × total), rounded down so 100% means
		 * every field is filled; {@code null} when there are no records.
		 */
		public Integer percent() {
			if (total == 0) {
				return null;
			}
			return (int) ((withProvider + withNotes + withDocument) * 100 / (3 * total));
		}

		public List<RingSegment> segments() {
			return List.of(new RingSegment("Provider named", withProvider, total, 0),
					new RingSegment("Notes added", withNotes, total, 1),
					new RingSegment("PDF attached", withDocument, total, 2));
		}

	}

	/**
	 * One 120° segment of the completeness ring, drawn on a circle whose {@code pathLength} is 300:
	 * each segment owns 100 units, of which 96 are drawn and 4 are a gap (2 on each side).
	 */
	public record RingSegment(String label, long count, long total, int index) {

		private static final double SLOT = 100;

		private static final double ARC = 96;

		private static final double CIRCLE = 300;

		/** The pale track behind the whole segment. */
		public String trackDash() {
			return dash(ARC);
		}

		/** The filled part, proportional to count ÷ total. */
		public String fillDash() {
			return dash(total == 0 ? 0 : ARC * count / total);
		}

		/** Moves the dash pattern to the start of this segment's arc. */
		public String offset() {
			return number(-(index * SLOT + (SLOT - ARC) / 2));
		}

		private static String dash(double length) {
			return number(length) + " " + number(CIRCLE - length);
		}

		private static String number(double value) {
			return String.format(Locale.ROOT, "%.2f", value);
		}

	}

	/**
	 * One week of the "records added" histogram: its Monday, how many records were added, the bar
	 * height in SVG units (0 to {@link WeekWindow#MAX_BAR_HEIGHT}) and whether it is the current week.
	 */
	public record WeekBar(LocalDate weekStart, long count, int height, boolean current) {
	}

	/** A record type's count and its whole-number share of all records (rounded half up). */
	public record TypeShare(RecordType type, long count, int percent) {

		static TypeShare of(RecordType type, long count, long total) {
			return new TypeShare(type, count, total == 0 ? 0 : (int) Math.round(count * 100.0 / total));
		}

	}

}
