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
