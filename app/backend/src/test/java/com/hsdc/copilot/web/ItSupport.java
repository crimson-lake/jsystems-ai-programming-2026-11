package com.hsdc.copilot.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import com.hsdc.copilot.cases.CaseForm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared helpers of the T15/T16 integration tests: SSE parsing, WireMock stub loading, multipart requests,
 * hygiene checks. References only T14 types ({@link CaseForm}) from production code.
 */
final class ItSupport {

	static final JsonMapper MAPPER = JsonMapper.builder().build();
	static final Duration TURN_TIMEOUT = Duration.ofSeconds(40);
	static final String RESPONSES_PATH = "/api/v1/responses";
	static final String DECISIONS_PATH = "/api/alpha/decisions";
	static final String VISION_KIND = "VISION";
	static final String DECISION_KIND = "DECISION";
	static final String STREAM_KIND = "STREAM";

	/** TAC-002-04: case? progress+ (error | decision progress delta* (done|error)). */
	static final Pattern EVENT_ORDER = Pattern
		.compile("^(case )?(progress )+(error|decision progress( delta)*( done| error))$");

	private ItSupport() {
	}

	record SseEvent(String name, JsonNode data) {
	}

	// ---------------------------------------------------------------- SSE

	static List<SseEvent> parseSse(String body) {
		List<SseEvent> events = new ArrayList<>();
		for (String block : body.replace("\r\n", "\n").split("\n\n")) {
			String name = null;
			StringBuilder data = new StringBuilder();
			for (String line : block.split("\n")) {
				if (line.startsWith("event:")) {
					name = line.substring(6).trim();
				}
				else if (line.startsWith("data:")) {
					data.append(line.substring(5).stripLeading());
				}
			}
			if (name != null) {
				events.add(new SseEvent(name, data.isEmpty() ? null : MAPPER.readTree(data.toString())));
			}
		}
		return events;
	}

	static String names(List<SseEvent> events) {
		return String.join(" ", events.stream().map(SseEvent::name).toList());
	}

	static void assertTacOrder(List<SseEvent> events) {
		assertThat(names(events)).as("event order (TAC-002-04)").matches(EVENT_ORDER);
	}

	static SseEvent first(List<SseEvent> events, String name) {
		return events.stream().filter(e -> e.name().equals(name)).findFirst()
			.orElseThrow(() -> new AssertionError("no '" + name + "' event in: " + names(events)));
	}

	static List<String> progressSteps(List<SseEvent> events) {
		return events.stream().filter(e -> e.name().equals("progress")).map(e -> e.data().path("step").asString())
			.toList();
	}

	static String joinedDeltas(List<SseEvent> events) {
		StringBuilder sb = new StringBuilder();
		events.stream().filter(e -> e.name().equals("delta")).forEach(e -> sb.append(e.data().path("text").asString()));
		return sb.toString();
	}

	static boolean isNullOrMissing(JsonNode node) {
		return node == null || node.isNull() || node.isMissingNode();
	}

	// ---------------------------------------------------------------- requests

