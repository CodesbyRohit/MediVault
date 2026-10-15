# Dashboard Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the patient dashboard around three vault stat cards, a patient summary, a record-completeness ring, an eight-week "records added" histogram and a category breakdown, using only the patient's own saved data.

**Architecture:** A pure `WeekWindow` (week boundaries and bucketing) and a `DashboardInsights` record (completeness, ring geometry, type shares, initials) are assembled by a new read-only `DashboardService` from owner-scoped repository queries. `RecordController.dashboard` adds one model attribute, `insights`. The Thymeleaf template draws the charts as server-rendered inline SVG, and `main.css` gains a dashboard section. No scripts and no inline styles.

**Tech Stack:** Java 17, Spring Boot 3.5 (MVC, Data JPA, Security), Thymeleaf, H2 for tests, JUnit 5 + AssertJ + MockMvc, Maven wrapper (`.\mvnw.cmd`).

**Spec:** `docs/superpowers/specs/2026-10-14-dashboard-redesign-design.md` (revision 2). Read it before starting; section numbers below (§) refer to it.

## Global Constraints

- Stack locked: Java 17, Spring Boot 3.5.x, Thymeleaf. No new dependency, no JavaScript, no web fonts, no chart library.
- CSP unchanged: no `<style>`, `<script>`, `style=` / `th:style=`, `on*=` handlers, or other-origin URLs in any template (`TemplateCspComplianceTests`).
- No change to entities, Flyway migrations, `SecurityConfig`, encryption, `DocumentStorage`, the quota property or its enforcement, or any write path.
- Every new query filters by owner id; the owner id always comes from the authenticated principal.
- Completeness % = (provider-present + notes-present + PDF-present) ÷ (3 × total records) × 100, rounded down; undefined (`null`, no ring) with zero records; a record with several PDFs is one PDF-present record.
- Storage line: "{used} of {limit} upload limit used" from `StorageUsage.usedLabel()` / `quotaLabel()`, never hard-coded; source line says "original file sizes".
- Time zone: `ZoneId.systemDefault()`; weeks start Monday; window = [first Monday 00:00, Monday after the current week 00:00) in that zone; 8 bars, oldest first.
- Ordering: latest record = `recordDate` desc, `createdAt` desc, `id` desc; last added = `createdAt` desc, `id` desc; last upload = `uploadedAt` desc, `id` desc.
- Keep these strings and model attributes working: "Welcome, {name}", "Records by type", "You don't have any medical records yet.", `href="/records/new">Add your first record</a>`, `recordCount`, `recentRecords`, `typeCounts`, `storage`, `recentDocuments`.
- Fictional data only in tests and checks.
- Code style: Java uses tabs (match existing files); templates 4 spaces; CSS 2 spaces.
- **Approval gates (MediVault workflow):** do not run `.\mvnw.cmd`, start the app or a browser, or run any Git command until Rohit has authorised that action for the current stage. Every "Run" and "Commit" step below is conditional on that authorisation; if it is missing, stop and ask.

## Review Focus

1. **Rows written outside the form with space-only provider/notes:** they must not count as present. Pinned in Task 3, `completenessCountsEachFieldOncePerRecord`.
2. **A record with several PDFs:** it must count once toward "PDF attached". Pinned in Task 3, same test.
3. **Hosted server in UTC, patient in India:** weeks are grouped in the server zone, and the caption names that zone so the split is explainable. Pinned in Task 1, `theSameInstantCanFallInDifferentWeeksInDifferentZones`, and Task 4, `thePageShowsTheChartsTheConfiguredLimitAndTheServerZone`.
4. **Patient whose records were all added more than 8 weeks ago:** the page still renders, with eight 0 bars, no division by zero, and the ring and categories present. Pinned in Task 1, `anEmptyWindowHasEightZeroBars`, and Task 4, `recordsOlderThanTheWindowLeaveEightEmptyWeeks`.
5. **Very long unbroken names or titles (up to 150 characters, no spaces):** they wrap inside their card with no horizontal page scroll. Pinned by the CSS in Task 4 (`overflow-wrap: anywhere`) and checked visually in Task 5 with such a record.

---

### Task 1: Week window and bucketing (pure)

**Files:**
- Create: `src/main/java/com/medivault/record/DashboardInsights.java` (this task adds only the `WeekBar` part; Task 2 completes the file)
- Create: `src/main/java/com/medivault/record/WeekWindow.java`
- Test: `src/test/java/com/medivault/record/WeekWindowTests.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `record WeekWindow(LocalDate firstWeekStart, ZoneId zone)` (package-private), with:
    - `static WeekWindow endingOn(LocalDate today, ZoneId zone)`
    - `Instant start()` (inclusive)
    - `Instant end()` (exclusive)
    - `List<DashboardInsights.WeekBar> bars(Collection<Instant> createdAt)`
    - constants `WEEKS = 8` and `MAX_BAR_HEIGHT = 100`
  - `public record DashboardInsights.WeekBar(LocalDate weekStart, long count, int height, boolean current)`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/medivault/record/WeekWindowTests.java`:

```java
package com.medivault.record;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.medivault.record.DashboardInsights.WeekBar;

/**
 * The dashboard's eight-week window: Monday-to-Sunday weeks in a given zone, start inclusive and
 * end exclusive, the current (partial) week last.
 */
class WeekWindowTests {

	private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

	private static final ZoneId LONDON = ZoneId.of("Europe/London");

	private static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 14);

	@Test
	void theWindowIsTheCurrentWeekAndTheSevenBeforeIt() {
		WeekWindow window = WeekWindow.endingOn(WEDNESDAY, KOLKATA);

		assertThat(window.firstWeekStart()).isEqualTo(LocalDate.of(2026, 8, 24));
		assertThat(window.start()).isEqualTo(Instant.parse("2026-08-23T18:30:00Z")); // Mon 24 Aug 00:00 IST
		assertThat(window.end()).isEqualTo(Instant.parse("2026-10-18T18:30:00Z")); // Mon 19 Oct 00:00 IST
	}

	@Test
	void mondayAndSundayBelongToTheSameWeek() {
		assertThat(WeekWindow.endingOn(LocalDate.of(2026, 10, 12), KOLKATA).firstWeekStart())
			.isEqualTo(LocalDate.of(2026, 8, 24));
		assertThat(WeekWindow.endingOn(LocalDate.of(2026, 10, 18), KOLKATA).firstWeekStart())
			.isEqualTo(LocalDate.of(2026, 8, 24));
		assertThat(WeekWindow.endingOn(LocalDate.of(2026, 10, 19), KOLKATA).firstWeekStart())
			.isEqualTo(LocalDate.of(2026, 8, 31));
	}

	@Test
	void boundariesAreInclusiveAtTheStartAndExclusiveAtTheEnd() {
		List<WeekBar> bars = WeekWindow.endingOn(WEDNESDAY, KOLKATA).bars(List.of(
				Instant.parse("2026-08-23T18:29:59.999999Z"), // Sun 23 Aug, last microsecond: before the window
				Instant.parse("2026-08-23T18:30:00Z"), // Mon 24 Aug 00:00 IST: first instant of the window
				Instant.parse("2026-10-11T18:29:59.999999Z"), // Sun 11 Oct, last microsecond: week of 5 Oct
				Instant.parse("2026-10-11T18:30:00Z"), // Mon 12 Oct 00:00 IST: current week
				Instant.parse("2026-10-18T18:29:59.999999Z"), // Sun 18 Oct, last microsecond: current week
				Instant.parse("2026-10-18T18:30:00Z"))); // Mon 19 Oct 00:00 IST: after the window

		assertThat(bars).extracting(WeekBar::weekStart)
			.containsExactly(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 7),
					LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28),
					LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12));
		assertThat(bars).extracting(WeekBar::count).containsExactly(1L, 0L, 0L, 0L, 0L, 0L, 1L, 2L);
		assertThat(bars).extracting(WeekBar::current)
			.containsExactly(false, false, false, false, false, false, false, true);
	}

	@Test
	void theSameInstantCanFallInDifferentWeeksInDifferentZones() {
		Instant sundayEveningUtc = Instant.parse("2026-10-11T20:00:00Z"); // Mon 12 Oct 01:30 in Kolkata

		assertThat(WeekWindow.endingOn(WEDNESDAY, ZoneOffset.UTC).bars(List.of(sundayEveningUtc)))
			.extracting(WeekBar::count)
			.containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 1L, 0L);
		assertThat(WeekWindow.endingOn(WEDNESDAY, KOLKATA).bars(List.of(sundayEveningUtc)))
			.extracting(WeekBar::count)
			.containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L);
	}

	@Test
	void aWeekWithAClockChangeIsStillSevenCalendarDays() {
		// UK clocks go back at 02:00 BST on Sunday 25 Oct 2026, so the week of 19 Oct lasts 169 hours.
		WeekWindow window = WeekWindow.endingOn(LocalDate.of(2026, 10, 28), LONDON);

		assertThat(window.firstWeekStart()).isEqualTo(LocalDate.of(2026, 9, 7));
		assertThat(window.start()).isEqualTo(Instant.parse("2026-09-06T23:00:00Z")); // 00:00 BST
		assertThat(window.end()).isEqualTo(Instant.parse("2026-11-02T00:00:00Z")); // 00:00 GMT
		assertThat(window.bars(List.of(Instant.parse("2026-10-18T23:00:00Z"), // Mon 19 Oct 00:00 BST
				Instant.parse("2026-10-25T23:59:59Z"), // Sun 25 Oct 23:59:59 GMT
				Instant.parse("2026-10-26T00:00:00Z")))) // Mon 26 Oct 00:00 GMT
			.extracting(WeekBar::count)
			.containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 2L, 1L);
	}

	@Test
	void barsAreScaledToTheBusiestWeekAndNonEmptyWeeksStayVisible() {
		List<Instant> added = new ArrayList<>();
		for (int i = 0; i < 200; i++) {
			added.add(Instant.parse("2026-10-13T06:00:00Z")); // current week
		}
		added.add(Instant.parse("2026-10-06T06:00:00Z")); // week of 5 Oct: 1 of 200 rounds to 1, shown as 2

		assertThat(WeekWindow.endingOn(WEDNESDAY, KOLKATA).bars(added)).extracting(WeekBar::height)
			.containsExactly(0, 0, 0, 0, 0, 0, 2, 100);
	}

	@Test
	void anEmptyWindowHasEightZeroBars() {
		List<WeekBar> bars = WeekWindow.endingOn(WEDNESDAY, KOLKATA).bars(List.of());

		assertThat(bars).hasSize(WeekWindow.WEEKS);
		assertThat(bars).extracting(WeekBar::count).containsOnly(0L);
		assertThat(bars).extracting(WeekBar::height).containsOnly(0);
	}

}
```

