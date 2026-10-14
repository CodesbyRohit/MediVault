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
import java.time.Instant;
import java.time.LocalDate;
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

}
