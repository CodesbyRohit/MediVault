package com.medivault.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.medivault.IntegrationTestSupport;
import com.medivault.record.MedicalRecord;
import com.medivault.user.User;

/**
 * Upload and download through a real Tomcat: the CSRF token travels inside the multipart body
 * (as the browser form sends it), multipart limits are applied by the container, and the
 * download is streamed with its real headers. MockMvc cannot show any of this.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealServerDocumentTests extends IntegrationTestSupport {

	private static final Pattern SESSION_COOKIE = Pattern.compile("JSESSIONID=([^;]+)");

	private static final Pattern CSRF_TOKEN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

	@LocalServerPort
	private int port;

	private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private HttpResponse<String> get(String path, String session) throws Exception {
		return http.send(HttpRequest.newBuilder(uri(path)).header("Cookie", "JSESSIONID=" + session).GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private static String group(Pattern pattern, String text) {
		Matcher matcher = pattern.matcher(text);
		assertThat(matcher.find()).as("pattern %s", pattern).isTrue();
		return matcher.group(1);
	}

	private String logIn(String email) throws Exception {
		HttpResponse<String> loginPage = http.send(HttpRequest.newBuilder(uri("/login")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		String session = group(SESSION_COOKIE, loginPage.headers().firstValue("Set-Cookie").orElseThrow());
		String form = "_csrf=" + URLEncoder.encode(group(CSRF_TOKEN, loginPage.body()), StandardCharsets.UTF_8)
				+ "&email=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=" + PASSWORD;
		HttpResponse<String> login = http.send(HttpRequest.newBuilder(uri("/login"))
			.header("Cookie", "JSESSIONID=" + session)
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(form))
			.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(login.statusCode()).isEqualTo(302);
		return group(SESSION_COOKIE, login.headers().firstValue("Set-Cookie").orElseThrow());
	}

	/** A browser-style multipart body: optional _csrf field first, then the file. */
	private HttpResponse<String> postUpload(String path, String session, String csrf, String filename, byte[] content)
			throws Exception {
		String boundary = "----MediVaultTest" + UUID.randomUUID();
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		if (csrf != null) {
			body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"_csrf\"\r\n\r\n" + csrf
					+ "\r\n").getBytes(StandardCharsets.UTF_8));
		}
		body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
				+ "\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		body.writeBytes(content);
		body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		return http.send(HttpRequest.newBuilder(uri(path))
			.header("Cookie", "JSESSIONID=" + session)
			.header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
			.build(), HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void browserStyleUploadAndDownloadWorkThroughTomcat() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		String session = logIn("asha@example.test");
		String recordPath = "/records/" + record.getId();
		String csrf = group(CSRF_TOKEN, get(recordPath, session).body());
		byte[] pdf = FictionalPdf.bytes("Real server upload");

		HttpResponse<String> upload = postUpload(recordPath + "/documents", session, csrf, "fictional-scan.pdf", pdf);

		assertThat(upload.statusCode()).isEqualTo(302);
		assertThat(upload.headers().firstValue("Location").orElseThrow()).endsWith(recordPath);
		HttpResponse<String> page = get(recordPath, session);
		assertThat(page.body()).contains("Document uploaded.").contains("fictional-scan.pdf");

		RecordDocument document = documentRepository.findAll().get(0);
		HttpResponse<byte[]> download = http.send(HttpRequest.newBuilder(uri(recordPath + "/documents/" + document.getId()))
			.header("Cookie", "JSESSIONID=" + session)
			.GET()
			.build(), HttpResponse.BodyHandlers.ofByteArray());
		assertThat(download.statusCode()).isEqualTo(200);
		assertThat(download.body()).isEqualTo(pdf);
		assertThat(download.headers().firstValue("Content-Type")).hasValue("application/pdf");
		assertThat(download.headers().firstValue("Content-Disposition").orElseThrow()).startsWith("attachment;");
		assertThat(download.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
		assertThat(download.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
	}

	/** Through real Tomcat error handling (HTML and JSON): no stack trace, exception, path or key. */
	@Test
	void aServerSideDownloadFailureRevealsNothingInternal() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		String session = logIn("asha@example.test");
		String recordPath = "/records/" + record.getId();
		postUpload(recordPath + "/documents", session, group(CSRF_TOKEN, get(recordPath, session).body()),
				"fictional.pdf", FictionalPdf.bytes("Error page check"));
		RecordDocument document = documentRepository.findAll().get(0);
		java.nio.file.Files.delete(java.nio.file.Path.of(System.getProperty("user.dir"), "target", "test-storage",
				"records", document.getStorageKey()));
		String url = recordPath + "/documents/" + document.getId();

		for (String accept : java.util.List.of("text/html", "application/json")) {
			HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri(url))
				.header("Cookie", "JSESSIONID=" + session)
				.header("Accept", accept)
				.GET()
				.build(), HttpResponse.BodyHandlers.ofString());
			assertThat(response.statusCode()).as(accept).isEqualTo(500);
			assertThat(response.body()).as(accept)
				.doesNotContain(document.getStorageKey())
				.doesNotContain("test-storage")
				.doesNotContain("Users")
				.doesNotContainIgnoringCase("exception")
				.doesNotContain("\"trace\"")
				.doesNotContain("\"message\"")
				.doesNotContain("at com.medivault")
				.doesNotContain("jdbc:");
		}
	}

	@Test
	void aMultipartUploadWithoutTheCsrfTokenIsRefused() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		String session = logIn("asha@example.test");

		HttpResponse<String> upload = postUpload("/records/" + record.getId() + "/documents", session, null, "x.pdf",
				FictionalPdf.bytes("No token"));

		assertThat(upload.statusCode()).isEqualTo(403);
		assertThat(documentRepository.count()).isZero();
	}

	@Test
	void anUploadOverTheContainerLimitIsRefusedAndStoresNothing() throws Exception {
		User asha = createPatient("Asha Fictional", "asha@example.test");
		MedicalRecord record = createRecord(asha, "Asha lipid profile");
		String session = logIn("asha@example.test");
		String recordPath = "/records/" + record.getId();
		String csrf = group(CSRF_TOKEN, get(recordPath, session).body());

		HttpResponse<String> upload = postUpload(recordPath + "/documents", session, csrf, "big.pdf",
				FictionalPdf.ofSize(12 * 1024 * 1024));

		// Refused by the container's multipart limit (before CSRF passes), then sent back to the record page.
		assertThat(upload.statusCode()).isEqualTo(302);
		assertThat(upload.headers().firstValue("Location").orElseThrow()).endsWith(recordPath);
		assertThat(get(recordPath, session).body()).contains("The file is too large. The limit is 10 MB.")
			.doesNotContain("Document uploaded.");
		assertThat(documentRepository.count()).isZero();
	}

}
