package com.medivault.web;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.medivault.IntegrationTestSupport;

class LandingAndLayoutTests extends IntegrationTestSupport {

	@Test
	void landingPageIsPublicAndExplainsMediVault() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(
					containsString("<title>MediVault"),
					containsString("Your medical records, organised and private."),
					containsString("fictional data only"),
					containsString("href=\"/register\""),
					containsString("href=\"/login\""))));
	}

	@Test
	void sharedLayoutProvidesLandmarksSkipLinkStylesheetAndFooter() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(
					containsString("lang=\"en\""),
					containsString("class=\"skip-link\" href=\"#main-content\""),
					containsString("<main id=\"main-content\""),
					containsString("<nav aria-label=\"Main\">"),
					containsString("href=\"/css/main.css\""),
					containsString("Built by Rohit Srivastava"))));
	}

	@Test
	void anonymousNavigationShowsLoginAndRegisterButNotLogout() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(content().string(allOf(
					containsString(">Log in</a>"),
					containsString(">Create account</a>"),
					not(containsString(">Log out</button>")))));
	}

	@Test
	void loggedInNavigationShowsLogoutFormWithCsrfToken() throws Exception {
		var patient = createPatient("Asha Fictional", "asha@example.test");
		mockMvc.perform(get("/").with(user(principal(patient))))
			.andExpect(content().string(allOf(
					containsString(">Log out</button>"),
					containsString("name=\"_csrf\""),
					containsString(">Dashboard</a>"),
					not(containsString(">Create account</a>")))));
	}

	@Test
	void loginAndRegisterPagesRenderWithCsrfProtectedForms() throws Exception {
		mockMvc.perform(get("/login"))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(
					containsString("<title>Log in &middot; MediVault</title>"),
					containsString("name=\"_csrf\""),
					containsString("autocomplete=\"current-password\""),
					containsString("aria-current=\"page\""))));
		mockMvc.perform(get("/register"))
			.andExpect(status().isOk())
			.andExpect(content().string(allOf(
					containsString("<title>Create account &middot; MediVault</title>"),
					containsString("name=\"_csrf\""),
					containsString("<label for=\"fullName\">"),
					containsString("autocomplete=\"new-password\""))));
	}

	@Test
	void responsesCarrySecurityHeaders() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("X-Frame-Options", "DENY"));
	}

	@Test
	void staticAssetsArePublic() throws Exception {
		mockMvc.perform(get("/css/main.css")).andExpect(status().isOk());
		mockMvc.perform(get("/images/favicon.svg")).andExpect(status().isOk());
	}

	@Test
	void loggedInUsersAreSentAwayFromLoginAndRegister() throws Exception {
		var patient = createPatient("Asha Fictional", "asha@example.test");
		mockMvc.perform(get("/login").with(user(principal(patient)))).andExpect(redirectedUrl("/dashboard"));
		mockMvc.perform(get("/register").with(user(principal(patient)))).andExpect(redirectedUrl("/dashboard"));
	}

}
