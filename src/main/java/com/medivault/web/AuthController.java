package com.medivault.web;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.medivault.security.RoleHome;
import com.medivault.user.EmailAlreadyRegisteredException;
import com.medivault.user.RegistrationForm;
import com.medivault.user.RegistrationService;

/**
 * Login page and self-registration. The login POST itself is handled by Spring Security.
 */
@Controller
public class AuthController {

	static final String DUPLICATE_EMAIL_MESSAGE = "An account with this email already exists.";

	private final RegistrationService registrationService;

	public AuthController(RegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	/** Only these fields can ever be bound from the registration request. */
	@InitBinder("registrationForm")
	void allowRegistrationFields(WebDataBinder binder) {
		binder.setAllowedFields("fullName", "email", "password", "confirmPassword");
	}

	@GetMapping("/login")
	public String login(Authentication authentication) {
		if (RoleHome.isLoggedIn(authentication)) {
			return "redirect:" + RoleHome.pathFor(authentication);
		}
		return "auth/login";
	}

	@GetMapping("/register")
	public String registrationForm(Authentication authentication, Model model) {
		if (RoleHome.isLoggedIn(authentication)) {
			return "redirect:" + RoleHome.pathFor(authentication);
		}
		model.addAttribute("registrationForm", new RegistrationForm());
		return "auth/register";
	}

	@PostMapping("/register")
	public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form, BindingResult result,
			Authentication authentication) {
		if (RoleHome.isLoggedIn(authentication)) {
			return "redirect:" + RoleHome.pathFor(authentication);
		}
		if (!result.hasFieldErrors("password") && !result.hasFieldErrors("confirmPassword") && !form.passwordsMatch()) {
			result.rejectValue("confirmPassword", "mismatch", "Passwords do not match.");
		}
		if (!result.hasErrors()) {
			try {
				registrationService.registerPatient(form);
				return "redirect:/login?registered";
			}
			catch (EmailAlreadyRegisteredException ex) {
				result.rejectValue("email", "duplicate", DUPLICATE_EMAIL_MESSAGE);
			}
		}
		form.clearPasswords();
		return "auth/register";
	}

}
