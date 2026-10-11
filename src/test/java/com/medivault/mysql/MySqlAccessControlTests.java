package com.medivault.mysql;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.medivault.record.MedicalRecord;
import com.medivault.security.MediVaultUserDetails;
import com.medivault.user.Role;
import com.medivault.user.User;

/** Real MySQL 8.4: the Milestone 1 ownership and admin restrictions still hold. */
@MySqlIntegrationTest
@AutoConfigureMockMvc
class MySqlAccessControlTests extends MySqlTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void patientSeesOnlyOwnRecordsAndOthersAreNotFound() throws Exception {
		User asha = createUser("Asha Fictional", "asha@example.test", Role.PATIENT);
		User ravi = createUser("Ravi Fictional", "ravi@example.test", Role.PATIENT);
		createRecord(asha, "Asha fictional report");
		MedicalRecord ravisRecord = createRecord(ravi, "Ravi fictional report");

		mockMvc.perform(get("/records").with(user(MediVaultUserDetails.from(asha))))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Asha fictional report")))
			.andExpect(content().string(not(containsString("Ravi fictional report"))));
		mockMvc.perform(get("/records/" + ravisRecord.getId()).with(user(MediVaultUserDetails.from(asha))))
			.andExpect(status().isNotFound());
	}

	@Test
	void adminCannotOpenMedicalContent() throws Exception {
		User admin = createUser("Admin Fictional", "admin@example.test", Role.ADMIN);
		User patient = createUser("Asha Fictional", "asha@example.test", Role.PATIENT);
		MedicalRecord record = createRecord(patient, "Asha fictional report");

		mockMvc.perform(get("/records/" + record.getId()).with(user(MediVaultUserDetails.from(admin))))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/admin").with(user(MediVaultUserDetails.from(admin))))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("Asha fictional report"))));
	}

}
