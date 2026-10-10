package com.medivault;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import com.medivault.security.MediVaultUserDetailsService;

class MedivaultApplicationTests extends IntegrationTestSupport {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private Environment environment;

	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoadsWithOnlyTheTestProfile() {
		assertThat(environment.getActiveProfiles()).containsExactly("test");
	}

	@Test
	void testsAlwaysUseTheInMemoryH2Database() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:medivault_test");
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
		}
	}

	@Test
	void loginIsDatabaseBackedAndNoGeneratedDefaultUserExists() {
		assertThat(context.getBeansOfType(InMemoryUserDetailsManager.class)).isEmpty();
		assertThat(context.getBean(UserDetailsService.class)).isInstanceOf(MediVaultUserDetailsService.class);
	}

}
