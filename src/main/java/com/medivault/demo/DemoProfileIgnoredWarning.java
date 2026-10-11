package com.medivault.demo;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Makes it visible when {@code demo} is requested without {@code local-h2} or {@code local-file}:
 * demo records are then deliberately NOT created, so they can never reach a MySQL database.
 */
@Component
@Profile("demo & !local-h2 & !local-file")
public class DemoProfileIgnoredWarning {

	private static final Logger log = LoggerFactory.getLogger(DemoProfileIgnoredWarning.class);

	@PostConstruct
	void warn() {
		log.warn("The 'demo' profile is active without 'local-h2' or 'local-file': no demo records will be created. "
				+ "Fictional demo records are only available together with one of the local H2 profiles.");
	}

}
