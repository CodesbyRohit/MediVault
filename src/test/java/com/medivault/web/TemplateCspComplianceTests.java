package com.medivault.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The Content-Security-Policy in {@code SecurityConfig} allows styles, scripts, images and form
 * targets from MediVault's own origin only (plus {@code data:} images), with no inline styles or
 * scripts. These checks read the packaged templates and stylesheet, so markup the policy would block,
 * a dependency on another host, or a reference to a missing local asset fails here instead of
 * silently breaking a page in the browser. Plain hyperlinks to other sites are allowed: the policy
 * does not restrict navigation.
 */
class TemplateCspComplianceTests {

	private static final String TEMPLATES = "templates/";

	private static final PathMatchingResourcePatternResolver RESOLVER = new PathMatchingResourcePatternResolver();

	/** What a template must never contain, keyed by the reason shown on failure. */
	private static final Map<String, Pattern> TEMPLATE_RULES = new LinkedHashMap<>();

	static {
		TEMPLATE_RULES.put("<style> element (inline styles are blocked)", Pattern.compile("(?i)<style[\\s>]"));
		TEMPLATE_RULES.put("<script> element (MediVault uses no scripts)", Pattern.compile("(?i)<script[\\s>]"));
		TEMPLATE_RULES.put("style attribute (inline styles are blocked)", Pattern.compile("(?i)\\s(?:th:)?style\\s*="));
		TEMPLATE_RULES.put("event-handler attribute (inline scripts are blocked)",
				Pattern.compile("(?i)\\s(?:th:)?on[a-z]+\\s*="));
		TEMPLATE_RULES.put("javascript: URL", Pattern.compile("(?i)javascript:"));
		TEMPLATE_RULES.put("<link> to another origin (stylesheets and icons are 'self' only)",
				Pattern.compile("(?i)<link\\b[^>]*\\s(?:th:)?href\\s*=\\s*[\"']\\s*(?:@\\{)?\\s*(?:https?:)?//"));
		TEMPLATE_RULES.put("embedded resource from another origin",
				Pattern.compile("(?i)\\s(?:th:)?(?:src|srcset|poster|data)\\s*=\\s*[\"']\\s*(?:@\\{)?\\s*(?:https?:)?//"));
		TEMPLATE_RULES.put("form target on another origin (form-action 'self')",
				Pattern.compile("(?i)\\s(?:th:)?(?:action|formaction)\\s*=\\s*[\"']\\s*(?:@\\{)?\\s*(?:https?:)?//"));
	}

	/** A Thymeleaf link to a packaged static asset, for example {@code @{/css/main.css}}. */
	private static final Pattern LOCAL_ASSET = Pattern.compile("@\\{(/(?:css|images)/[^}?#]+)\\}");

	private static final Pattern CSS_URL = Pattern.compile("(?i)url\\(\\s*[\"']?([^\"')]+)[\"']?\\s*\\)");

	/** The packaged templates by path below {@code templates/}, for example {@code auth/login.html}. */
	private static Map<String, String> templates() throws IOException {
		Map<String, String> templates = new LinkedHashMap<>();
		for (Resource resource : RESOLVER.getResources("classpath:" + TEMPLATES + "**/*.html")) {
			String url = resource.getURL().toString();
			templates.put(url.substring(url.lastIndexOf(TEMPLATES) + TEMPLATES.length()),
					resource.getContentAsString(StandardCharsets.UTF_8));
		}
		return templates;
	}

	private static List<String> violations(String name, String html) {
		List<String> violations = new ArrayList<>();
		TEMPLATE_RULES.forEach((rule, pattern) -> {
			if (pattern.matcher(html).find()) {
				violations.add(name + ": " + rule);
			}
		});
		return violations;
	}

	@Test
	void templatesContainNothingTheContentSecurityPolicyWouldBlock() throws IOException {
		Map<String, String> templates = templates();
		assertThat(templates).as("packaged templates").containsKeys("layout.html", "index.html", "auth/login.html",
				"records/detail.html");

		List<String> violations = new ArrayList<>();
		templates.forEach((name, html) -> violations.addAll(violations(name, html)));

		assertThat(violations).isEmpty();
	}

	@Test
	void everyLocalAssetATemplateLinksToIsPackaged() throws IOException {
		List<String> missing = new ArrayList<>();
		int references = 0;
		for (Map.Entry<String, String> template : templates().entrySet()) {
			Matcher asset = LOCAL_ASSET.matcher(template.getValue());
			while (asset.find()) {
				references++;
				if (!RESOLVER.getResource("classpath:static" + asset.group(1)).exists()) {
					missing.add(template.getKey() + ": " + asset.group(1));
				}
			}
		}

		assertThat(references).as("local asset links found (at least the stylesheet and the icon)")
			.isGreaterThanOrEqualTo(2);
		assertThat(missing).isEmpty();
	}

	@Test
	void theStylesheetLoadsNothingFromAnotherOrigin() throws IOException {
		Resource stylesheet = RESOLVER.getResource("classpath:static/css/main.css");
		String css = stylesheet.getContentAsString(StandardCharsets.UTF_8);
		assertThat(css).as("main.css").isNotBlank();
		assertThat(Pattern.compile("(?i)@import").matcher(css).find()).as("@import in main.css").isFalse();

		List<String> problems = new ArrayList<>();
		Matcher url = CSS_URL.matcher(css);
		while (url.find()) {
			String target = url.group(1).strip();
			if (target.startsWith("data:")) {
				continue; // data: images are allowed (img-src 'self' data:)
			}
			if (target.matches("(?i)(?:https?:)?//.*")) {
				problems.add("url() on another origin: " + target);
			}
			else if (!(target.startsWith("/") ? RESOLVER.getResource("classpath:static" + target)
					: stylesheet.createRelative(target)).exists()) {
				problems.add("url() to a missing file: " + target);
			}
		}

		assertThat(problems).isEmpty();
	}

	/** Guards the checks themselves: each forbidden sample is caught, and markup MediVault uses is not. */
	@Test
	void theRulesRecogniseWhatTheyForbidAndNothingElse() {
		List<String> forbidden = List.of("<style>p { color: red }</style>", "<script src=\"/app.js\"></script>",
				"<p style=\"color: red\">", "<p th:style=\"'color: red'\">", "<button onclick=\"go()\">",
				"<button th:onclick=\"go()\">", "<a href=\"javascript:void(0)\">",
				"<link rel=\"stylesheet\" href=\"https://cdn.example.test/x.css\">",
				"<link rel=\"icon\"\n      th:href=\"@{https://cdn.example.test/x.svg}\">",
				"<img src=\"//cdn.example.test/x.png\">", "<img th:src=\"@{https://cdn.example.test/x.png}\">",
				"<form action=\"https://collector.example.test/\" method=\"post\">");
		List<String> allowed = List.of("<html lang=\"en\" xmlns:th=\"http://www.thymeleaf.org\">",
				"<link rel=\"stylesheet\" th:href=\"@{/css/main.css}\">",
				"<form class=\"form\" th:action=\"@{/records}\" th:object=\"${recordForm}\" method=\"post\" novalidate>",
				"<td data-label=\"Title\">", "<a href=\"https://example.test/about\">About</a>",
				"<p>Use fictional data only.</p>");

		assertThat(forbidden).allSatisfy(sample -> assertThat(violations("sample", sample)).as(sample).isNotEmpty());
		assertThat(allowed).allSatisfy(sample -> assertThat(violations("sample", sample)).as(sample).isEmpty());
	}

}
