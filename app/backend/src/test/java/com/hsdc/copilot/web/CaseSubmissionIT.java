package com.hsdc.copilot.web;

import static com.hsdc.copilot.web.ItSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

import com.hsdc.copilot.cases.CaseForm;
import com.hsdc.copilot.cases.CaseStore;
import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.cases.Category;
import com.hsdc.copilot.web.ItSupport.SseEvent;

import tools.jackson.databind.JsonNode;

/**
 * T15: SSE/HTTP contract of POST /api/cases (ADR-002 sections 3.5, 5; ADR-000 section 6; TAC-03/04, AC-22, 24, 26, 40, 42).
 * OpenRouter is served by WireMock stubs copied verbatim from the S-1 captures (wiremock/case-submission/**).
 * Triage runs in assist mode (T29): the recorded Jev Decisions stubs are loaded for every test; one Decisions call
 * per first submission is asserted.
 * Red until T26: no handler (404) and no CaseStore bean.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnableWireMock(@ConfigureWireMock(name = "openrouter", filesUnderClasspath = "wiremock/case-submission",
		baseUrlProperties = "it.wiremock.url"))
@TestPropertySource(properties = { "openrouter.base-url=${it.wiremock.url}/api/v1",
		"triage.base-url=${it.wiremock.url}/api/alpha/decisions" })
class CaseSubmissionIT {

	private static final String DIR = "case-submission";
	private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
	private static final String AI_ERROR_PL = "Nie udało się przeanalizować zgłoszenia. Spróbuj ponownie.";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private ObjectProvider<CaseStore> caseStores;

	@InjectWireMock("openrouter")
	private WireMockServer wm;

	@BeforeEach
	void resetStubServer() {
		wm.resetAll();
		// T29: triage runs in assist mode (application-test.yml); the recorded Jev answers serve every first submission.
		loadStubs(wm, DIR, "triage-return-ok", "triage-complaint-ok");
	}

	private static LocalDate today() {
		return LocalDate.now(WARSAW);
	}

	private static CaseForm returnForm(int daysAgo, String reason) {
		return new CaseForm(CaseType.RETURN, Category.SMARTPHONE, "Galaxy S23", today().minusDays(daysAgo), reason);
	}

	private static UUID caseIdOf(List<SseEvent> events) {
		return UUID.fromString(first(events, "case").data().path("caseId").asString());
	}

	private CaseStore store() {
		return caseStores.getObject();
	}

	// ------------------------------------------------------------ wiring sanity (expected to pass even while red)

	@Test
	void wireMockServesRecordedCapturesAndDiscriminatorsMatch() throws Exception {
		assertStubServerAnswersCapturedRequests(wm, DIR);
	}

	@Test
	void decisionsStubsAnswerTheRecordedTriageRequests() throws Exception {
		wm.resetAll();
		assertTriageStubsAnswerCapturedRequests(wm, DIR);
	}

	@Test
	void everyStubMappingIsAVerbatimCapture() throws IOException {
		assertStubsReferenceCaptures(DIR);
	}

	// ------------------------------------------------------------ happy path

	@Test
	void happyPath_streamsCaseProgressDecisionDeltaDone_andStoresTheCase() {
		loadStubs(wm, DIR, "vision-ok", "decision-ok", "stream-ok");
		CaseForm form = returnForm(10, "Zmiana zdania");

		MvcTestResult result = submitCase(mvc, form, "phone-1.jpg");
		List<SseEvent> events = sse(result);

		assertTacOrder(events);
		assertThat(names(events)).startsWith("case progress progress progress decision progress delta").endsWith(" done");
		assertThat(progressSteps(events)).containsExactly("UPLOAD_RECEIVED", "ANALYZING_PHOTO", "PREPARING_DECISION",
				"WRITING_REPLY");

		UUID caseId = caseIdOf(events);
		JsonNode decision = first(events, "decision").data();
		assertThat(decision.path("label").asString()).isIn("ACCEPTED_PRELIMINARY", "REJECTED", "NEEDS_MORE_INFO",
				"ESCALATED");
		assertThat(decision.path("changed").asBoolean(true)).isFalse();
		assertThat(isNullOrMissing(decision.get("previousLabel"))).isTrue();
		assertThat(decision.has("changeReasonPl")).isTrue();
		assertThat(decision.has("safety")).isTrue();
		assertThat(decision.path("suggestOtherCaseType").asString()).isIn("NONE", "RETURN", "COMPLAINT");
		assertThat(decision.path("ruleIds").isArray()).isTrue();
		assertThat(decision.path("ruleIds").size()).isBetween(1, 5);

		// The stub's streamed text, forwarded unchanged (it cites no rule ID, so the rule-ID filter passes it).
		assertThat(joinedDeltas(events)).isEqualTo(capturedStreamText("responses-c-streaming-text.sse"));
		JsonNode done = events.get(events.size() - 1).data();
		assertThat(UUID.fromString(done.path("messageId").asString())).isNotNull();

		// One call per step; one triage call (assist mode, T29); nothing in the outgoing requests that OpenRouter
		// rejects (TAC-001-02).
		assertThat(responsesRequests(wm).stream().map(ItSupport::kindOf).toList())
			.containsExactly(VISION_KIND, DECISION_KIND, STREAM_KIND);
		assertThat(decisionsApiRequestCount(wm)).isEqualTo(1);
		responsesRequests(wm).forEach(r -> assertThat(r.getBodyAsString()).doesNotContain("\"store\":true")
			.doesNotContain("previous_response_id"));

		// The committed case exists in the store (AC-40 guarantee is the mirror image of this).
		var stored = store().find(caseId);
		assertThat(stored).isPresent();
		assertThat(stored.get().getForm()).isEqualTo(form);
		assertThat(stored.get().getAnalyses()).hasSize(1);
		assertThat(stored.get().getCurrentDecision()).isNotNull();
		assertThat(stored.get().getCurrentDecision().label().name()).isEqualTo(decision.path("label").asString());

		assertNoInternals(body(result));
	}

	// ------------------------------------------------------------ upstream failures: error last, no case stored

	static Stream<Arguments> upstreamFailures() {
		// name, stubs, decisionSentBeforeError
		return Stream.of(
				Arguments.of("vision 401 (responses-e)", new String[] { "vision-401", "decision-ok", "stream-ok" }, false),
				Arguments.of("vision 400 (responses-f)", new String[] { "vision-400", "decision-ok", "stream-ok" }, false),
				Arguments.of("decision 401 (responses-e)", new String[] { "vision-ok", "decision-401", "stream-ok" },
						false),
				Arguments.of("stream connection reset", new String[] { "vision-ok", "decision-ok", "stream-reset" }, true),
				Arguments.of("stream ends with response.incomplete (responses-g)",
						new String[] { "vision-ok", "decision-ok", "stream-incomplete" }, true));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("upstreamFailures")
	void upstreamFailure_endsWithErrorEventAndStoresNoCase(String scenario, String[] stubs, boolean decisionSent) {
		loadStubs(wm, DIR, stubs);

		MvcTestResult result = submitCase(mvc, returnForm(10, "Zmiana zdania"), "phone-1.jpg");
		List<SseEvent> events = sse(result);

		assertTacOrder(events);
		SseEvent last = events.get(events.size() - 1);
		assertThat(last.name()).isEqualTo("error");
		assertThat(last.data().path("code").asString()).isEqualTo("AI_UNAVAILABLE");
		assertThat(last.data().path("message").asString()).isEqualTo(AI_ERROR_PL);
		assertThat(names(events)).doesNotContain("done");
		assertThat(names(events).contains("decision")).isEqualTo(decisionSent);

		UUID caseId = caseIdOf(events);
		assertThat(store().find(caseId)).as("no case after an error (ADR-002 section 5)").isEmpty();
		// T29: triage runs first (ADR-004 section 3.1), so the turn made exactly one Decisions call before failing.
		assertThat(decisionsApiRequestCount(wm)).isEqualTo(1);
		assertNoInternals(body(result));
	}

	// ------------------------------------------------------------ validation (400)

	@Test
	void invalidModelAndMissingPhoto_return400WithPolishFieldErrors() {
		loadStubs(wm, DIR, "vision-ok", "decision-ok", "stream-ok");

		MvcTestResult result = submitCase(mvc, new CaseForm(CaseType.RETURN, Category.SMARTPHONE, "A",
				today().minusDays(3), "Zmiana zdania"), null);

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(result.getResponse().getContentType()).startsWith("application/json");
		JsonNode body = json(result);
		assertThat(body.path("code").asString()).isEqualTo("VALIDATION");
		assertThat(fieldMessage(body, "model")).isEqualTo("Podaj nazwę lub model urządzenia (2–100 znaków)");
		assertThat(fieldMessage(body, "photo")).isEqualTo("Dodaj zdjęcie urządzenia.");
		assertThat(responsesRequests(wm)).as("validation fails before any AI call").isEmpty();
		assertNoInternals(body(result));
	}

	static Stream<Arguments> invalidForms() {
		LocalDate today = LocalDate.now(ZoneId.of("Europe/Warsaw"));
		return Stream.of(
				Arguments.of(new CaseForm(CaseType.RETURN, Category.SMARTPHONE, "Galaxy S23", today.plusDays(1), null),
						"purchaseDate", "Data zakupu nie może być w przyszłości"),
				Arguments.of(new CaseForm(CaseType.RETURN, Category.SMARTPHONE, "Galaxy S23",
						today.minusYears(10).minusDays(1), null), "purchaseDate", "Data zakupu jest zbyt odległa"),
				Arguments.of(new CaseForm(CaseType.COMPLAINT, Category.LAPTOP, "ThinkPad T14", today.minusDays(5),
						"Za krótki opis"), "reason", "Opisz problem (min. 20 znaków)"));
	}

	@ParameterizedTest
	@MethodSource("invalidForms")
	void invalidForm_returns400WithFieldError(CaseForm form, String field, String message) {
		MvcTestResult result = submitCase(mvc, form, "phone-1.jpg");

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		JsonNode body = json(result);
		assertThat(body.path("code").asString()).isEqualTo("VALIDATION");
		assertThat(fieldMessage(body, field)).isEqualTo(message);
		assertThat(responsesRequests(wm)).isEmpty();
		assertNoInternals(body(result));
	}

	private static String fieldMessage(JsonNode body, String field) {
		for (JsonNode e : body.path("fieldErrors")) {
			if (field.equals(e.path("field").asString())) {
				return e.path("message").asString();
			}
		}
		throw new AssertionError("no field error for '" + field + "' in " + body);
	}

	// ------------------------------------------------------------ guardrail precedence (AC-24, AC-26)

	@Test
	void returnBought45DaysAgoWithSwollenBatteryReason_isEscalatedForSafety_notRejected() {
		// The stubbed model decision (responses-b) says ACCEPTED_PRELIMINARY: the guardrail must override it.
		loadStubs(wm, DIR, "vision-ok", "decision-ok", "stream-ok");

		MvcTestResult result = submitCase(mvc, returnForm(45, "Bateria spuchła i czuć swąd"), "phone-1.jpg");
		List<SseEvent> events = sse(result);

		assertTacOrder(events);
		JsonNode decision = first(events, "decision").data();
		assertThat(decision.path("label").asString()).isEqualTo("ESCALATED");
		assertThat(decision.path("safety").asBoolean()).isTrue();
		assertThat(decision.path("ruleIds").toString()).contains("R7.1");
		assertThat(decision.path("ruleIds").toString()).doesNotContain("R2.3");
		assertThat(names(events)).endsWith(" done");
		assertNoInternals(body(result));
	}

	@Test
	void returnBought45DaysAgoWithoutSafetyWording_isRejectedWithR2_3() {
		loadStubs(wm, DIR, "vision-ok", "decision-ok", "stream-ok");

		MvcTestResult result = submitCase(mvc, returnForm(45, "Zmiana zdania"), "phone-1.jpg");
		List<SseEvent> events = sse(result);

		JsonNode decision = first(events, "decision").data();
		assertThat(decision.path("label").asString()).isEqualTo("REJECTED");
		assertThat(decision.path("safety").asBoolean()).isFalse();
		assertThat(decision.path("ruleIds").toString()).contains("R2.3");
	}
}
