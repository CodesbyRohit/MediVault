package com.medivault.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.medivault.IntegrationTestSupport;
import com.medivault.demo.DemoRecordSeeder;
import com.medivault.user.User;

class RecordOwnershipTests extends IntegrationTestSupport {

	@Autowired
	private ApplicationContext context;

	@Test
	void patientSeesOnlyTheirOwnRecordsNewestFirst() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		MedicalRecord older = createRecord(asha, "Asha older report", LocalDate.now().minusDays(30));
		MedicalRecord newer = createRecord(asha, "Asha newer report", LocalDate.now().minusDays(2));
		createRecord(ravi, "Ravi private report");

		var result = mockMvc.perform(get("/records").with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(
					containsString("Asha older report"),
					containsString("Asha newer report"),
					not(containsString("Ravi private report")))))
			.andReturn();

		@SuppressWarnings("unchecked")
		List<MedicalRecord> records = (List<MedicalRecord>) result.getModelAndView().getModel().get("records");
		assertThat(records).extracting(MedicalRecord::getId).containsExactly(newer.getId(), older.getId());
	}

	@Test
	void patientCanOpenTheirOwnRecord() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");

		mockMvc.perform(get("/records/" + record.getId()).with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(
					containsString("Asha lipid profile"),
					containsString("Fictional Clinic"),
					containsString("Fictional notes for Asha lipid profile"))));
	}

	@Test
	void anotherPatientsRecordReturns404AndRevealsNothing() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		MedicalRecord ravisRecord = createRecord(ravi, "Ravi private report");

		mockMvc.perform(get("/records/" + ravisRecord.getId()).with(user(principal(asha))))
			.andExpect(status().isNotFound())
			.andExpect(content().string(not(containsString("Ravi private report"))));
	}

	@Test
	void missingRecordAlsoReturns404() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(get("/records/987654").with(user(principal(asha)))).andExpect(status().isNotFound());
	}

	@Test
	void dashboardCountsAndListsOnlyOwnRecordsWithTheThreeMostRecentlyAdded() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		User ravi = createPatient("Ravi Fictional", "ravi@example.test");
		// Added in order 1..4; record 4 has the oldest record date but was added last.
		for (int i = 1; i <= 4; i++) {
			createRecord(asha, "Asha report " + i, LocalDate.now().minusDays(i));
		}
		createRecord(ravi, "Ravi private report");

		mockMvc.perform(get("/dashboard").with(user(principal(asha))))
			.andExpect(status().isOk())
			.andExpect(model().attribute("recordCount", 4L))
			.andExpect(content().string(allOf(
					containsString("Asha report 4"),
					containsString("Asha report 2"),
					not(containsString("Asha report 1")),
					not(containsString("Ravi private report")))));
	}

	@Test
	void emptyStatesAreShownWhenThereAreNoRecords() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(get("/records").with(user(principal(asha))))
			.andExpect(content().string(containsString("You don't have any medical records yet.")));
		mockMvc.perform(get("/dashboard").with(user(principal(asha))))
			.andExpect(model().attribute("recordCount", 0L))
			.andExpect(content().string(containsString("You don't have any medical records yet.")));
	}

	@Test
	void withoutTheDemoProfileRegistrationCreatesNoRecords() throws Exception {
		assertThat(context.getBeanProvider(DemoRecordSeeder.class).getIfAvailable()).isNull();

		mockMvc.perform(post("/register").with(csrf())
			.param("fullName", "Asha Fictional")
			.param("email", "asha@example.test")
			.param("password", PASSWORD)
			.param("confirmPassword", PASSWORD));

		assertThat(userRepository.count()).isEqualTo(1);
		assertThat(recordRepository.count()).isZero();
	}

}