	static String formJson(CaseForm form) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("caseType", form.caseType() == null ? null : form.caseType().name());
		map.put("category", form.category() == null ? null : form.category().name());
		map.put("model", form.model());
		map.put("purchaseDate", form.purchaseDate() == null ? null : form.purchaseDate().toString());
		map.put("reason", form.reason());
		return MAPPER.writeValueAsString(map);
	}

	static byte[] photo(String fileName) {
		try {
			// Failsafe runs with the module directory (app/backend) as working directory.
			Path path = Path.of("../../assets/example-images").resolve(fileName);
			assertThat(path).as("real example photo " + fileName).exists();
			return Files.readAllBytes(path);
		}
		catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	static String contentTypeOf(String fileName) {
		return fileName.endsWith(".webp") ? "image/webp" : fileName.endsWith(".png") ? "image/png" : "image/jpeg";
	}

	/** POST /api/cases with a JSON form part and, when photoName is not null, the real example photo. */
	static MvcTestResult submitCase(MockMvcTester mvc, CaseForm form, String photoName) {
		return submitCaseJson(mvc, formJson(form), photoName);
	}

	static MvcTestResult submitCaseJson(MockMvcTester mvc, String formJson, String photoName) {
		var request = mvc.post().multipart().uri("/api/cases")
			.accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
			.file(new MockMultipartFile("form", "form.json", "application/json",
					formJson.getBytes(StandardCharsets.UTF_8)));
		if (photoName != null) {
			request = request.file(new MockMultipartFile("photo", photoName, contentTypeOf(photoName), photo(photoName)));
		}
		return request.exchange(TURN_TIMEOUT);
	}

	static MvcTestResult sendMessage(MockMvcTester mvc, UUID caseId, UUID clientMessageId, String text,
			String photoName) {
		return messageRequest(mvc, caseId, clientMessageId, text, photoName).exchange(TURN_TIMEOUT);
	}

	/** Starts the turn without waiting for its completion (for concurrency tests). */
	static MvcTestResult sendMessageAsync(MockMvcTester mvc, UUID caseId, UUID clientMessageId, String text,
			String photoName) {
		return messageRequest(mvc, caseId, clientMessageId, text, photoName).asyncExchange();
	}

	private static MockMvcTester.MockMultipartMvcRequestBuilder messageRequest(MockMvcTester mvc, UUID caseId,
			UUID clientMessageId, String text, String photoName) {
		var request = mvc.post().multipart().uri("/api/cases/{id}/messages", caseId)
			.accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
			.part(new MockPart("clientMessageId", clientMessageId.toString().getBytes(StandardCharsets.UTF_8)));
		if (text != null) {
			request = request.part(new MockPart("text", text.getBytes(StandardCharsets.UTF_8)));
		}
		if (photoName != null) {
			request = request.file(new MockMultipartFile("photo", photoName, contentTypeOf(photoName), photo(photoName)));
		}
		return request;
	}

	static String body(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		}
		catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	static List<SseEvent> sse(MvcTestResult result) {
		assertThat(result.getResponse().getStatus()).as("HTTP status of the stream, body: " + body(result)).isEqualTo(200);
		assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
		return parseSse(body(result));
	}

	static JsonNode json(MvcTestResult result) {
		return MAPPER.readTree(body(result));
	}

	// ---------------------------------------------------------------- hygiene (AC-42, TAC-04, TAC-002-06)

	static void assertNoInternals(String body) {
		assertThat(body).doesNotContain("Exception", "at com.", "at org.", "java.", "test-dummy-key", "test/vision-model",
				"test/decision-model", "store\":true", "previous_response_id");
		assertThat(body.toLowerCase()).doesNotContain("gpt", "luna", "openrouter", "stacktrace", "x-powered-by");
		for (String snippet : promptSnippets()) {
			assertThat(body).as("prompt text must not leak").doesNotContain(snippet);
		}
	}

	private static List<String> promptSnippets() {
		List<String> snippets = new ArrayList<>();
		try {
			Resource[] prompts = new PathMatchingResourcePatternResolver().getResources("classpath:prompts/*.md");
			assertThat(prompts).as("prompt files on the classpath").isNotEmpty();
			for (Resource prompt : prompts) {
				try (InputStream in = prompt.getInputStream()) {
					new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().map(String::strip)
						.filter(l -> l.length() >= 40 && !l.startsWith("{{") && !l.startsWith("#")).limit(3)
						.forEach(snippets::add);
				}
			}
		}
		catch (IOException e) {
			throw new AssertionError(e);
		}
		return snippets;
	}

	// ---------------------------------------------------------------- WireMock

	static void loadStubs(WireMockServer wm, String dir, String... names) {
		for (String name : names) {
			wm.addStubMapping(StubMapping.buildFrom(read("wiremock/" + dir + "/stubs/" + name + ".json")));
		}
	}

	static String read(String classpathLocation) {
		try (InputStream in = new ClassPathResource(classpathLocation).getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new AssertionError(classpathLocation, e);
		}
	}

	static List<LoggedRequest> responsesRequests(WireMockServer wm) {
		return wm.getAllServeEvents().stream().map(ServeEvent::getRequest)
			.filter(r -> r.getUrl().startsWith(RESPONSES_PATH)).sorted((a, b) -> a.getLoggedDate().compareTo(b.getLoggedDate()))
			.toList();
	}

	static List<LoggedRequest> responsesRequests(WireMockServer wm, String kind) {
		return responsesRequests(wm).stream().filter(r -> kind.equals(kindOf(r))).toList();
	}

	static long decisionsApiRequestCount(WireMockServer wm) {
		return wm.getAllServeEvents().stream().filter(e -> e.getRequest().getUrl().startsWith(DECISIONS_PATH)).count();
	}

	/** VISION, DECISION or STREAM, by the same discriminators the stubs use. */
	static String kindOf(LoggedRequest request) {
		JsonNode body = MAPPER.readTree(request.getBodyAsString());
		if (body.path("stream").asBoolean(false)) {
			return STREAM_KIND;
		}
		JsonNode props = body.path("text").path("format").path("schema").path("properties");
		if (props.has("safetyHazard")) {
			return VISION_KIND;
		}
		if (props.has("ruleIds")) {
			return DECISION_KIND;
		}
		return "OTHER";
	}

	/**
	 * Waits until the turn has passed the structured decision call and is inside the delayed streaming call.
	 * WireMock journals a request only after its response completed, so an in-flight delayed request cannot be
	 * polled; the completed decision request is the observable signal, followed by a short pause.
	 */
	static void awaitTurnInStreamStep(WireMockServer wm, Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		try {
			while (System.nanoTime() < deadline) {
				if (!responsesRequests(wm, DECISION_KIND).isEmpty()) {
					Thread.sleep(800);
					return;
				}
				Thread.sleep(50);
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}
		throw new AssertionError("the turn did not reach the decision call within " + timeout);
	}

	/** Text of all response.output_text.delta events of a recorded SSE capture. */
	static String capturedStreamText(String captureFile) {
		StringBuilder sb = new StringBuilder();
		for (String line : read("openrouter-captures/" + captureFile).split("\n")) {
			if (line.startsWith("data: {") && line.contains("\"type\":\"response.output_text.delta\"")) {
				sb.append(MAPPER.readTree(line.substring(6)).path("delta").asString());
			}
		}
		return sb.toString();
	}

	// ---------------------------------------------------------------- policy checks (plan section 4.5, TAC-001-06)

	/** Every stub mapping references an existing capture and its body file is byte-identical to the capture. */
	static void assertStubsReferenceCaptures(String dir) throws IOException {
		Resource[] mappings = new PathMatchingResourcePatternResolver()
			.getResources("classpath:wiremock/" + dir + "/stubs/*.json");
		assertThat(mappings).isNotEmpty();
		for (Resource mapping : mappings) {
			JsonNode node = MAPPER.readTree(new String(mapping.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
			String source = node.path("metadata").path("source").asString();
			assertThat(new ClassPathResource("openrouter-captures/" + source + ".meta.json").exists())
				.as(mapping.getFilename() + " references capture " + source).isTrue();
			String bodyFile = node.path("response").path("bodyFileName").asString(null);
			if (bodyFile != null) {
				assertThat(bodyFile).startsWith(source);
				byte[] stub = new ClassPathResource("wiremock/" + dir + "/__files/" + bodyFile).getInputStream()
					.readAllBytes();
				byte[] capture = new ClassPathResource("openrouter-captures/" + bodyFile).getInputStream().readAllBytes();
				assertThat(stub).as(bodyFile + " is a verbatim copy of the capture").isEqualTo(capture);
				assertThat(node.path("response").path("status").asInt())
					.isEqualTo(MAPPER.readTree(read("openrouter-captures/" + source + ".meta.json")).path("httpStatus").asInt());
			}
			else {
				assertThat(node.path("response").path("fault").asString()).isEqualTo("CONNECTION_RESET_BY_PEER");
			}
		}
	}

	/** Proves the WireMock wiring and the discriminators: the captured requests are answered by the stubs. */
	static void assertStubServerAnswersCapturedRequests(WireMockServer wm, String dir) throws Exception {
		loadStubs(wm, dir, "vision-ok", "decision-ok", "stream-ok");
		HttpClient client = HttpClient.newHttpClient();
		String[][] cases = {
				{ "responses-a-vision-structured", ".json", "application/json" },
				{ "responses-b-decision-structured", ".json", "application/json" },
				{ "responses-c-streaming-text", ".sse", "text/event-stream" } };
		for (String[] c : cases) {
			HttpRequest request = HttpRequest.newBuilder(URI.create(wm.baseUrl() + RESPONSES_PATH))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(read("openrouter-captures/" + c[0] + ".request.json"))).build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			assertThat(response.statusCode()).as(c[0]).isEqualTo(200);
			assertThat(response.headers().firstValue("content-type").orElse("")).startsWith(c[2]);
			assertThat(response.body()).as(c[0]).isEqualTo(read("openrouter-captures/" + c[0] + c[1]));
		}
		assertThat(responsesRequests(wm).stream().map(ItSupport::kindOf).toList())
			.containsExactly(VISION_KIND, DECISION_KIND, STREAM_KIND);
	}
}
