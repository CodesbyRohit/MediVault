package com.medivault.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.medivault.IntegrationTestSupport;
import com.medivault.user.User;

/**
 * Search, filters, sorting and paging on {@code GET /records}. Every case also checks that another
 * patient's matching records never appear. All data is fictional.
 */
class RecordSearchTests extends IntegrationTestSupport {

	private static final LocalDate TODAY = LocalDate.now();

	private MedicalRecord save(User owner, String title, RecordType type, LocalDate date, String provider,
			String notes) {
		return recordRepository.save(new MedicalRecord(owner, title, type, date, provider, notes));
	}

	@SuppressWarnings("unchecked")
	private List<String> titles(MockHttpServletRequestBuilder request) throws Exception {
		var result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
		return ((List<MedicalRecord>) result.getModelAndView().getModel().get("records")).stream()
			.map(MedicalRecord::getTitle)
			.toList();
	}

	@Test
	void textSearchMatchesTitleProviderAndNotesIgnoringCaseAndOnlyOwnRecords() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		save(asha, "Thyroid panel", RecordType.LAB_REPORT, TODAY.minusDays(1), "City Lab", null);
		save(asha, "Chest X-ray", RecordType.IMAGING, TODAY.minusDays(2), "THYROID Clinic", null);
		save(asha, "Follow-up", RecordType.OTHER, TODAY.minusDays(3), null, "Discussed thyroid results");
		save(asha, "Flu shot", RecordType.VACCINATION, TODAY.minusDays(4), null, null);
		save(ravi, "Ravi thyroid scan", RecordType.IMAGING, TODAY, null, null);

