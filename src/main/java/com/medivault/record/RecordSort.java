package com.medivault.record;

import org.springframework.data.domain.Sort;

/** The orders a patient can choose on the records page. Every order ends with the id for stable paging. */
public enum RecordSort {

	DATE_DESC("Newest first", Sort.by(Sort.Order.desc("recordDate"), Sort.Order.desc("id"))),
	DATE_ASC("Oldest first", Sort.by(Sort.Order.asc("recordDate"), Sort.Order.asc("id"))),
	TITLE("Title (A–Z)", Sort.by(Sort.Order.asc("title").ignoreCase(), Sort.Order.asc("id"))),
	UPDATED("Recently updated", Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));

	private final String label;

	private final Sort sort;

	RecordSort(String label, Sort sort) {
		this.label = label;
		this.sort = sort;
	}

	public String getLabel() {
		return label;
	}

	public Sort sort() {
		return sort;
	}

}
