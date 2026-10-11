package com.medivault.document;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.medivault.document.DocumentRejectedException.Reason;

/**
 * Uploads over {@code spring.servlet.multipart.max-file-size} fail while the request is parsed,
 * before any controller is chosen, so this advice is deliberately global. On Tomcat the failure
 * already surfaces in the security filter chain (while the CSRF token is read), so the upload never
 * passes CSRF or reaches a controller, and this handler runs during the following ERROR dispatch.
 * It only sends the user back to the record page with the same message as the application's own
 * size check; it changes nothing else. Only the numeric record id is taken from the URL, and the
 * record page itself still enforces login and ownership.
 */
@ControllerAdvice
public class UploadSizeLimitAdvice {

	private static final Pattern UPLOAD_PATH = Pattern.compile("/records/([0-9]{1,18})/documents");

	private final RecordDocumentService documentService;

	public UploadSizeLimitAdvice(RecordDocumentService documentService) {
		this.documentService = documentService;
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public String uploadTooLarge(HttpServletRequest request, RedirectAttributes redirect) {
		redirect.addFlashAttribute("uploadError", documentService.rejectionMessage(Reason.TOO_LARGE));
		Matcher upload = UPLOAD_PATH.matcher(originalPath(request));
		return upload.matches() ? "redirect:/records/" + upload.group(1) : "redirect:/records";
	}

	private static String originalPath(HttpServletRequest request) {
		Object errorUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
		String uri = request.getDispatcherType() == DispatcherType.ERROR && errorUri instanceof String original
				? original : request.getRequestURI();
		String contextPath = request.getContextPath();
		return uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
	}

}
