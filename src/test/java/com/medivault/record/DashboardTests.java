package com.medivault.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.ModelAndView;

import com.medivault.IntegrationTestSupport;
import com.medivault.demo.DemoRecordSeeder;
import com.medivault.document.FictionalPdf;
import com.medivault.document.RecordDocumentService.RecentDocument;
import com.medivault.document.RecordDocumentService.StorageUsage;
import com.medivault.record.DashboardInsights.Completeness;
import com.medivault.record.DashboardInsights.TypeShare;
import com.medivault.record.DashboardInsights.WeekBar;
import com.medivault.user.User;

/**
 * The patient dashboard shows only what the logged-in patient actually saved: a newly registered
 * account starts empty, every figure comes from persisted rows, "Recently added" lists the newest
 * records first (by creation time, then id), and another patient's data never appears. Fictional
 * data only.
 */
class DashboardTests extends IntegrationTestSupport {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private JdbcTemplate jdbc;

	private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

	private static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 14);

	@Autowired
	private DashboardService dashboardService;

	private Map<String, Object> dashboardModel(User patient) throws Exception {
		ModelAndView view = mockMvc.perform(get("/dashboard").with(user(principal(patient))))
			.andExpect(status().isOk())
			.andReturn()
			.getModelAndView();
		assertThat(view).isNotNull();
		return view.getModel();
	}

	@SuppressWarnings("unchecked")
	private static List<String> recentTitles(Map<String, Object> model) {
		return ((List<MedicalRecord>) model.get("recentRecords")).stream().map(MedicalRecord::getTitle).toList();
	}

	private void addRecordThroughTheForm(User patient, String title, RecordType type, LocalDate date) throws Exception {
		mockMvc.perform(post("/records").with(csrf())
			.with(user(principal(patient)))
			.param("title", title)
			.param("recordType", type.name())
			.param("recordDate", date.toString())
			.param("providerName", "Fictional Clinic")
			.param("notes", ""))
			.andExpect(redirectedUrlPattern("/records/*"));
	}

	private void upload(User patient, MedicalRecord record, String name, byte[] pdf) throws Exception {
		mockMvc.perform(multipart("/records/" + record.getId() + "/documents")
			.file(new MockMultipartFile("file", name, "application/pdf", pdf))
			.with(csrf())
			.with(user(principal(patient)))).andExpect(flash().attribute("uploadSuccess", true));
	}

	/** Sets when a record was "added", as an exact instant; whole seconds only, to suit TIMESTAMP(6). */
	private void setCreatedAt(MedicalRecord record, Instant createdAt) {
		jdbc.update("update medical_records set created_at = ? where id = ?",
				OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC), record.getId());
	}

	@Test
	void aNewlyRegisteredPatientStartsWithAnEmptyDashboard() throws Exception {
		assertThat(context.getBeanProvider(DemoRecordSeeder.class).getIfAvailable()).as("no demo seeding").isNull();
		mockMvc.perform(post("/register").with(csrf())
			.param("fullName", "Asha Fictional")
			.param("email", "asha@example.test")
			.param("password", PASSWORD)
			.param("confirmPassword", PASSWORD))
			.andExpect(redirectedUrl("/login?registered"));
		User asha = userRepository.findByEmail("asha@example.test").orElseThrow();

		assertThat(recordRepository.countByOwnerId(asha.getId())).isZero();
		assertThat(documentRepository.countByRecordOwnerId(asha.getId())).isZero();
		Map<String, Object> model = dashboardModel(asha);
		assertThat(model.get("recordCount")).isEqualTo(0L);
		assertThat(recentTitles(model)).isEmpty();
		assertThat((Map<?, ?>) model.get("typeCounts")).isEmpty();
		assertThat((List<?>) model.get("recentDocuments")).isEmpty();
		StorageUsage storage = (StorageUsage) model.get("storage");
		assertThat(storage.documentCount()).isZero();
		assertThat(storage.usedBytes()).isZero();

		mockMvc.perform(get("/dashboard").with(user(principal(asha))))
			.andExpect(content().string(allOf(containsString("You don't have any medical records yet."),
					containsString("href=\"/records/new\">Add your first record</a>"),
					not(containsString("Sample:")), not(containsString("(fictional)")))));
	}

	@Test
	void countsTypesAndStorageComeFromWhatThePatientActuallySaved() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		addRecordThroughTheForm(asha, "Asha lipid profile", RecordType.LAB_REPORT, LocalDate.now().minusDays(10));
		addRecordThroughTheForm(asha, "Asha antibiotic course", RecordType.PRESCRIPTION, LocalDate.now().minusDays(2));
		MedicalRecord lipid = recordRepository.findAllByOwnerIdOrderByRecordDateDescIdDesc(asha.getId())
			.stream()
			.filter(r -> r.getTitle().equals("Asha lipid profile"))
			.findFirst()
			.orElseThrow();
		byte[] pdf = FictionalPdf.bytes("Asha lipid profile scan");
		upload(asha, lipid, "fictional-lipid.pdf", pdf);

		Map<String, Object> model = dashboardModel(asha);

		assertThat(model.get("recordCount")).isEqualTo(2L);
		assertThat(recentTitles(model)).containsExactly("Asha antibiotic course", "Asha lipid profile");
		@SuppressWarnings("unchecked")
		Map<RecordType, Long> types = (Map<RecordType, Long>) model.get("typeCounts");
		assertThat(types).containsExactlyInAnyOrderEntriesOf(Map.of(RecordType.LAB_REPORT, 1L, RecordType.PRESCRIPTION, 1L));
		StorageUsage storage = (StorageUsage) model.get("storage");
		assertThat(storage.documentCount()).isEqualTo(1);
		assertThat(storage.usedBytes()).isEqualTo(pdf.length);
		@SuppressWarnings("unchecked")
		List<RecentDocument> documents = (List<RecentDocument>) model.get("recentDocuments");
		assertThat(documents).extracting(RecentDocument::filename).containsExactly("fictional-lipid.pdf");
		assertThat(documents).extracting(RecentDocument::recordId).containsExactly(lipid.getId());
	}

	@Test
	void recentlyAddedListsTheNewestRecordsFirstWhateverTheirRecordDate() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		createRecord(asha, "Added first, dated yesterday", LocalDate.now().minusDays(1));
		createRecord(asha, "Added second, dated last year", LocalDate.now().minusYears(1));
		createRecord(asha, "Added third, dated last month", LocalDate.now().minusMonths(1));
		createRecord(asha, "Added fourth, dated 2010", LocalDate.of(2010, 5, 1));

		assertThat(recentTitles(dashboardModel(asha))).containsExactly("Added fourth, dated 2010",
				"Added third, dated last month", "Added second, dated last year");
	}

	@Test
	void recordsAddedAtTheSameInstantAreOrderedByNewestIdFirst() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord first = createRecord(asha, "Tie record A", LocalDate.now().minusDays(1));
		MedicalRecord second = createRecord(asha, "Tie record B", LocalDate.now().minusDays(1));
		MedicalRecord third = createRecord(asha, "Tie record C", LocalDate.now().minusDays(1));
		createRecord(asha, "Tie record D", LocalDate.now().minusDays(1));
		jdbc.update("update medical_records set created_at = ? where owner_id = ?",
				Timestamp.from(Instant.parse("2026-01-01T09:00:00Z")), asha.getId());
		assertThat(jdbc.queryForObject("select count(distinct created_at) from medical_records where owner_id = ?",
				Long.class, asha.getId())).as("all four share one timestamp").isEqualTo(1L);
		assertThat(first.getId()).isLessThan(second.getId()).isLessThan(third.getId());

		assertThat(recentTitles(dashboardModel(asha))).containsExactly("Tie record D", "Tie record C", "Tie record B");
	}

	@Test
	void anotherPatientsRecordsAndDocumentsNeverAppearOrCount() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		MedicalRecord raviRecord = createRecord(ravi, "Ravi private report");
		upload(ravi, raviRecord, "ravi-private.pdf", FictionalPdf.bytes("Ravi private scan"));

		Map<String, Object> ashaModel = dashboardModel(asha);
		assertThat(ashaModel.get("recordCount")).isEqualTo(0L);
		assertThat(recentTitles(ashaModel)).isEmpty();
		assertThat((Map<?, ?>) ashaModel.get("typeCounts")).isEmpty();
		assertThat((List<?>) ashaModel.get("recentDocuments")).isEmpty();
		assertThat(((StorageUsage) ashaModel.get("storage")).usedBytes()).isZero();
		mockMvc.perform(get("/dashboard").with(user(principal(asha))))
			.andExpect(content().string(allOf(not(containsString("Ravi private report")),
					not(containsString("ravi-private.pdf")))));

		Map<String, Object> raviModel = dashboardModel(ravi);
		assertThat(raviModel.get("recordCount")).isEqualTo(1L);
		assertThat(recentTitles(raviModel)).containsExactly("Ravi private report");
		assertThat(((StorageUsage) raviModel.get("storage")).documentCount()).isEqualTo(1);
	}

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
		// Older than anything of Asha's, so a leak into the earliest record date would show.
		createRecord(ravi, "Ravi oldest report", LocalDate.of(2020, 1, 1));
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
		// Ids rise a < b < c < older, but the added times do not follow the ids, so an order that
		// skipped createdAt (or used only the id) would pick a different record.
		MedicalRecord a = createRecord(asha, "Same day, added latest, lowest id", sameDay);
		MedicalRecord b = createRecord(asha, "Same day, added latest, higher id", sameDay);
		MedicalRecord c = createRecord(asha, "Same day, added earlier, highest id", sameDay);
		MedicalRecord older = createRecord(asha, "Older date, highest id overall", LocalDate.of(2025, 5, 1));
		setCreatedAt(a, Instant.parse("2026-10-03T08:00:00Z"));
		setCreatedAt(b, Instant.parse("2026-10-03T08:00:00Z"));
		setCreatedAt(c, Instant.parse("2026-10-02T08:00:00Z"));
		setCreatedAt(older, Instant.parse("2026-10-01T08:00:00Z"));

		DashboardInsights insights = dashboardService.insightsFor(asha.getId(), WEDNESDAY, KOLKATA);

		assertThat(a.getId()).isLessThan(b.getId());
		assertThat(b.getId()).isLessThan(c.getId());
		assertThat(c.getId()).isLessThan(older.getId());
		// Same record date: latest added wins (a and b), then the higher id (b), never c.
		assertThat(insights.latestRecord().getTitle()).isEqualTo("Same day, added latest, higher id");
		// Last added is by added time, not id: 3 Oct 08:00 UTC, not the highest id's 1 Oct.
		assertThat(insights.lastAddedAt()).isEqualTo(LocalDateTime.of(2026, 10, 3, 13, 30));
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

	/** The glass theme in main.css keys on main.dashboard; the stat cards and chart need these hooks. */
	@Test
	void thePageCarriesTheHooksTheGlassThemeStylesByClassOnly() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		createRecord(asha, "Asha lipid profile");

		String html = mockMvc.perform(get("/dashboard").with(user(principal(asha))))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(html).contains("<main id=\"main-content\" tabindex=\"-1\" class=\"container dashboard\"",
				"class=\"card stat-card stat-records\"", "class=\"card stat-card stat-documents\"",
				"class=\"card stat-card stat-latest\"", "class=\"card summary-card\"")
			.doesNotContain("style=");
		assertThat(html.split("class=\"histogram-grid\"", -1)).as("histogram grid lines + 1").hasSize(5);
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
		setCreatedAt(old, Instant.now().minus(Duration.ofDays(70)));

		Map<String, Object> model = dashboardModel(asha);

		assertThat(((DashboardInsights) model.get("insights")).weeks()).extracting(WeekBar::count).containsOnly(0L);
		mockMvc.perform(get("/dashboard").with(user(principal(asha))))
			.andExpect(content().string(allOf(containsString("Record completeness"),
					containsString("Records added per week"), containsString("this week, so far"))));
	}

}
