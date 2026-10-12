package com.medivault.record;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

import org.springframework.data.jpa.domain.Specification;

/**
 * Query building blocks for the records page. {@link #ownedBy(Long)} is the mandatory starting
 * point: {@link #search(Long, RecordSearchForm)} always combines it with the patient's options, so
 * no search can ever reach another patient's records.
 */
final class RecordSpecifications {

	/**
	 * Escape character for LIKE patterns, so a search for "%" or "_" matches only that character. Not
	 * a backslash: MySQL treats a backslash inside string literals as an escape itself.
	 */
	static final char ESCAPE = '!';

	private RecordSpecifications() {
	}

	static Specification<MedicalRecord> search(Long ownerId, RecordSearchForm criteria) {
		Specification<MedicalRecord> spec = ownedBy(ownerId);
		if (criteria.getQ() != null) {
			spec = spec.and(containsText(criteria.getQ()));
		}
		if (criteria.getType() != null) {
			spec = spec.and(hasType(criteria.getType()));
		}
		if (criteria.getProvider() != null) {
			spec = spec.and(hasProvider(criteria.getProvider()));
		}
		if (criteria.getFrom() != null) {
			spec = spec.and(onOrAfter(criteria.getFrom()));
		}
		if (criteria.getTo() != null) {
			spec = spec.and(onOrBefore(criteria.getTo()));
		}
		return spec;
	}

	static Specification<MedicalRecord> ownedBy(Long ownerId) {
		Objects.requireNonNull(ownerId, "ownerId");
		return (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId);
	}

	/** Case-insensitive match in the title, provider or notes. */
	static Specification<MedicalRecord> containsText(String text) {
		String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
		return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get("title")), pattern, ESCAPE),
				cb.like(cb.lower(root.get("providerName")), pattern, ESCAPE),
				cb.like(cb.lower(root.get("notes")), pattern, ESCAPE));
	}

	static Specification<MedicalRecord> hasType(RecordType type) {
		return (root, query, cb) -> cb.equal(root.get("recordType"), type);
	}

	static Specification<MedicalRecord> hasProvider(String provider) {
		return (root, query, cb) -> cb.equal(root.get("providerName"), provider);
	}

	static Specification<MedicalRecord> onOrAfter(LocalDate date) {
		return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("recordDate"), date);
	}

	static Specification<MedicalRecord> onOrBefore(LocalDate date) {
		return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("recordDate"), date);
	}

	static String escapeLike(String text) {
		StringBuilder escaped = new StringBuilder(text.length());
		for (char c : text.toCharArray()) {
			if (c == ESCAPE || c == '%' || c == '_') {
				escaped.append(ESCAPE);
			}
			escaped.append(c);
		}
		return escaped.toString();
	}

}
