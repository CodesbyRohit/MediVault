package com.medivault;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

// @TestPropertySource outranks OS environment variables (e.g. SPRING_DATASOURCE_URL),
// so the H2 settings always win and tests never reach a real database.
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class MedivaultApplicationTests {

	@Test
	void contextLoads() {
	}

}
