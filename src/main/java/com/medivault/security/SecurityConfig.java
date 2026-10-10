package com.medivault.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import com.medivault.user.Role;

@Configuration
public class SecurityConfig {

	private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; img-src 'self' data:; "
			+ "style-src 'self'; script-src 'self'; object-src 'none'; base-uri 'self'; "
			+ "form-action 'self'; frame-ancestors 'none'";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				// Let Spring Boot's error page render. Only ERROR dispatches are exempt; internal
				// FORWARD dispatches are authorised like any other request.
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers("/", "/login", "/register", "/css/**", "/images/**", "/favicon.ico", "/error")
				.permitAll()
				.requestMatchers("/admin", "/admin/**").hasRole(Role.ADMIN.name())
				.requestMatchers("/dashboard", "/records", "/records/**").hasRole(Role.PATIENT.name())
				// Default-deny: anything not listed above is refused.
				.anyRequest().denyAll())
			.formLogin(form -> form
				.loginPage("/login")
				.usernameParameter("email")
				.passwordParameter("password")
				.successHandler(new RoleHomeSuccessHandler())
				.failureUrl("/login?error")
				.permitAll())
			.logout(logout -> logout
				.logoutUrl("/logout") // POST only while CSRF protection is enabled
				.logoutSuccessUrl("/login?logout")
				.invalidateHttpSession(true)
				.clearAuthentication(true)
				.deleteCookies("JSESSIONID")
				.permitAll())
			.httpBasic(AbstractHttpConfigurer::disable)
			.headers(headers -> headers
				.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
				.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)));
		// CSRF protection and session-fixation protection stay at their secure defaults.
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * After login: return to the page the user originally asked for, otherwise go to the
	 * role's home page ({@code /admin} or {@code /dashboard}).
	 */
	static class RoleHomeSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

		@Override
		protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
				Authentication authentication) {
			return RoleHome.pathFor(authentication);
		}

	}

}
