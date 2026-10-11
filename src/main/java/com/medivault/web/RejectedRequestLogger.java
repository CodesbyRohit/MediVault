package com.medivault.web;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Logs one line per handled request error (404, 400, ...) without any request-controlled text.
 * <p>
 * Spring's own "Resolved [...]" warnings print the exception message, which can repeat a malformed
 * path value, the requested path, a header value or an HTTP method token.
 * {@code DefaultHandlerExceptionResolver} logs them in every profile (and DevTools switches them on
 * for the other resolvers), so they are switched off in {@code application.properties}. This
 * resolver logs only code-defined facts instead: the exception type, the route template (for example
 * {@code /records/{id}}), a standard HTTP method, and for type mismatches the parameter name and
 * expected type. It never handles the exception (always returns {@code null}), so responses are
 * unchanged. Controlled by {@code medivault.web.log-rejected-requests} (on by default).
 */
@Component
public class RejectedRequestLogger implements HandlerExceptionResolver, Ordered {

	private static final Logger log = LoggerFactory.getLogger(RejectedRequestLogger.class);

	private static final Set<String> STANDARD_METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE",
			"OPTIONS", "TRACE");

	private final boolean enabled;

	public RejectedRequestLogger(@Value("${medivault.web.log-rejected-requests:false}") boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		if (enabled && log.isWarnEnabled()) {
			log.warn("Rejected request: {} {} -> {}{}", method(request), route(request), ex.getClass().getSimpleName(),
					detail(ex));
		}
		return null;
	}

	/** Right after Spring Boot's DefaultErrorAttributes, before every resolver that handles the error. */
	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 1;
	}

	private static String method(HttpServletRequest request) {
		return STANDARD_METHODS.contains(request.getMethod()) ? request.getMethod() : "(other method)";
	}

	/** The matched route template from the application's mappings, never the request URI. */
	private static String route(HttpServletRequest request) {
		Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		return pattern instanceof String template ? template : "(no matching route)";
	}

	private static String detail(Exception ex) {
		if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
			Class<?> required = mismatch.getRequiredType();
			return " (parameter '" + mismatch.getName() + "' must be "
					+ (required != null ? required.getSimpleName() : "of another type") + ")";
		}
		return "";
	}

}