- [ ] **Step 2: Run the tests to verify they fail** (needs approval to run tests)

Run: `.\mvnw.cmd test "-Dtest=WeekWindowTests"`
Expected: BUILD FAILURE, test compilation error "cannot find symbol: class WeekWindow" / "DashboardInsights".

- [ ] **Step 3: Write the minimal implementation**

Create `src/main/java/com/medivault/record/DashboardInsights.java` (first version; Task 2 replaces it with the full record):

```java
package com.medivault.record;

import java.time.LocalDate;

/**
 * Read-only dashboard figures for one patient, computed only from that patient's saved rows.
 */
public final class DashboardInsights {

	private DashboardInsights() {
	}

	/**
	 * One week of the "records added" histogram: its Monday, how many records were added, the bar
	 * height in SVG units (0 to {@link WeekWindow#MAX_BAR_HEIGHT}) and whether it is the current week.
	 */
	public record WeekBar(LocalDate weekStart, long count, int height, boolean current) {
	}

}
```

Create `src/main/java/com/medivault/record/WeekWindow.java`:

```java
package com.medivault.record;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.medivault.record.DashboardInsights.WeekBar;

/**
 * The eight Monday-to-Sunday weeks ending with the week that contains {@code today}, as calendar
 * dates in {@code zone}. The window runs from 00:00 on the first Monday (inclusive) to 00:00 on the
 * Monday after the current week (exclusive), both in {@code zone}, so every week is seven calendar
 * days even when the clocks change.
 */
record WeekWindow(LocalDate firstWeekStart, ZoneId zone) {

	static final int WEEKS = 8;

	static final int MAX_BAR_HEIGHT = 100;

	/** The smallest height of a week with at least one record, so it never disappears. */
	private static final int MIN_VISIBLE_HEIGHT = 2;

	static WeekWindow endingOn(LocalDate today, ZoneId zone) {
		return new WeekWindow(mondayOf(today).minusWeeks(WEEKS - 1), zone);
	}

	Instant start() {
		return firstWeekStart.atStartOfDay(zone).toInstant();
	}

	Instant end() {
		return firstWeekStart.plusWeeks(WEEKS).atStartOfDay(zone).toInstant();
	}

	/** Counts the instants per week; instants outside the window are ignored. Oldest week first. */
	List<WeekBar> bars(Collection<Instant> createdAt) {
		Instant start = start();
		Instant end = end();
		long[] counts = new long[WEEKS];
		for (Instant instant : createdAt) {
			if (instant.isBefore(start) || !instant.isBefore(end)) {
				continue;
			}
			LocalDate weekStart = mondayOf(LocalDate.ofInstant(instant, zone));
			counts[(int) ChronoUnit.WEEKS.between(firstWeekStart, weekStart)]++;
		}
		long busiest = Math.max(1, Arrays.stream(counts).max().orElse(0));
		List<WeekBar> bars = new ArrayList<>(WEEKS);
		for (int i = 0; i < WEEKS; i++) {
			int height = counts[i] == 0 ? 0
					: (int) Math.max(MIN_VISIBLE_HEIGHT, Math.round(counts[i] * (double) MAX_BAR_HEIGHT / busiest));
			bars.add(new WeekBar(firstWeekStart.plusWeeks(i), counts[i], height, i == WEEKS - 1));
		}
		return List.copyOf(bars);
	}

	private static LocalDate mondayOf(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}

}
```

- [ ] **Step 4: Run the tests to verify they pass** (needs approval to run tests)

Run: `.\mvnw.cmd test "-Dtest=WeekWindowTests"`
Expected: `Tests run: 7, Failures: 0, Errors: 0` and BUILD SUCCESS.

- [ ] **Step 5: Commit** (only if Git is authorised for this stage)

```bash
git add src/main/java/com/medivault/record/DashboardInsights.java src/main/java/com/medivault/record/WeekWindow.java src/test/java/com/medivault/record/WeekWindowTests.java
git commit -m "feat(dashboard): add eight-week window with exact zone-aware boundaries

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Completeness, ring geometry, type shares, initials (pure)

**Files:**
- Modify (replace whole file): `src/main/java/com/medivault/record/DashboardInsights.java`
- Test: `src/test/java/com/medivault/record/DashboardInsightsTests.java`

**Interfaces:**
- Consumes: `WeekWindow` and `WeekBar` from Task 1 (`WeekBar` keeps exactly the same components).
- Produces:
  - The main record:
    ```java
    public record DashboardInsights(String initials, LocalDate memberSince, Completeness completeness,
        List<WeekBar> weeks, ZoneId zone, LocalDate earliestRecordDate, MedicalRecord latestRecord,
        LocalDateTime lastAddedAt, LocalDateTime lastUploadAt, int providerCount, List<TypeShare> types)
    ```
    with `public long typesOnFile()` and `static String initials(String fullName)`.
  - `public record Completeness(long total, long withProvider, long withNotes, long withDocument)`, with:
    - `public Integer percent()`, which is `null` when `total == 0`;
    - `public List<RingSegment> segments()`.
  - `public record RingSegment(String label, long count, long total, int index)`, with three methods that each return a `String`: `trackDash()`, `fillDash()` and `offset()`.
  - `public record TypeShare(RecordType type, long count, int percent)`, with `static TypeShare of(RecordType type, long count, long total)`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/medivault/record/DashboardInsightsTests.java`:

