package com.medivault.record;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Search, filter and sort options for the records page ({@code GET /records}). Every option is
 * optional; an invalid option is reported and then ignored, so the page always renders. The owner
 * is never part of this form: it always comes from the authenticated principal.
 */
public class RecordSearchForm {

	public static final int MAX_QUERY_LENGTH = 100;

	@Size(max = MAX_QUERY_LENGTH, message = "Search text must be 100 characters or fewer.")
	private String q;

	private RecordType type;

	@Size(max = 150, message = "Provider must be 150 characters or fewer.")
	private String provider;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate from;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate to;

	private RecordSort sort = RecordSort.DATE_DESC;

	@Min(value = 0, message = "Page numbers start at 1.")
	@Max(value = 100_000, message = "That page does not exist.")
	private Integer page = 0;

	/** Resets an option that failed validation, so the search runs without it. */
	public void discard(String field) {
		switch (field) {
			case "q" -> q = null;
			case "type" -> type = null;
			case "provider" -> provider = null;
			case "from" -> from = null;
			case "to" -> to = null;
			case "sort" -> sort = RecordSort.DATE_DESC;
			case "page" -> page = 0;
			default -> {
				// not a search field
			}
		}
	}

	public boolean hasInvertedDateRange() {
		return from != null && to != null && from.isAfter(to);
	}

	/** Whether any option narrows the result (sorting and paging do not). */
	public boolean isFiltered() {
		return q != null || type != null || provider != null || from != null || to != null;
	}

	/** The records page URL for {@code pageIndex} (0-based) with the current options; empty options are left out. */
	public String pageUrl(int pageIndex) {
		UriComponentsBuilder url = UriComponentsBuilder.fromPath("/records");
		addIfPresent(url, "q", q);
		addIfPresent(url, "type", type == null ? null : type.name());
		addIfPresent(url, "provider", provider);
		addIfPresent(url, "from", from == null ? null : from.toString());
		addIfPresent(url, "to", to == null ? null : to.toString());
		if (sort != RecordSort.DATE_DESC) {
			url.queryParam("sort", sort.name());
		}
		if (pageIndex > 0) {
			url.queryParam("page", pageIndex);
		}
		return url.encode().build().toUriString();
	}

	private static void addIfPresent(UriComponentsBuilder url, String name, String value) {
		if (value != null) {
			url.queryParam(name, value);
		}
	}

	private static String stripToNull(String value) {
		if (value == null) {
			return null;
		}
		String stripped = value.strip();
		return stripped.isEmpty() ? null : stripped;
	}

	public String getQ() {
		return q;
	}

	public void setQ(String q) {
		this.q = stripToNull(q);
	}

	public RecordType getType() {
		return type;
	}

	public void setType(RecordType type) {
		this.type = type;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = stripToNull(provider);
	}

	public LocalDate getFrom() {
		return from;
	}

	public void setFrom(LocalDate from) {
		this.from = from;
	}

	public LocalDate getTo() {
		return to;
	}

	public void setTo(LocalDate to) {
		this.to = to;
	}

	public RecordSort getSort() {
		return sort;
	}

	public void setSort(RecordSort sort) {
		this.sort = sort == null ? RecordSort.DATE_DESC : sort;
	}

	public Integer getPage() {
		return page;
	}

	public void setPage(Integer page) {
		this.page = page == null ? 0 : page;
	}

}
