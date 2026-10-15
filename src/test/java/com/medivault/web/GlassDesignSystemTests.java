package com.medivault.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * The glass ("glassmorphism") visual system in {@code main.css} is built from tokens, not from values
 * scattered across rules. These checks read the packaged stylesheet and pin the parts that keep it
 * readable and safe: one blur token, a dashboard theme that only reassigns tokens, glass on every major
 * dashboard panel, and solid surfaces wherever glass is unsupported, unwanted or overridden by forced
 * colours.
 */
class GlassDesignSystemTests {

	/** The dashboard theme: applies where the page holds the dashboard and the browser supports :has(). */
	private static final String DASHBOARD_THEME = ":root:has(main.dashboard)";

	/** Tokens only the fallbacks may switch; the dashboard theme reassigns the tints behind them instead. */
	private static final List<String> GLASS_IN_USE = List.of("--glass-bg", "--glass-bg-quiet", "--glass-bg-raised",
			"--glass-blur");

	private static final Pattern BACKDROP_FILTER = Pattern.compile("(?:-webkit-)?backdrop-filter\\s*:\\s*([^;]+)");

	/** One CSS rule: the at-rule it sits in (empty at top level), its selector list and its declarations. */
	record Rule(String context, String selector, String body) {

		List<String> selectors() {
			return Arrays.stream(selector.split(",")).map(s -> s.strip().replaceAll("\\s+", " ")).toList();
		}

		Map<String, String> declarations() {
			Map<String, String> declarations = new LinkedHashMap<>();
			for (String declaration : body.split(";")) {
				int colon = declaration.indexOf(':');
				if (colon > 0) {
					declarations.put(declaration.substring(0, colon).strip(),
							declaration.substring(colon + 1).strip().replaceAll("\\s+", " "));
				}
			}
			return declarations;
		}

	}

	private static String stylesheet() throws IOException {
		String css = new ClassPathResource("static/css/main.css").getContentAsString(StandardCharsets.UTF_8);
		return css.replaceAll("(?s)/\\*.*?\\*/", "");
	}

	/** Every style rule in the stylesheet, including those inside @media and @supports blocks. */
	static List<Rule> rules(String css, String context) {
		List<Rule> rules = new ArrayList<>();
		int position = 0;
		while (true) {
			int open = css.indexOf('{', position);
			if (open < 0) {
				return rules;
			}
			String prelude = css.substring(position, open).strip().replaceAll("\\s+", " ");
			int depth = 1;
			int end = open + 1;
			while (depth > 0) {
				char c = css.charAt(end++);
				depth += (c == '{') ? 1 : (c == '}') ? -1 : 0;
			}
			String body = css.substring(open + 1, end - 1);
			if (prelude.startsWith("@")) {
				rules.addAll(rules(body, prelude));
			}
			else {
				rules.add(new Rule(context, prelude, body));
			}
			position = end;
		}
	}

	private static List<Rule> rulesFor(List<Rule> rules, String selector) {
		return rules.stream().filter(rule -> rule.selectors().contains(selector)).toList();
	}

	/** The declarations a selector gets from top-level rules, later rules winning. */
	private static Map<String, String> topLevelDeclarations(List<Rule> rules, String selector) {
		Map<String, String> declarations = new LinkedHashMap<>();
		rulesFor(rules, selector).stream()
			.filter(rule -> rule.context().isEmpty())
			.forEach(rule -> declarations.putAll(rule.declarations()));
		return declarations;
	}

	private static Map<String, String> rootDeclarationsIn(List<Rule> rules, String contextStart) {
		Map<String, String> declarations = new LinkedHashMap<>();
		rules.stream()
			.filter(rule -> rule.context().startsWith(contextStart) && rule.selectors().contains(":root"))
			.forEach(rule -> declarations.putAll(rule.declarations()));
		return declarations;
	}

	@Test
	void everyBackdropFilterUsesTheBlurTokenAndBlurIsDefinedOnlyAsAToken() throws IOException {
		List<String> problems = new ArrayList<>();
		for (Rule rule : rules(stylesheet(), "")) {
			Matcher backdrop = BACKDROP_FILTER.matcher(rule.body());
			while (backdrop.find()) {
				String value = backdrop.group(1).strip();
				if (!value.equals("var(--glass-blur)") && !value.equals("none")) {
					problems.add(rule.selector() + ": backdrop-filter " + value);
				}
			}
			rule.declarations().forEach((property, value) -> {
				if (value.contains("blur(") && !property.equals("--glass-blur")) {
					problems.add(rule.selector() + ": " + property + " " + value);
				}
			});
		}

		assertThat(problems).isEmpty();
	}