```java
package com.medivault.record;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.medivault.record.DashboardInsights.Completeness;
import com.medivault.record.DashboardInsights.RingSegment;
import com.medivault.record.DashboardInsights.TypeShare;

/**
 * Completeness is a paperwork measure: (provider-present + notes-present + PDF-present) ÷ (3 ×
 * records) × 100, rounded down, and undefined without records.
 */
class DashboardInsightsTests {

	@Test
	void completenessIsFilledFieldsOverThreeTimesTheRecordsRoundedDown() {
		assertThat(new Completeness(3, 3, 2, 2).percent()).isEqualTo(77); // 700 ÷ 9 = 77.8
		assertThat(new Completeness(2, 2, 2, 1).percent()).isEqualTo(83); // 500 ÷ 6 = 83.3
		assertThat(new Completeness(4, 4, 4, 4).percent()).isEqualTo(100);
		assertThat(new Completeness(4, 0, 0, 0).percent()).isZero();
	}

	@Test
	void onlyACompletelyFilledVaultReaches100Percent() {
		// 299 of 300 fields is 99.67%: rounding down keeps it below 100%.
		assertThat(new Completeness(100, 100, 100, 99).percent()).isEqualTo(99);
	}

	@Test
	void completenessIsUndefinedWithoutRecords() {
		Completeness none = new Completeness(0, 0, 0, 0);

		assertThat(none.percent()).isNull();
		assertThat(none.segments()).extracting(RingSegment::fillDash).containsOnly("0.00 300.00");
	}

	@Test
	void ringSegmentsSplitTheCircleIntoThreeArcsWithGaps() {
		List<RingSegment> segments = new Completeness(3, 3, 1, 0).segments();

		assertThat(segments).extracting(RingSegment::label)
			.containsExactly("Provider named", "Notes added", "PDF attached");
		assertThat(segments).extracting(RingSegment::trackDash).containsOnly("96.00 204.00");
		assertThat(segments).extracting(RingSegment::offset).containsExactly("-2.00", "-102.00", "-202.00");
		assertThat(segments).extracting(RingSegment::fillDash)
			.containsExactly("96.00 204.00", "32.00 268.00", "0.00 300.00");
	}

	@Test
	void typeSharesRoundHalfUpAndAreZeroWithoutRecords() {
		assertThat(TypeShare.of(RecordType.LAB_REPORT, 1, 3).percent()).isEqualTo(33);
		assertThat(TypeShare.of(RecordType.LAB_REPORT, 2, 3).percent()).isEqualTo(67);
		assertThat(TypeShare.of(RecordType.LAB_REPORT, 1, 8).percent()).isEqualTo(13); // 12.5 rounds up
		assertThat(TypeShare.of(RecordType.LAB_REPORT, 0, 0).percent()).isZero();
	}

	@Test
	void initialsUseTheFirstAndLastNameParts() {
		assertThat(DashboardInsights.initials("Asha Fictional Rao")).isEqualTo("AR");
		assertThat(DashboardInsights.initials("  asha  ")).isEqualTo("A");
		assertThat(DashboardInsights.initials("Émile Zola")).isEqualTo("ÉZ");
		assertThat(DashboardInsights.initials("   ")).isEqualTo("?");
	}

}
```

- [ ] **Step 2: Run the tests to verify they fail** (needs approval to run tests)

Run: `.\mvnw.cmd test "-Dtest=DashboardInsightsTests"`
Expected: BUILD FAILURE, test compilation error "cannot find symbol: class Completeness".

- [ ] **Step 3: Write the implementation**

Replace `src/main/java/com/medivault/record/DashboardInsights.java` with:

```java
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
```

- [ ] **Step 4: Run the tests to verify they pass** (needs approval to run tests)

Run: `.\mvnw.cmd test "-Dtest=DashboardInsightsTests,WeekWindowTests"`
Expected: `Tests run: 13, Failures: 0, Errors: 0` and BUILD SUCCESS.

- [ ] **Step 5: Commit** (only if Git is authorised for this stage)

