package com.medivault.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.medivault.IntegrationTestSupport;
import com.medivault.user.User;

class AuthenticationTests extends IntegrationTestSupport {

	@Test
	void patientLoginSucceedsAndLandsOnDashboard() throws Exception {
		createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(formLogin("/login").userParameter("email").user("asha@example.test").password(PASSWORD))
			.andExpect(redirectedUrl("/dashboard"))
			.andExpect(authenticated().withUsername("asha@example.test").withRoles("PATIENT"));
	}

	@Test
	void loginEmailIsCaseInsensitive() throws Exception {
		createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(formLogin("/login").userParameter("email").user("  ASHA@example.test ").password(PASSWORD))
			.andExpect(authenticated().withUsername("asha@example.test"));
	}

	@Test
	void adminLoginLandsOnTheUserList() throws Exception {
		createAdmin("Admin Fictional", "admin@example.test");

		mockMvc.perform(formLogin("/login").userParameter("email").user("admin@example.test").password(PASSWORD))
			.andExpect(redirectedUrl("/admin"))
			.andExpect(authenticated().withRoles("ADMIN"));
	}

	@Test
	void wrongPasswordShowsTheGenericError() throws Exception {
		createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(formLogin("/login").userParameter("email").user("asha@example.test").password("Wrong12345"))
			.andExpect(redirectedUrl("/login?error"))
			.andExpect(unauthenticated());
		mockMvc.perform(get("/login").param("error", ""))
			.andExpect(content().string(containsString("Invalid email or password.")));
	}

	@Test
	void unknownEmailFailsExactlyLikeAWrongPassword() throws Exception {
		mockMvc.perform(formLogin("/login").userParameter("email").user("nobody@example.test").password(PASSWORD))
			.andExpect(redirectedUrl("/login?error"))
			.andExpect(unauthenticated());
	}

	@Test
	void disabledAccountCannotLogIn() throws Exception {
		User patient = createPatient("Asha Fictional", "asha@example.test");
		patient.setEnabled(false);
		userRepository.save(patient);

		mockMvc.perform(formLogin("/login").userParameter("email").user("asha@example.test").password(PASSWORD))
			.andExpect(redirectedUrl("/login?error"))
			.andExpect(unauthenticated());
	}

	@Test
	void loginWithoutCsrfTokenIsRejected() throws Exception {
		createPatient("Asha Fictional", "asha@example.test");

		mockMvc.perform(post("/login").param("email", "asha@example.test").param("password", PASSWORD))
			.andExpect(status().isForbidden())
			.andExpect(unauthenticated());
	}

	@Test
	void sessionIdChangesOnSuccessfulLogin() throws Exception {
		createPatient("Asha Fictional", "asha@example.test");
		MockHttpSession session = new MockHttpSession();
		String idBeforeLogin = session.getId();

		mockMvc.perform(post("/login").with(csrf())
			.session(session)
			.param("email", "asha@example.test")
			.param("password", PASSWORD))
			.andExpect(redirectedUrl("/dashboard"))
			.andExpect(authenticated());

		assertThat(session.isInvalid()).isFalse();
		assertThat(session.getId()).as("session fixation protection").isNotEqualTo(idBeforeLogin);
	}

	@Test
	void logoutInvalidatesTheSession() throws Exception {
		createPatient("Asha Fictional", "asha@example.test");
		MvcResult login = mockMvc
			.perform(formLogin("/login").userParameter("email").user("asha@example.test").password(PASSWORD))
			.andExpect(authenticated())
			.andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		assertThat(session).isNotNull();

		mockMvc.perform(post("/logout").with(csrf()).session(session))
			.andExpect(redirectedUrl("/login?logout"))
			.andExpect(unauthenticated());

		assertThat(session.isInvalid()).isTrue();
		mockMvc.perform(get("/login").param("logout", ""))
			.andExpect(content().string(containsString("You have been logged out.")));
	}

	@Test
	void logoutRequiresPost() throws Exception {
		// GET /logout is not a logout endpoint (CSRF-safe); default-deny sends anonymous users to login.
		mockMvc.perform(get("/logout")).andExpect(redirectedUrlPattern("**/login"));
	}

	@Test
	void successfulRegistrationMessageIsShownOnLoginPage() throws Exception {
		mockMvc.perform(get("/login").param("registered", ""))
			.andExpect(content().string(containsString("Registration successful. Please log in.")));
	}

}
