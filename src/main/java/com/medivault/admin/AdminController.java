package com.medivault.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.medivault.user.UserRepository;

/**
 * Read-only account overview for administrators. It deliberately has no access to medical
 * records. Access is limited to ADMIN accounts in {@code SecurityConfig}.
 */
@Controller
public class AdminController {

	private final UserRepository userRepository;

	public AdminController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@GetMapping("/admin")
	public String users(Model model) {
		model.addAttribute("users",
				userRepository.findAllByOrderByCreatedAtAscIdAsc().stream().map(UserSummary::from).toList());
		return "admin/users";
	}

}
