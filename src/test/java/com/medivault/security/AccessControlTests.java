package com.medivault.security;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import com.medivault.IntegrationTestSupport;
import com.medivault.record.MedicalRecord;
import com.medivault.user.User;

class AccessControlTests extends IntegrationTestSupport {

	@ParameterizedTest
	@ValueSource(strings = { "/dashboard", "/records", "/records/1", "/admin", "/admin/anything", "/unlisted-page" })
	void anonymousUsersAreRedirectedToLogin(String path) throws Exception {
		mockMvc.perform(get(path)).andExpect(status().isFound()).andExpect(redirectedUrl("http://localhost/login"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/admin", "/admin/anything" })
	void patientsCannotOpenAdminRoutes(String path) throws Exception {
		User patient = createPatient("Asha Fictional", "asha@example.test");
		mockMvc.perform(get(path).with(user(principal(patient)))).andExpect(status().isForbidden());
	}

	@Test
	void adminsCannotOpenPatientDashboardOrRecords() throws Exception {
		User admin = createAdmin("Admin Fictional", "admin@example.test");
		User patient = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(patient, "Fictional thyroid panel");

		mockMvc.perform(get("/dashboard").with(user(principal(admin)))).andExpect(status().isForbidden());
		mockMvc.perform(get("/records").with(user(principal(admin)))).andExpect(status().isForbidden());
		mockMvc.perform(get("/records/" + record.getId()).with(user(principal(admin))))
			.andExpect(status().isForbidden());
	}

	@Test
	void adminSeesTheUserListButNoMedicalRecordContent() throws Exception {
		User admin = createAdmin("Admin Fictional", "admin@example.test");
		User patient = createPatient("Asha Fictional", "asha@example.test");
		createRecord(patient, "Fictional thyroid panel");

		mockMvc.perform(get("/admin").with(user(principal(admin))))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(
					containsString("asha@example.test"),
					containsString("Asha Fictional"),
					containsString("admin@example.test"),
					containsString("Patient"),
					containsString("Admin"),
					not(containsString("Fictional thyroid panel")),
					not(containsString("Fictional Clinic")),
					not(containsString("$2")))));
	}

	@Test
	void patientCanOpenOwnDashboard() throws Exception {
		User patient = createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(get("/dashboard").with(user(principal(patient))))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Welcome, Asha Fictional")));
	}

	@Test
	void unlistedRoutesAreDeniedForLoggedInUsers() throws Exception {
		User patient = createPatient("Asha Fictional", "asha@example.test");
		mockMvc.perform(get("/unlisted-page").with(user(principal(patient)))).andExpect(status().isForbidden());
		mockMvc.perform(get("/h2-console").with(user(principal(patient)))).andExpect(status().isForbidden());
	}

}
