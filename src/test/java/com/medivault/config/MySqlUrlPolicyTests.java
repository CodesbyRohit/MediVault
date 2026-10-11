package com.medivault.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MySqlUrlPolicyTests {

	private static final String OK = "jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA";

	@Test
	void acceptsTheApprovedShape() {
		assertThat(MySqlUrlPolicy.parse(OK, "s")).isEqualTo(new MySqlUrlPolicy.Target(3307, "medivault_dev", "dev"));
		assertThat(MySqlUrlPolicy.parse("jdbc:mysql://127.0.0.1:3308/medivault_it?sslMode=verify_identity"
				+ "&trustCertificateKeyStoreUrl=file:/D:/MediVaultData/mysql84/tls/mysql-ca-truststore.p12"
				+ "&trustCertificateKeyStoreType=PKCS12&connectTimeout=5000", "s"))
			.isEqualTo(new MySqlUrlPolicy.Target(3308, "medivault_it", "it"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void missingUrlIsRejected(String url) {
		assertThatThrownBy(() -> MySqlUrlPolicy.parse(url, "s")).hasMessageContaining("it is missing");
	}

	@ParameterizedTest(name = "[{index}] {1}")
	@CsvSource(delimiter = '|', value = {
			// scheme
			"jdbc:mariadb://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA | must start with",
			"jdbc:mysql+srv://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA | must start with",
			"jdbc:mysql:loadbalance://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA | must start with",
			"JDBC:MYSQL://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA | must start with",
			// host
			"jdbc:mysql://localhost:3307/medivault_dev?sslMode=VERIFY_CA | host must be exactly",
			"jdbc:mysql://0.0.0.0:3307/medivault_dev?sslMode=VERIFY_CA | host must be exactly",
			"jdbc:mysql://127.0.0.2:3307/medivault_dev?sslMode=VERIFY_CA | host must be exactly",
			"jdbc:mysql://db.example.test:3307/medivault_dev?sslMode=VERIFY_CA | host must be exactly",
			"jdbc:mysql://[::1]:3307/medivault_dev?sslMode=VERIFY_CA | single host",
			"jdbc:mysql://127.0.0.1:3307,127.0.0.1:3308/medivault_dev?sslMode=VERIFY_CA | single host",
			"jdbc:mysql://address=(host=127.0.0.1)(port=3307)/medivault_dev?sslMode=VERIFY_CA | single host",
			"jdbc:mysql://probe:x@127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA | credentials are not allowed",
			// port
			"jdbc:mysql://127.0.0.1/medivault_dev?sslMode=VERIFY_CA | explicit numeric port",
			"jdbc:mysql://127.0.0.1:/medivault_dev?sslMode=VERIFY_CA | explicit numeric port",
			"jdbc:mysql://127.0.0.1:abc/medivault_dev?sslMode=VERIFY_CA | explicit numeric port",
			"jdbc:mysql://127.0.0.1:-1/medivault_dev?sslMode=VERIFY_CA | explicit numeric port",
			"jdbc:mysql://127.0.0.1:123456/medivault_dev?sslMode=VERIFY_CA | explicit numeric port",
			"jdbc:mysql://127.0.0.1:3306/medivault_dev?sslMode=VERIFY_CA | port 3306 is never allowed",
			"jdbc:mysql://127.0.0.1:03306/medivault_dev?sslMode=VERIFY_CA | port 3306 is never allowed",
			"jdbc:mysql://127.0.0.1:0/medivault_dev?sslMode=VERIFY_CA | out of range",
			"jdbc:mysql://127.0.0.1:65536/medivault_dev?sslMode=VERIFY_CA | out of range",
			// database
			"jdbc:mysql://127.0.0.1:3307?sslMode=VERIFY_CA | database name is required",
			"jdbc:mysql://127.0.0.1:3307/?sslMode=VERIFY_CA | medivault_dev or medivault_it",
			"jdbc:mysql://127.0.0.1:3307/mysql?sslMode=VERIFY_CA | medivault_dev or medivault_it",
			"jdbc:mysql://127.0.0.1:3307/medivault_prod?sslMode=VERIFY_CA | medivault_dev or medivault_it",
			"jdbc:mysql://127.0.0.1:3307/MEDIVAULT_DEV?sslMode=VERIFY_CA | medivault_dev or medivault_it",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev/x?sslMode=VERIFY_CA | medivault_dev or medivault_it",
			// TLS
			"jdbc:mysql://127.0.0.1:3307/medivault_dev | sslMode is required",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?connectTimeout=5000 | sslMode is required",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=DISABLED | VERIFY_CA or VERIFY_IDENTITY",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=PREFERRED | VERIFY_CA or VERIFY_IDENTITY",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=REQUIRED | VERIFY_CA or VERIFY_IDENTITY",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA&trustCertificateKeyStoreUrl=http://x.test/t.p12 | must be a file: URL",
			// parameters (allow-list)
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA&useSSL=false | not on the allow-list",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA&allowPublicKeyRetrieval=true | not on the allow-list",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA&user=root | not on the allow-list",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA&password=x | not on the allow-list",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA&trustCertificateKeyStorePassword=x | not on the allow-list",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA&allowLoadLocalInfile=true | not on the allow-list",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA&autoDeserialize=true | not on the allow-list",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA&allowMultiQueries=true | not on the allow-list",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA&socketFactory=x | not on the allow-list",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?ssl%4Dode=VERIFY_CA | not on the allow-list",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA&sslMode=VERIFY_CA | duplicate parameter",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?SSLMODE=VERIFY_CA&sslMode=VERIFY_CA | duplicate parameter",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA&& | key=value",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode= | key=value",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode | key=value",
			"jdbc:mysql://127.0.0.1:3307/medivault_dev?sslMode=VERIFY_CA#frag | fragments and whitespace" })
	void rejectsEverythingOutsideTheApprovedShape(String url, String reason) {
		assertThatThrownBy(() -> MySqlUrlPolicy.parse(url, "spring.datasource.url"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageStartingWith("Refusing to start: spring.datasource.url was rejected by the MySQL connection policy")
			.hasMessageContaining(reason);
	}

	@Test
	void rejectionMessagesNeverEchoTheUrl() {
		String url = "jdbc:mysql://127.0.0.1:3306/medivault_dev?sslMode=VERIFY_CA&x=not-a-real-secret";
		assertThatThrownBy(() -> MySqlUrlPolicy.parse(url, "s")).message()
			.doesNotContain("not-a-real-secret")
			.doesNotContain("jdbc:mysql://");
	}

}