		assertThat(titles(get("/records").param("q", "thyroid").with(user(principal(asha)))))
			.containsExactly("Thyroid panel", "Chest X-ray", "Follow-up");
	}

	@Test
	void wildcardCharactersInTheSearchTextMatchOnlyThemselves() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		save(asha, "Dose 100% complete", RecordType.OTHER, TODAY.minusDays(1), null, null);
		save(asha, "Dose 100 complete", RecordType.OTHER, TODAY.minusDays(2), null, null);
		save(asha, "file_name report", RecordType.OTHER, TODAY.minusDays(3), null, null);
		save(asha, "filexname report", RecordType.OTHER, TODAY.minusDays(4), null, null);
		save(asha, "Bang! report", RecordType.OTHER, TODAY.minusDays(5), null, null);

		assertThat(titles(get("/records").param("q", "100%").with(user(principal(asha)))))
			.containsExactly("Dose 100% complete");
		assertThat(titles(get("/records").param("q", "file_").with(user(principal(asha)))))
			.containsExactly("file_name report");
		assertThat(titles(get("/records").param("q", "%").with(user(principal(asha)))))
			.containsExactly("Dose 100% complete");
		assertThat(titles(get("/records").param("q", "g!").with(user(principal(asha)))))
			.containsExactly("Bang! report");
	}

	@Test
	void typeProviderAndInclusiveDateRangeFiltersCombine() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		save(asha, "Old lab", RecordType.LAB_REPORT, LocalDate.of(2024, 1, 10), "City Lab", null);
		save(asha, "Range start lab", RecordType.LAB_REPORT, LocalDate.of(2025, 3, 1), "City Lab", null);
		save(asha, "Range end lab", RecordType.LAB_REPORT, LocalDate.of(2025, 3, 31), "City Lab", null);
		save(asha, "Other provider lab", RecordType.LAB_REPORT, LocalDate.of(2025, 3, 15), "Town Lab", null);
		save(asha, "Range imaging", RecordType.IMAGING, LocalDate.of(2025, 3, 15), "City Lab", null);
		save(ravi, "Ravi lab", RecordType.LAB_REPORT, LocalDate.of(2025, 3, 15), "City Lab", null);

		assertThat(titles(get("/records").param("type", "LAB_REPORT")
			.param("provider", "City Lab")
			.param("from", "2025-03-01")
			.param("to", "2025-03-31")
			.with(user(principal(asha))))).containsExactly("Range end lab", "Range start lab");
		assertThat(titles(get("/records").param("type", "IMAGING").with(user(principal(asha)))))
			.containsExactly("Range imaging");
	}

	@Test
	void theProviderFilterOffersOnlyTheOwnersProviders() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		save(asha, "Rec A", RecordType.OTHER, TODAY, "Zeta Clinic", null);
		save(asha, "Rec B", RecordType.OTHER, TODAY, "Alpha Lab", null);
		save(asha, "Rec C", RecordType.OTHER, TODAY, "Alpha Lab", null);
		save(ravi, "Rec R", RecordType.OTHER, TODAY, "Ravi Secret Hospital", null);

		mockMvc.perform(get("/records").with(user(principal(asha))))
			.andExpect(model().attribute("providers", List.of("Alpha Lab", "Zeta Clinic")))
			.andExpect(content().string(not(containsString("Ravi Secret Hospital"))));
	}

	@Test
	void sortOrdersAreApplied() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		save(asha, "beta", RecordType.OTHER, TODAY.minusDays(5), null, null);
		save(asha, "Alpha", RecordType.OTHER, TODAY.minusDays(1), null, null);
		save(asha, "gamma", RecordType.OTHER, TODAY.minusDays(9), null, null);

		assertThat(titles(get("/records").with(user(principal(asha))))).containsExactly("Alpha", "beta", "gamma");
		assertThat(titles(get("/records").param("sort", "DATE_ASC").with(user(principal(asha)))))
			.containsExactly("gamma", "beta", "Alpha");
		assertThat(titles(get("/records").param("sort", "TITLE").with(user(principal(asha)))))
			.containsExactly("Alpha", "beta", "gamma");
	}

	@Test
	void resultsArePagedAndPageLinksKeepTheFilters() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		for (int i = 1; i <= 25; i++) {
			save(asha, "Lab %02d".formatted(i), RecordType.LAB_REPORT, TODAY.minusDays(i), null, null);
		}
		save(asha, "Unrelated imaging", RecordType.IMAGING, TODAY, null, null);

		var first = mockMvc.perform(get("/records").param("type", "LAB_REPORT").with(user(principal(asha))))
			.andExpect(content().string(allOf(containsString("25 records, page 1 of 2"),
					containsString("href=\"/records?type=LAB_REPORT&amp;page=1\""))))
			.andReturn();
		assertThat(((Page<?>) first.getModelAndView().getModel().get("page")).getContent()).hasSize(20);

		assertThat(titles(get("/records").param("type", "LAB_REPORT").param("page", "1").with(user(principal(asha)))))
			.containsExactly("Lab 21", "Lab 22", "Lab 23", "Lab 24", "Lab 25");
		// A page past the end shows the last page instead of nothing.
		assertThat(titles(get("/records").param("type", "LAB_REPORT").param("page", "99").with(user(principal(asha)))))
			.hasSize(5);
	}

	@Test
	void invalidOptionsAreReportedAndIgnored() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		save(asha, "Asha record", RecordType.OTHER, TODAY, null, null);

		mockMvc.perform(get("/records").param("type", "NOT_A_TYPE")
			.param("from", "yesterday")
			.param("sort", "DROP_TABLE")
			.param("page", "-4")
			.param("q", "x".repeat(101))
			.with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(containsString("Some search options were ignored"),
					containsString("Choose a record type from the list."), containsString("Enter a valid start date."),
					containsString("Search text must be 100 characters or fewer."), containsString("Asha record"),
					not(containsString("Failed to convert")))));
	}

	@Test
	void anInvertedDateRangeIsReportedAndIgnored() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		save(asha, "Asha record", RecordType.OTHER, LocalDate.of(2025, 6, 1), null, null);

		mockMvc.perform(get("/records").param("from", "2025-12-31").param("to", "2025-01-01").with(user(principal(asha))))
			.andExpect(content().string(allOf(containsString("The end date must be on or after the start date."),
					containsString("Asha record"))));
	}

	@Test
	void aSearchWithoutMatchesSaysSoAndOffersToClearIt() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		save(asha, "Asha record", RecordType.OTHER, TODAY, null, null);
		save(ravi, "Ravi needle", RecordType.OTHER, TODAY, null, null);

		mockMvc.perform(get("/records").param("q", "needle").with(user(principal(asha))))
			.andExpect(content().string(allOf(containsString("No matching records"), containsString("Clear the filters"),
					not(containsString("Ravi needle")))));
	}

	@Test
	void likePatternsAreEscaped() {
		assertThat(RecordSpecifications.escapeLike("50%_off!")).isEqualTo("50!%!_off!!");
	}

}