	@Test
	void theDashboardThemeOnlyReassignsTokensAndLeavesTheFallbackSwitchesAlone() throws IOException {
		List<Rule> theme = rules(stylesheet(), "").stream()
			.filter(rule -> rule.selector().contains(":has(main.dashboard)"))
			.toList();
		assertThat(theme).as("dashboard theme rules").isNotEmpty();
		assertThat(theme).allSatisfy(rule -> assertThat(rule.selectors()).containsExactly(DASHBOARD_THEME));

		Map<String, String> declarations = new LinkedHashMap<>();
		theme.forEach(rule -> declarations.putAll(rule.declarations()));
		assertThat(declarations.keySet()).as("properties the dashboard theme sets")
			.allSatisfy(property -> assertThat(property.startsWith("--") || property.equals("color-scheme"))
				.as(property)
				.isTrue())
			.doesNotContainAnyElementsOf(GLASS_IN_USE)
			.contains("--page-bg", "--glass-tint", "--glass-tint-quiet", "--glass-tint-raised", "--glass-solid",
					"--color-text", "--color-muted", "--color-focus", "--color-data-1");
		assertThat(declarations).containsEntry("color-scheme", "dark");
	}

	@Test
	void everyMajorDashboardSurfaceIsGlass() throws IOException {
		List<Rule> rules = rules(stylesheet(), "");
		Map<String, String> surfaces = Map.of(".site-header", "var(--glass-bg-quiet)", ".site-footer",
				"var(--glass-bg-quiet)", ".dashboard > .page-header", "var(--glass-bg-quiet)", ".dash-grid .card",
				"var(--glass-bg)");

		surfaces.forEach((selector, background) -> {
			Map<String, String> declarations = topLevelDeclarations(rules, selector);
			assertThat(declarations).as(selector)
				.containsEntry("background", background)
				.containsEntry("backdrop-filter", "var(--glass-blur)")
				.containsEntry("-webkit-backdrop-filter", "var(--glass-blur)");
		});

		Map<String, String> summary = topLevelDeclarations(rules, ".dash-grid > .summary-card");
		assertThat(summary.get("background")).as("patient summary").endsWith("var(--glass-bg-raised)");
		assertThat(summary.get("box-shadow")).as("patient summary edge light").contains("var(--glass-edge-lit)");
	}

	@Test
	void glassFallsBackToSolidSurfacesWithoutBlurSupportOrWhenLessTransparencyIsRequested() throws IOException {
		List<Rule> rules = rules(stylesheet(), "");
		Map<String, String> solid = Map.of("--glass-bg", "var(--glass-solid)", "--glass-bg-quiet",
				"var(--glass-solid-quiet)", "--glass-bg-raised", "var(--glass-solid-raised)");

		Map<String, String> unsupported = rootDeclarationsIn(rules, "@supports not");
		assertThat(unsupported).as("@supports not (backdrop-filter)").containsAllEntriesOf(solid);

		Map<String, String> reduced = rootDeclarationsIn(rules, "@media (prefers-reduced-transparency: reduce)");
		assertThat(reduced).as("reduced transparency or more contrast")
			.containsAllEntriesOf(solid)
			.containsEntry("--glass-blur", "none");
		assertThat(rules).as("the atmosphere is switched off with reduced transparency")
			.anyMatch(rule -> rule.context().startsWith("@media (prefers-reduced-transparency: reduce)")
					&& rule.selectors().contains("body::before")
					&& "var(--page-bg)".equals(rule.declarations().get("background")));
	}

	@Test
	void forcedColoursSwitchOffGlassAtmosphereAndEdgeLight() throws IOException {
		List<Rule> forced = rules(stylesheet(), "").stream()
			.filter(rule -> rule.context().equals("@media (forced-colors: active)"))
			.toList();

		assertThat(rootDeclarationsIn(forced, "@media")).containsEntry("--glass-blur", "none");
		assertThat(forced).anyMatch(rule -> rule.selectors().contains("body::before")
				&& "none".equals(rule.declarations().get("display")));
		assertThat(forced).anyMatch(rule -> rule.selectors().contains(".dash-grid > .summary-card::before")
				&& "none".equals(rule.declarations().get("display")));
		assertThat(forced).anyMatch(rule -> rule.selectors().contains(".dash-grid > .summary-card")
				&& "Canvas".equals(rule.declarations().get("background")));
	}

	/** Guards the parser the checks above rely on. */
	@Test
	void theParserFindsRulesInsideAtRulesWithTheirDeclarations() {
		String css = ":root { --a: 1px; }\n@media (forced-colors: active) {\n  .x,\n  .y  .z { display: none; }\n}\n"
				+ "@supports not (a: b) { :root { --a: 2px; } }";

		List<Rule> rules = rules(css, "");

		assertThat(rules).hasSize(3);
		assertThat(rules.get(1).context()).isEqualTo("@media (forced-colors: active)");
		assertThat(rules.get(1).selectors()).containsExactly(".x", ".y .z");
		assertThat(rules.get(1).declarations()).containsEntry("display", "none");
		assertThat(rootDeclarationsIn(rules, "@supports not")).containsExactly(Map.entry("--a", "2px"));
	}

}
