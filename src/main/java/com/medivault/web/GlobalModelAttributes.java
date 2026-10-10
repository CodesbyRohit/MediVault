package com.medivault.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Model attributes shared by every page rendered through a controller.
 */
@ControllerAdvice
public class GlobalModelAttributes {

	/** Used by the layout to mark the current navigation link with {@code aria-current="page"}. */
	@ModelAttribute("currentPath")
	public String currentPath(HttpServletRequest request) {
		return request.getRequestURI();
	}

}