```bash
git add src/main/java/com/medivault/record/DashboardInsights.java src/test/java/com/medivault/record/DashboardInsightsTests.java
git commit -m "feat(dashboard): define record completeness, ring geometry and type shares

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Owner-scoped queries, DashboardService and controller wiring

**Files:**
- Modify: `src/main/java/com/medivault/record/MedicalRecordRepository.java` (add imports and six methods after `countByTypeForOwner`)
- Modify: `src/main/java/com/medivault/document/RecordDocumentRepository.java` (add one method after `sumSizeBytesForOwner`)
- Create: `src/main/java/com/medivault/record/DashboardService.java`
- Modify: `src/main/java/com/medivault/record/RecordController.java:41-49` (constructor) and `:67-77` (`dashboard`)
- Test: `src/test/java/com/medivault/record/DashboardTests.java` (add fields, helper and five tests)

**Interfaces:**
- Consumes:
  - from Tasks 1–2: `WeekWindow.endingOn/start/end/bars`, the `DashboardInsights` constructor, `DashboardInsights.initials`, `Completeness`, `TypeShare.of`;
  - from existing code: `MedicalRecordService.typeCountsForOwner(Long)` (returns `Map<RecordType, Long>`), `MedicalRecordService.providerNamesForOwner(Long)` (returns `List<String>`), and `RecordDocumentRepository.findRecentForOwner(Long, Pageable)`.
- Produces:
  - `public DashboardInsights DashboardService.insightsFor(Long ownerId)`;
  - `public DashboardInsights DashboardService.insightsFor(Long ownerId, LocalDate today, ZoneId zone)`;
  - the model attribute `insights` on `/dashboard`.

- [ ] **Step 1: Write the failing integration tests**

In `src/test/java/com/medivault/record/DashboardTests.java`:

1. Add these imports, merging them in alphabetical order with the existing ones:

   ```java
   import java.time.LocalDateTime;
   import java.time.OffsetDateTime;
   import java.time.ZoneId;
   import java.time.ZoneOffset;
   import java.util.LinkedHashMap;

   import com.medivault.record.DashboardInsights.Completeness;
   import com.medivault.record.DashboardInsights.TypeShare;
   import com.medivault.record.DashboardInsights.WeekBar;
   ```

2. Add these fields after the existing `jdbc` field:

   ```java
   	private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

   	private static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 14);

   	@Autowired
   	private DashboardService dashboardService;
   ```

3. Add this helper after `upload(...)`:

   ```java
   	/** Sets when a record was "added", as an exact instant; whole seconds only, to suit TIMESTAMP(6). */
   	private void setCreatedAt(MedicalRecord record, Instant createdAt) {
   		jdbc.update("update medical_records set created_at = ? where id = ?",
   				OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC), record.getId());
   	}
   ```

4. Add these tests before the class's closing brace:

```java
	@Test
	void completenessCountsEachFieldOncePerRecord() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord complete = createRecord(asha, "Asha complete record"); // provider and notes
		createRecord(asha, "Asha bare record", LocalDate.now().minusDays(3)); // neither
		// Written outside the form, which would have stored these blanks as null.
		recordRepository.save(new MedicalRecord(asha, "Asha spaces only", RecordType.OTHER,
				LocalDate.now().minusDays(4), "   ", "   "));
		upload(asha, complete, "fictional-one.pdf", FictionalPdf.bytes("Asha first scan"));
		upload(asha, complete, "fictional-two.pdf", FictionalPdf.bytes("Asha second scan"));

		Completeness completeness = dashboardService.insightsFor(asha.getId(), WEDNESDAY, KOLKATA).completeness();

		assertThat(completeness).isEqualTo(new Completeness(3, 1, 1, 1));
		assertThat(completeness.percent()).isEqualTo(33); // (1 + 1 + 1) × 100 ÷ 9 = 33.3, rounded down
	}

	@Test
	void weeklyActivityUsesTheEightWeekWindowInTheGivenZone() {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		Map<String, Instant> added = new LinkedHashMap<>();
		added.put("just before the window", Instant.parse("2026-08-23T18:29:59Z")); // Sun 23 Aug 23:59:59 IST
		added.put("first second of the window", Instant.parse("2026-08-23T18:30:00Z")); // Mon 24 Aug 00:00 IST
		added.put("Sunday night", Instant.parse("2026-10-11T18:29:59Z")); // Sun 11 Oct 23:59:59 IST
		added.put("Monday midnight", Instant.parse("2026-10-11T18:30:00Z")); // Mon 12 Oct 00:00 IST
		added.put("end of the window", Instant.parse("2026-10-18T18:30:00Z")); // Mon 19 Oct 00:00 IST
		added.forEach((label, instant) -> setCreatedAt(createRecord(asha, "Asha " + label), instant));
		setCreatedAt(createRecord(ravi, "Ravi this week"), Instant.parse("2026-10-13T06:00:00Z"));

		List<WeekBar> weeks = dashboardService.insightsFor(asha.getId(), WEDNESDAY, KOLKATA).weeks();

		assertThat(weeks).extracting(WeekBar::weekStart)
			.containsExactly(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 7),
					LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28),
					LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12));
		assertThat(weeks).extracting(WeekBar::count).containsExactly(1L, 0L, 0L, 0L, 0L, 0L, 1L, 1L);
	}

	@Test
	void anotherPatientsDataNeverCountsInTheInsights() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		MedicalRecord ashaRecord = createRecord(asha, "Asha older report", LocalDate.of(2026, 9, 1));
		MedicalRecord raviRecord = recordRepository.save(new MedicalRecord(ravi, "Ravi private report",
				RecordType.IMAGING, LocalDate.of(2026, 10, 10), "Ravi Fictional Clinic", "Ravi fictional notes"));
		upload(ravi, raviRecord, "ravi-private.pdf", FictionalPdf.bytes("Ravi private scan"));
		setCreatedAt(ashaRecord, Instant.parse("2026-10-13T06:00:00Z"));
		setCreatedAt(raviRecord, Instant.parse("2026-10-13T07:00:00Z"));

		DashboardInsights insights = dashboardService.insightsFor(asha.getId(), WEDNESDAY, KOLKATA);

		assertThat(insights.completeness()).isEqualTo(new Completeness(1, 0, 0, 0));
		assertThat(insights.weeks()).extracting(WeekBar::count).containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L);
		assertThat(insights.latestRecord().getTitle()).isEqualTo("Asha older report");
		assertThat(insights.earliestRecordDate()).isEqualTo(LocalDate.of(2026, 9, 1));
		assertThat(insights.lastAddedAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 11, 30));
		assertThat(insights.lastUploadAt()).isNull();
		assertThat(insights.providerCount()).isZero();
		assertThat(insights.types()).filteredOn(share -> share.count() > 0)
			.extracting(TypeShare::type)
			.containsExactly(RecordType.PRESCRIPTION);
	}

	@Test
	void latestRecordFollowsRecordDateThenAddedTimeThenId() {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		LocalDate sameDay = LocalDate.of(2026, 10, 1);
		MedicalRecord first = createRecord(asha, "Same day, added first", sameDay);
		MedicalRecord second = createRecord(asha, "Same day, added later", sameDay);
		MedicalRecord third = createRecord(asha, "Same day, same instant, higher id", sameDay);
		MedicalRecord older = createRecord(asha, "Older date, added last", LocalDate.of(2025, 5, 1));
		setCreatedAt(first, Instant.parse("2026-10-02T08:00:00Z"));
		setCreatedAt(second, Instant.parse("2026-10-03T08:00:00Z"));
		setCreatedAt(third, Instant.parse("2026-10-03T08:00:00Z"));
		setCreatedAt(older, Instant.parse("2026-10-05T08:00:00Z"));

		DashboardInsights insights = dashboardService.insightsFor(asha.getId(), WEDNESDAY, KOLKATA);

		assertThat(third.getId()).isGreaterThan(second.getId());
		assertThat(insights.latestRecord().getTitle()).isEqualTo("Same day, same instant, higher id");
		assertThat(insights.lastAddedAt()).isEqualTo(LocalDateTime.of(2026, 10, 5, 13, 30));
		assertThat(insights.earliestRecordDate()).isEqualTo(LocalDate.of(2025, 5, 1));
	}

	@Test
	void aPatientWithoutRecordsGetsEmptyInsights() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");

		DashboardInsights insights = dashboardService.insightsFor(asha.getId(), WEDNESDAY, KOLKATA);

		assertThat(insights.completeness()).isEqualTo(new Completeness(0, 0, 0, 0));
		assertThat(insights.completeness().percent()).isNull();
		assertThat(insights.weeks()).hasSize(8).extracting(WeekBar::count).containsOnly(0L);
		assertThat(insights.latestRecord()).isNull();
		assertThat(insights.earliestRecordDate()).isNull();
		assertThat(insights.lastAddedAt()).isNull();
		assertThat(insights.lastUploadAt()).isNull();
		assertThat(insights.providerCount()).isZero();
		assertThat(insights.types()).extracting(TypeShare::type).containsExactly(RecordType.values());
		assertThat(insights.types()).extracting(TypeShare::count).containsOnly(0L);
		assertThat(insights.initials()).isEqualTo("AF");
		assertThat(insights.memberSince()).isEqualTo(LocalDate
			.ofInstant(userRepository.findById(asha.getId()).orElseThrow().getCreatedAt(), KOLKATA));
		assertThat(dashboardModel(asha).get("insights")).isInstanceOf(DashboardInsights.class);
	}
```

- [ ] **Step 2: Run the tests to verify they fail** (needs approval to run tests)

Run: `.\mvnw.cmd test "-Dtest=DashboardTests"`
Expected: BUILD FAILURE, test compilation error "cannot find symbol: class DashboardService".

- [ ] **Step 3: Add the repository queries**

In `MedicalRecordRepository.java`, add `import java.time.Instant;`, then insert after `countByTypeForOwner(...)`:

```java
	/** Records naming a provider; a name of spaces only does not count. */
	@Query("select count(r) from MedicalRecord r where r.owner.id = :ownerId "
			+ "and r.providerName is not null and trim(r.providerName) <> ''")
	long countWithProviderForOwner(@Param("ownerId") Long ownerId);

	/** Records with notes; notes of spaces only do not count. */
	@Query("select count(r) from MedicalRecord r where r.owner.id = :ownerId "
			+ "and r.notes is not null and trim(r.notes) <> ''")
	long countWithNotesForOwner(@Param("ownerId") Long ownerId);

	/** When each of the owner's records added in {@code [from, to)} was added. */
	@Query("select r.createdAt from MedicalRecord r where r.owner.id = :ownerId "
			+ "and r.createdAt >= :from and r.createdAt < :to")
	List<Instant> findCreatedAtForOwnerBetween(@Param("ownerId") Long ownerId, @Param("from") Instant from,
			@Param("to") Instant to);

	/** The record with the earliest record date; the id breaks ties. */
	Optional<MedicalRecord> findFirstByOwnerIdOrderByRecordDateAscIdAsc(Long ownerId);

	/** The record with the latest record date; ties go to the most recently added, then the highest id. */
	Optional<MedicalRecord> findFirstByOwnerIdOrderByRecordDateDescCreatedAtDescIdDesc(Long ownerId);

	/** The most recently added record; the id breaks ties between identical timestamps. */
	Optional<MedicalRecord> findFirstByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);
```

In `RecordDocumentRepository.java`, insert after `sumSizeBytesForOwner(...)`:

```java
	/** How many of the owner's records have at least one document (a record with several counts once). */
	@Query("select count(distinct d.record.id) from RecordDocument d where d.record.owner.id = :ownerId")
	long countRecordsWithDocumentsForOwner(@Param("ownerId") Long ownerId);
