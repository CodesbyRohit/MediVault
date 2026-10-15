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
