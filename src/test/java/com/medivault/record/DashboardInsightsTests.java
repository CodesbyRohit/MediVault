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