```

- [ ] **Step 4: Add DashboardService**

Create `src/main/java/com/medivault/record/DashboardService.java`:

```java
package com.medivault.record;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medivault.document.RecordDocumentRepository;
import com.medivault.record.DashboardInsights.Completeness;
import com.medivault.record.DashboardInsights.TypeShare;
import com.medivault.user.User;
import com.medivault.user.UserRepository;

/**
 * Read-only figures for one patient's dashboard. Every query is scoped by the owner id, which
 * callers must take from the authenticated principal. Nothing here writes.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

	private final MedicalRecordRepository recordRepository;

	private final RecordDocumentRepository documentRepository;

	private final UserRepository userRepository;

	private final MedicalRecordService recordService;

	public DashboardService(MedicalRecordRepository recordRepository, RecordDocumentRepository documentRepository,
			UserRepository userRepository, MedicalRecordService recordService) {
		this.recordRepository = recordRepository;
		this.documentRepository = documentRepository;
		this.userRepository = userRepository;
		this.recordService = recordService;
	}

	/** The insights as of today, with weeks, dates and times in the server's time zone. */
	public DashboardInsights insightsFor(Long ownerId) {
		ZoneId zone = ZoneId.systemDefault();
		return insightsFor(ownerId, LocalDate.now(zone), zone);
	}

	/** The insights as of {@code today} in {@code zone}; tests use this to fix the date and zone. */
	public DashboardInsights insightsFor(Long ownerId, LocalDate today, ZoneId zone) {
		User owner = userRepository.findById(ownerId).orElseThrow(RecordNotFoundException::new);
		long total = recordRepository.countByOwnerId(ownerId);
		Completeness completeness = new Completeness(total, recordRepository.countWithProviderForOwner(ownerId),
				recordRepository.countWithNotesForOwner(ownerId),
				documentRepository.countRecordsWithDocumentsForOwner(ownerId));
		WeekWindow window = WeekWindow.endingOn(today, zone);
		Map<RecordType, Long> typeCounts = recordService.typeCountsForOwner(ownerId);
		List<TypeShare> types = Arrays.stream(RecordType.values())
			.map(type -> TypeShare.of(type, typeCounts.getOrDefault(type, 0L), total))
			.toList();
		int providerCount = (int) recordService.providerNamesForOwner(ownerId)
			.stream()
			.filter(name -> !name.isBlank())
			.count();
		return new DashboardInsights(DashboardInsights.initials(owner.getFullName()),
				LocalDate.ofInstant(owner.getCreatedAt(), zone), completeness,
				window.bars(recordRepository.findCreatedAtForOwnerBetween(ownerId, window.start(), window.end())),
				zone,
				recordRepository.findFirstByOwnerIdOrderByRecordDateAscIdAsc(ownerId)
					.map(MedicalRecord::getRecordDate)
					.orElse(null),
				recordRepository.findFirstByOwnerIdOrderByRecordDateDescCreatedAtDescIdDesc(ownerId).orElse(null),
				recordRepository.findFirstByOwnerIdOrderByCreatedAtDescIdDesc(ownerId)
					.map(record -> LocalDateTime.ofInstant(record.getCreatedAt(), zone))
					.orElse(null),
				documentRepository.findRecentForOwner(ownerId, PageRequest.of(0, 1))
					.stream()
					.findFirst()
					.map(upload -> LocalDateTime.ofInstant(upload.getUploadedAt(), zone))
					.orElse(null),
				providerCount, types);
	}

}
```

- [ ] **Step 5: Wire the controller**

In `RecordController.java`, replace the field block and constructor (lines 41–49) with:

```java
	private final MedicalRecordService recordService;

	private final RecordDocumentService documentService;

	private final DashboardService dashboardService;

	public RecordController(MedicalRecordService recordService, RecordDocumentService documentService,
			DashboardService dashboardService) {
		this.recordService = recordService;
		this.documentService = documentService;
		this.dashboardService = dashboardService;
	}
```

In `dashboard(...)`, add this line directly before `return "dashboard";`:

```java
		model.addAttribute("insights", dashboardService.insightsFor(ownerId));
```

- [ ] **Step 6: Run the tests to verify they pass** (needs approval to run tests)

Run: `.\mvnw.cmd test "-Dtest=DashboardTests,DashboardInsightsTests,WeekWindowTests"`
Expected: all pass (the 5 existing + 5 new `DashboardTests`, plus 13 unit tests) and BUILD SUCCESS. The existing dashboard tests must still pass unchanged.

- [ ] **Step 7: Commit** (only if Git is authorised for this stage)

```bash
git add src/main/java/com/medivault/record/MedicalRecordRepository.java src/main/java/com/medivault/document/RecordDocumentRepository.java src/main/java/com/medivault/record/DashboardService.java src/main/java/com/medivault/record/RecordController.java src/test/java/com/medivault/record/DashboardTests.java
git commit -m "feat(dashboard): compute owner-scoped completeness, weekly activity and highlights

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Dashboard template and stylesheet

**Files:**
- Modify (replace whole file): `src/main/resources/templates/dashboard.html`
- Modify: `src/main/resources/static/css/main.css`. The changes are:
  - add four tokens in `:root`;
  - widen the meter selectors at lines 721–749;
  - add a dashboard section before `@media (max-width: 40rem)` at line 813;
  - extend the fallback blocks at lines 1061–1130.
- Test: `src/test/java/com/medivault/record/DashboardTests.java` (add three tests)

**Interfaces:**
- Consumes: the `insights` model attribute (Task 3) and the existing `fullName`, `recordCount`, `recentRecords`, `storage` and `recentDocuments`.
- Produces: no Java interface. The CSS classes used only by this template are:
  - layout and cards: `dashboard`, `dash-grid`, `stat-card`, `summary-card`, `completeness-card`, `weekly-card`, `types-card`, `recent-card`, `dash-welcome`;
  - completeness ring: `ring*`, `legend-*`;
  - histogram: `histogram*`;
  - utility: `visually-hidden`.

- [ ] **Step 1: Write the failing page tests**

Add these tests to `DashboardTests.java`, which already imports `StorageUsage`, `ZoneId` and the Hamcrest matchers:

```java
	@Test
	void thePageShowsTheChartsTheConfiguredLimitAndTheServerZone() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		createRecord(asha, "Asha lipid profile");
		StorageUsage storage = (StorageUsage) dashboardModel(asha).get("storage");

		mockMvc.perform(get("/dashboard").with(user(principal(asha))))
			.andExpect(content().string(allOf(containsString("Welcome, Asha Fictional"),
					containsString("Record completeness"), containsString("not a health score."),
					containsString("Records added per week"), containsString("Records by type"),
					containsString(storage.usedLabel() + " of " + storage.quotaLabel() + " upload limit used"),
					containsString("Source: your attached PDFs (original file sizes)"),
					containsString("server time zone " + ZoneId.systemDefault().getId()),
					containsString("Only your account can open these records."),
					not(containsString("You don't have any medical records yet.")))));
	}

	@Test
	void theEmptyDashboardHasNoChartsOrInventedFigures() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(get("/dashboard").with(user(principal(asha))))
			.andExpect(content().string(allOf(containsString("You don't have any medical records yet."),
					containsString("href=\"/records/new\">Add your first record</a>"),
					containsString("No records yet"), containsString("No PDFs yet"),
					not(containsString("Record completeness")), not(containsString("Records added per week")),
					not(containsString("Records span")), not(containsString("null")))));
	}

	@Test
	void recordsOlderThanTheWindowLeaveEightEmptyWeeks() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord old = createRecord(asha, "Asha old record");
		setCreatedAt(old, Instant.now().minus(java.time.Duration.ofDays(70)));

		Map<String, Object> model = dashboardModel(asha);

		assertThat(((DashboardInsights) model.get("insights")).weeks()).extracting(WeekBar::count).containsOnly(0L);
		mockMvc.perform(get("/dashboard").with(user(principal(asha))))
			.andExpect(content().string(allOf(containsString("Record completeness"),
					containsString("Records added per week"), containsString("this week, so far"))));
	}
```

`recordsOlderThanTheWindowLeaveEightEmptyWeeks` writes a fractional-second instant. That's fine, because it only asserts that the record falls outside the window, not an exact boundary.

- [ ] **Step 2: Run the tests to verify they fail** (needs approval to run tests)

Run: `.\mvnw.cmd test "-Dtest=DashboardTests"`
Expected: the 3 new tests FAIL. The old template lacks "Record completeness", "upload limit used", "No records yet" and so on.

- [ ] **Step 3: Replace the template**

Replace `src/main/resources/templates/dashboard.html` with:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layout :: layout(~{::title}, ~{::main})}">
<head>
    <title>Dashboard &middot; MediVault</title>
</head>
<body>
<main id="main-content" tabindex="-1" class="container dashboard"
      th:with="completeness=${insights.completeness}, hasRecords=${insights.completeness.total > 0}">
    <div class="page-header page-header-actions">
        <div>
            <p class="eyebrow">Dashboard</p>
            <h1 th:text="|Welcome, ${fullName}|">Welcome, Patient</h1>
            <p class="muted page-lead">Every figure below is counted from records you saved.</p>
        </div>
        <a class="button" th:href="@{/records/new}">Add a record</a>
    </div>

    <div class="dash-grid">
        <section class="card card-stat stat-card" aria-labelledby="count-title">
            <h2 id="count-title" class="card-label">Records stored</h2>
            <p class="stat"><span th:text="${recordCount}">0</span><span class="stat-unit"
                    th:text="${recordCount == 1} ? 'record' : 'records'">records</span></p>
            <p class="stat-line" th:if="${insights.lastAddedAt != null}">Last added
                <time th:datetime="|${#temporals.format(insights.lastAddedAt, 'yyyy-MM-dd')}T${#temporals.format(insights.lastAddedAt, 'HH:mm')}|"
                      th:text="${#temporals.format(insights.lastAddedAt, 'd MMM yyyy, HH:mm')}">14 Oct 2026, 09:30</time></p>
            <p class="stat-line" th:if="${insights.lastAddedAt == null}">No records yet</p>
            <p class="stat-source">Source: your saved records</p>
        </section>

        <section class="card card-stat stat-card" aria-labelledby="documents-title">
            <h2 id="documents-title" class="card-label">Documents</h2>
            <p class="stat"><span th:text="${storage.documentCount}">0</span><span class="stat-unit"
                    th:text="${storage.documentCount == 1} ? 'PDF' : 'PDFs'">PDFs</span></p>
            <meter class="storage-meter" min="0" th:max="${storage.quotaBytes}" th:value="${storage.usedBytes}"
                   aria-hidden="true"></meter>
            <p class="stat-line" th:text="|${storage.usedLabel()} of ${storage.quotaLabel()} upload limit used|">
                0 bytes of 100 MB upload limit used</p>
            <p class="stat-line" th:if="${insights.lastUploadAt != null}">Last upload
                <time th:datetime="|${#temporals.format(insights.lastUploadAt, 'yyyy-MM-dd')}T${#temporals.format(insights.lastUploadAt, 'HH:mm')}|"
                      th:text="${#temporals.format(insights.lastUploadAt, 'd MMM yyyy, HH:mm')}">12 Oct 2026, 18:05</time></p>
            <p class="stat-line" th:if="${insights.lastUploadAt == null}">No PDFs yet</p>
            <p class="stat-source">Source: your attached PDFs (original file sizes)</p>
        </section>

        <section class="card card-stat stat-card" aria-labelledby="latest-title">
            <h2 id="latest-title" class="card-label">Latest record</h2>
            <th:block th:if="${insights.latestRecord != null}">
                <p class="stat stat-date"><time th:datetime="${insights.latestRecord.recordDate}"
                        th:text="${#temporals.format(insights.latestRecord.recordDate, 'd MMM yyyy')}">3 Oct 2026</time></p>
                <p class="stat-line"><span th:text="${insights.latestRecord.recordType.displayName}">Lab report</span>
                    &middot; <a th:href="@{/records/{id}(id=${insights.latestRecord.id})}"
                                th:text="${insights.latestRecord.title}">Lipid profile</a></p>
            </th:block>
            <th:block th:if="${insights.latestRecord == null}">
                <p class="stat">&mdash;</p>
                <p class="stat-line">No records yet</p>
            </th:block>
            <p class="stat-source">Source: record date as you entered it</p>
        </section>

        <section class="card summary-card" aria-labelledby="summary-title">
            <h2 id="summary-title" class="card-label">Patient summary</h2>
            <div class="summary-identity">
                <span class="initials" aria-hidden="true" th:text="${insights.initials}">AF</span>
                <div>
                    <p class="summary-name" th:text="${fullName}">Asha Fictional</p>
                    <p class="summary-role">Patient account</p>
                </div>
            </div>
            <dl class="summary-facts">
                <div>
                    <dt>Member since</dt>
                    <dd><time th:datetime="${insights.memberSince}"
                              th:text="${#temporals.format(insights.memberSince, 'd MMM yyyy')}">1 Oct 2026</time></dd>
                </div>
                <div th:if="${insights.latestRecord != null and insights.earliestRecordDate != null}">
                    <dt>Records span</dt>
                    <dd><time th:datetime="${insights.earliestRecordDate}"
                              th:text="${#temporals.format(insights.earliestRecordDate, 'd MMM yyyy')}">1 Jan 2024</time>
                        &ndash;
                        <time th:datetime="${insights.latestRecord.recordDate}"
                              th:text="${#temporals.format(insights.latestRecord.recordDate, 'd MMM yyyy')}">3 Oct 2026</time></dd>
                </div>
                <div th:if="${hasRecords}">
                    <dt>Providers named</dt>
                    <dd th:text="${insights.providerCount}">3</dd>
                </div>
                <div th:if="${hasRecords}">
                    <dt>Record types on file</dt>
                    <dd th:text="|${insights.typesOnFile()} of ${insights.types.size()}|">4 of 6</dd>
                </div>
            </dl>
            <p class="summary-privacy">Only your account can open these records. New PDF uploads are stored
                encrypted.</p>
        </section>

        <section class="card dash-welcome" th:if="${!hasRecords}" aria-labelledby="welcome-title">
            <h2 id="welcome-title" class="card-label">Start your vault</h2>
            <div class="empty-state">
                <p>You don't have any medical records yet.</p>
                <p><a class="button" th:href="@{/records/new}">Add your first record</a></p>
            </div>
        </section>

        <section class="card completeness-card" th:if="${hasRecords}" aria-labelledby="completeness-title">
            <h2 id="completeness-title" class="card-label">Record completeness</h2>
            <div class="ring-layout">
                <svg class="ring" viewBox="0 0 120 120" role="img"
                     th:aria-label="|Overall record completeness ${completeness.percent()} percent|">
                    <g transform="rotate(-90 60 60)">
                        <th:block th:each="segment, segmentStat : ${completeness.segments()}">
                            <circle class="ring-track" cx="60" cy="60" r="48" pathLength="300"
                                    th:stroke-dasharray="${segment.trackDash()}"
                                    th:stroke-dashoffset="${segment.offset()}"></circle>
                            <circle th:class="|ring-fill ring-fill-${segmentStat.count}|" cx="60" cy="60" r="48"
                                    pathLength="300" th:stroke-dasharray="${segment.fillDash()}"
                                    th:stroke-dashoffset="${segment.offset()}"></circle>
                        </th:block>
                    </g>
                    <text class="ring-value" x="60" y="60" text-anchor="middle" dominant-baseline="central"
                          th:text="|${completeness.percent()}%|">67%</text>
                </svg>
                <ul class="ring-legend">
                    <li th:each="segment, segmentStat : ${completeness.segments()}">
                        <span th:class="|legend-swatch legend-swatch-${segmentStat.count}|" aria-hidden="true"></span>
                        <span th:text="${segment.label}">Provider named</span>
                        <span class="legend-value" th:text="|${segment.count} of ${segment.total}|">8 of 12</span>
                    </li>
                </ul>
            </div>
            <p class="chart-caption">Paperwork completeness &mdash; not a health score.</p>
        </section>

        <section class="card weekly-card" th:if="${hasRecords}" aria-labelledby="weekly-title">
            <h2 id="weekly-title" class="card-label">Records added per week</h2>
            <svg class="histogram" viewBox="0 0 320 150" aria-hidden="true" focusable="false">
                <line class="histogram-axis" x1="0" y1="120.5" x2="320" y2="120.5"></line>
                <g th:each="week, weekStat : ${insights.weeks}">
                    <rect class="histogram-bar" width="24" rx="3"
                          th:x="${weekStat.index * 40 + 8}" th:y="${120 - week.height}"
                          th:height="${week.height}"></rect>
                    <text class="histogram-count" text-anchor="middle"
                          th:x="${weekStat.index * 40 + 20}" th:y="${114 - week.height}"
                          th:text="${week.count}">0</text>
                    <text class="histogram-label" text-anchor="middle" y="140"
                          th:x="${weekStat.index * 40 + 20}"
                          th:text="${#temporals.format(week.weekStart, 'd MMM')}">6 Oct</text>
                </g>
            </svg>
            <table class="visually-hidden">
                <caption>Records added per week, last 8 weeks, oldest first</caption>
                <thead>
                <tr>
                    <th scope="col">Week starting</th>
                    <th scope="col">Records added</th>
                </tr>
                </thead>
                <tbody>
                <tr th:each="week : ${insights.weeks}">
                    <th scope="row"><span th:text="${#temporals.format(week.weekStart, 'd MMM yyyy')}">6 Oct 2026</span><span
                            th:if="${week.current}"> (this week, so far)</span></th>
                    <td th:text="${week.count}">0</td>
                </tr>
                </tbody>
            </table>
            <p class="chart-caption"
               th:text="|By date added to MediVault, not the record date. Weeks start Monday (server time zone ${insights.zone.id}).|">
                By date added to MediVault, not the record date. Weeks start Monday (server time zone UTC).</p>
        </section>

        <section class="card types-card" th:if="${hasRecords}" aria-labelledby="types-title">
            <h2 id="types-title" class="card-label">Records by type</h2>
            <ul class="type-counts">
                <li th:each="share : ${insights.types}" th:classappend="${share.count == 0} ? 'type-empty'">
                    <a th:href="@{/records(type=${share.type})}" th:text="${share.type.displayName}">Lab report</a>
                    <meter min="0" th:max="${recordCount}" th:value="${share.count}" aria-hidden="true"></meter>
                    <span class="type-figure"><span th:text="${share.count}">2</span>
                        <span class="muted" th:text="|${share.percent}%|">17%</span></span>
                </li>
            </ul>
        </section>

        <section class="card recent-card" th:if="${hasRecords}" aria-labelledby="recent-title">
            <h2 id="recent-title" class="card-label">Recently added</h2>
            <ul class="record-list">
                <li th:each="record : ${recentRecords}">
                    <a th:href="@{/records/{id}(id=${record.id})}" th:text="${record.title}">Record title</a>
                    <span class="record-meta">
                        <span th:text="${record.recordType.displayName}">Type</span> &middot;
                        <time th:datetime="${record.recordDate}"
                              th:text="${#temporals.format(record.recordDate, 'd MMM yyyy')}">1 Jan 2026</time>
                    </span>
                </li>
            </ul>
            <p><a th:href="@{/records}">View all your records</a></p>
        </section>

        <section class="card recent-card" th:if="${hasRecords}" aria-labelledby="uploads-title">
            <h2 id="uploads-title" class="card-label">Recently uploaded</h2>
            <ul class="record-list" th:if="${!recentDocuments.isEmpty()}">
                <li th:each="document : ${recentDocuments}">
                    <a th:href="@{/records/{id}(id=${document.recordId})}" th:text="${document.filename}">report.pdf</a>
                    <span class="record-meta">
                        <span th:text="${document.recordTitle}">Record title</span> &middot;
                        <span th:text="${document.sizeLabel}">12 KB</span> &middot;
                        <time th:datetime="${document.uploadedAt}"
                              th:text="${#temporals.format(document.uploadedAt, 'd MMM yyyy')}">1 Jan 2026</time>
                    </span>
                </li>
            </ul>
            <p class="muted" th:if="${recentDocuments.isEmpty()}">No PDFs yet. Open a record to attach one.</p>
        </section>
    </div>
</main>
</body>
</html>
```

- [ ] **Step 4: Add the colour tokens**

In `main.css` `:root`, insert after the `--color-banner` line (line 32):

```css
  --color-data-1: #0b5f7a;         /* chart marks: 7.2:1 on white */
  --color-data-2: #0e7c86;         /* 4.9:1 on white */
  --color-data-3: #2a9d8f;         /* 3.3:1 on white, 3.0:1 on --color-data-track (marks need 3:1) */
  --color-data-track: #eef5f8;     /* decorative track behind chart marks */
```

- [ ] **Step 5: Reuse the teal meter style for the storage meter**

In `main.css`, change the four selector lines of the existing meter rules (lines 721, 732, 739–741, 746) to:

```css
.type-counts meter,
.storage-meter {
```
```css
.type-counts meter::-webkit-meter-bar,
.storage-meter::-webkit-meter-bar {
```
```css
.type-counts meter::-webkit-meter-optimum-value,
.type-counts meter::-webkit-meter-suboptimum-value,
.type-counts meter::-webkit-meter-even-less-good-value,
.storage-meter::-webkit-meter-optimum-value,
.storage-meter::-webkit-meter-suboptimum-value,
.storage-meter::-webkit-meter-even-less-good-value {
```
```css
.type-counts meter::-moz-meter-bar,
.storage-meter::-moz-meter-bar {
```

Leave the declarations inside these rules unchanged.

- [ ] **Step 6: Add the dashboard section**

In `main.css`, insert directly before `@media (max-width: 40rem) {` (line 813):

```css
/* ---------- Dashboard ---------- */

.dashboard {
  max-width: 76rem;
}

.page-lead {
  margin: 0;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
}

.dash-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-md);
  margin-bottom: var(--space-md);
}

/* Long unbroken names and titles wrap inside their card instead of widening the page. */
.dash-grid > * {
  min-width: 0;
  overflow-wrap: anywhere;
}

.dash-grid .card {
  display: flex;
  flex-direction: column;
}

.dash-grid .card-stat {
  align-self: stretch;
}

.card-label {
  margin: 0 0 var(--space-md);
  font-size: 1rem;
  font-weight: 600;
  letter-spacing: 0;
}

.stat,
.legend-value,
.type-figure,
.summary-facts dd,
.histogram text,
.ring-value {
  font-variant-numeric: tabular-nums;
}

.stat-unit {
  margin-left: 0.35rem;
  font-size: 1rem;
  font-weight: 600;
  letter-spacing: 0;
  color: var(--color-muted);
}

.stat-date {
  font-size: 1.75rem;
}

.stat-line {
  margin: var(--space-sm) 0 0;
  font-size: 0.9rem;
}

.storage-meter {
  width: 100%;
  height: 0.5rem;
  margin-top: var(--space-sm);
}

.stat-source {
  margin-top: auto;
  padding-top: var(--space-md);
  font-size: 0.8125rem;
  color: var(--color-muted);
}

/* Patient summary: the one navy surface on the page. Text colours are documented on their tokens. */
.summary-card {
  color: #ffffff;
  background: var(--color-navy-soft);
  border-color: var(--color-navy);
}

.summary-card .card-label,
.summary-role,
.summary-facts dt,
.summary-privacy {
  color: var(--color-on-dark-muted);
}

.summary-identity {
  display: flex;
  gap: var(--space-md);
  align-items: center;
  margin-bottom: var(--space-lg);
}

.initials {
  flex: none;
  display: grid;
  place-items: center;
  width: 3rem;
  height: 3rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--color-accent);
  background: rgba(94, 234, 212, 0.12);
  border: 1px solid rgba(94, 234, 212, 0.45);
  border-radius: 50%;
}

.summary-name {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 600;
  line-height: 1.3;
}

.summary-role {
  margin: 0;
  font-size: 0.875rem;
}

.summary-facts {
  margin: 0 0 var(--space-lg);
}

.summary-facts div {
  padding-block: var(--space-sm);
  border-top: 1px solid rgba(255, 255, 255, 0.12);
}

.summary-facts dt {
  font-size: 0.8125rem;
}

.summary-facts dd {
  margin: 0;
  font-weight: 600;
}

.summary-privacy {
  margin-top: auto;
  font-size: 0.8125rem;
}

/* Completeness ring: three arcs on one circle (pathLength 300), positioned by dash offsets. */
.ring-layout {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-lg);
  align-items: center;
}

.ring {
  flex: none;
  width: 8.5rem;
  height: 8.5rem;
}

.ring-track,
.ring-fill {
  fill: none;
  stroke-width: 12;
}

.ring-track { stroke: var(--color-data-track); }
.ring-fill-1 { stroke: var(--color-data-1); }
.ring-fill-2 { stroke: var(--color-data-2); }
.ring-fill-3 { stroke: var(--color-data-3); }

.ring-value {
  font-size: 24px;
  font-weight: 700;
  fill: var(--color-navy);
}

.ring-legend {
  flex: 1 1 10rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.ring-legend li {
  display: grid;
  grid-template-columns: 0.75rem minmax(0, 1fr) auto;
  gap: var(--space-sm);
  align-items: center;
  padding-block: 0.3rem;
  font-size: 0.9rem;
}

.legend-swatch {
  width: 0.75rem;
  height: 0.75rem;
  border-radius: 3px;
}

.legend-swatch-1 { background: var(--color-data-1); }
.legend-swatch-2 { background: var(--color-data-2); }
.legend-swatch-3 { background: var(--color-data-3); }

.legend-value {
  font-weight: 600;
}

.chart-caption {
  margin: var(--space-md) 0 0;
  font-size: 0.8125rem;
  color: var(--color-muted);
}

/* Weekly histogram: bars and labels are drawn in a 320 x 150 viewBox that scales with the card. */
.histogram {
  display: block;
  width: 100%;
  height: auto;
}

.histogram-axis {
  stroke: var(--color-border);
  stroke-width: 1;
}

.histogram-bar {
  fill: var(--color-data-1);
}

.histogram-count {
  font-size: 12px;
  font-weight: 600;
  fill: var(--color-text);
}

.histogram-label {
  font-size: 11px;
  fill: var(--color-muted);
}

.dash-grid .type-counts li {
  grid-template-columns: minmax(0, 1fr) 5rem 4.5rem;
}

.type-figure {
  text-align: right;
}

.type-empty a {
  color: var(--color-muted);
}

@media (min-width: 40rem) {
  .dash-grid {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }

  .dash-grid > * {
    grid-column: span 3;
  }

  .dash-grid > .stat-card {
    grid-column: span 2;
  }

  /* Charts get the full width on tablets so the histogram labels stay legible. */
  .dash-grid > .summary-card,
  .dash-grid > .completeness-card,
  .dash-grid > .weekly-card,
  .dash-grid > .types-card,
  .dash-grid > .dash-welcome {
    grid-column: 1 / -1;
  }
}

/* The summary is placed explicitly (columns 1-3, rows 1-2); everything else flows around it in
   source order. It holds no links, so this does not change the keyboard focus order. */
@media (min-width: 66rem) {
  .dash-grid {
    grid-template-columns: repeat(12, minmax(0, 1fr));
  }

  .dash-grid > * {
    grid-column: span 4;
  }

  .dash-grid > .stat-card {
    grid-column: span 3;
  }

  .dash-grid > .summary-card {
    grid-column: 1 / span 3;
    grid-row: 1 / span 2;
  }

  .dash-grid > .completeness-card {
    grid-column: span 4;
  }

  .dash-grid > .weekly-card {
    grid-column: span 5;
  }

  .dash-grid > .types-card {
    grid-column: span 4;
  }

  .dash-grid > .dash-welcome {
    grid-column: 4 / -1;
  }
}

```

- [ ] **Step 7: Extend the fallbacks**

In the existing `@media (prefers-contrast: more)` block (line 1097), add after its existing rule:

```css
  .histogram-axis {
    stroke: var(--color-control-border);
  }
```

In the existing `@media (forced-colors: active)` block (line 1109), add after the `.button` rule:

```css
  .summary-card {
    color: CanvasText;
    background: Canvas;
  }

  .ring-track {
    stroke: GrayText;
  }

  .ring-fill {
    stroke: Highlight;
  }

  .histogram-bar {
    fill: Highlight;
  }

  .ring-value,
  .histogram text {
    fill: CanvasText;
  }

  .legend-swatch {
    border: 1px solid CanvasText;
  }
```

- [ ] **Step 8: Run the dashboard and CSP tests** (needs approval to run tests)

Run: `.\mvnw.cmd test "-Dtest=DashboardTests,TemplateCspComplianceTests,AccessControlTests,DocumentDeletionTests,RecordOwnershipTests"`
Expected: all pass and BUILD SUCCESS.
- `TemplateCspComplianceTests` proves the template has no inline style, script or handler.
- `AccessControlTests` still finds "Welcome, Asha Fictional".
- `DocumentDeletionTests` still finds "Records by type".
- `RecordOwnershipTests` still finds the empty-state text.

- [ ] **Step 9: Commit** (only if Git is authorised for this stage)

```bash
git add src/main/resources/templates/dashboard.html src/main/resources/static/css/main.css src/test/java/com/medivault/record/DashboardTests.java
git commit -m "feat(ui): redesign the patient dashboard with vault stats, completeness and weekly activity

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Full verification (no code changes expected)

**Files:** none. If a check fails, fix the cause in the task that owns that file, and re-run that task's tests and then this task.

**Interfaces:** consumes the finished dashboard; produces a verification report.

- [ ] **Step 1: Full test suite** (needs approval to run tests)

Run: `.\mvnw.cmd test`
Expected: BUILD SUCCESS with 0 failures and 0 errors. Report the exact `Tests run:` totals from the output. MySQL integration tests may be skipped without a MySQL 8 instance; report them as skipped, not passed.

- [ ] **Step 2: Offline visual check** (needs approval to build and launch the app and headless Chrome)

Follow the recorded MediVault method:
- Start the app with the `local-h2` profile and fictional data.
- Run headless Chrome on the host with `--proxy-server=http://127.0.0.1:9` and `--host-resolver-rules="MAP * ~NOTFOUND , EXCLUDE 127.0.0.1"`.
- Run a 1-second monitor of this run's java/chrome connections that aborts on any connection to an address other than 127.0.0.1.

Capture `/dashboard` at window widths 1440, 900 and 390 px for:
1. A newly registered patient: the welcome panel, zero stats, no charts.
2. A fictional patient with several records across types:
   - one record with two PDFs and one with none;
   - one record added more than 8 weeks ago;
   - one record whose title is 150 characters with no spaces.

For each capture, check and note:
- no horizontal scroll;
- the long title wraps inside its card;
- the ring segments, legend and percentage match the counts;
- 8 bars with week labels, and the current week is last;
- the storage line reads "… of 100 MB upload limit used";
- the caption names the server time zone;
- focus outlines are visible when tabbing.

Save captures under `artifacts/ui-evidence-2026-10-14/dashboard-redesign/` (untracked) and stop the app afterwards.

- [ ] **Step 3: Report**

Report to Rohit:
- test totals;
- each capture and what it showed, keeping verified results separate from anything unverified;
- the diff summary.

Then stop and wait for approval. Commit and push only if Rohit authorises it, following the pre-push checks in the workflow rules.
