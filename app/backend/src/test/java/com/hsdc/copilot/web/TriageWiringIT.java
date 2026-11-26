package com.hsdc.copilot.web;

import static com.hsdc.copilot.web.ItSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.hsdc.copilot.cases.CaseForm;
import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.cases.Category;
import com.hsdc.copilot.triage.TriageAgreementLogger;
import com.hsdc.copilot.triage.TriageService;
import com.hsdc.copilot.web.ItSupport.SseEvent;

/**
 * T29: triage wiring in {@code assist} mode (ADR-004 sections 3.1, 3.4, 3.5; TAC-004-02). The Decisions API is
 * served by the recorded Jev stubs (wiremock/case-submission/stubs/triage-*.json), OpenRouter by the S-1 stubs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnableWireMock(@ConfigureWireMock(name = "openrouter", filesUnderClasspath = "wiremock/case-submission",
		baseUrlProperties = "it.wiremock.url"))
@TestPropertySource(properties = { "openrouter.base-url=${it.wiremock.url}/api/v1",
		"triage.base-url=${it.wiremock.url}/api/alpha/decisions", "triage.mode=assist" })
class TriageWiringIT {

	/** The CASE FACTS key as it appears JSON-escaped in the request body (the prompts also mention the word). */
	private static final String SIGNALS = "\\\"triageSignals\\\":[";

	private static final String DIR = "case-submission";
	private static final String[] LLM_OK = { "vision-ok", "decision-ok", "stream-ok" };
	private static final Duration DEADLINE = Duration.ofSeconds(55);

	@Autowired
	private MockMvcTester mvc;

	@InjectWireMock("openrouter")
	private WireMockServer wm;

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	@BeforeEach
	void setUp() {
		wm.resetAll();
		logs.start();
		logger(TriageAgreementLogger.class).addAppender(logs);
		logger(TriageService.class).addAppender(logs);
	}

	@AfterEach
	void tearDown() {
		logger(TriageAgreementLogger.class).detachAppender(logs);
		logger(TriageService.class).detachAppender(logs);
	}

	private static Logger logger(Class<?> type) {
		return (Logger) LoggerFactory.getLogger(type);
	}

	private List<String> lines(String prefix) {
		return logs.list.stream().map(ILoggingEvent::getFormattedMessage).filter(m -> m.startsWith(prefix)).toList();
	}

	private static CaseForm returnForm() {
		return new CaseForm(CaseType.RETURN, Category.SMARTPHONE, "Galaxy S23",
				LocalDate.now(ZoneId.of("Europe/Warsaw")).minusDays(10), "Zmiana zdania");
	}

	@Test
	void assist_firstSubmission_oneDecisionsCall_signalsInVisionAndDecision_oneAgreementLine() {
		loadStubs(wm, DIR, LLM_OK);
		loadStubs(wm, DIR, "triage-return-ok");

		List<SseEvent> events = sse(submitCase(mvc, returnForm(), "phone-1.jpg"));

		assertTacOrder(events);
		assertThat(names(events)).endsWith(" done");
		// The triage call is covered by ANALYZING_PHOTO (ADR-001 section 3.6): no new progress step.
		assertThat(progressSteps(events)).containsExactly("UPLOAD_RECEIVED", "ANALYZING_PHOTO", "PREPARING_DECISION",
				"WRITING_REPLY");
		assertThat(decisionsApiRequestCount(wm)).isEqualTo(1);
		String label = first(events, "decision").data().path("label").asString();

		// ADR-004 section 3.4 / ADR-001 section 3.3: CASE FACTS of the vision and decision steps carry the signals.
		String vision = responsesRequests(wm, VISION_KIND).get(0).getBodyAsString();
		String decision = responsesRequests(wm, DECISION_KIND).get(0).getBodyAsString();
		for (String body : List.of(vision, decision)) {
			assertThat(body).contains(SIGNALS).contains("safety_hazard").contains("\\\"band\\\":\\\"");
		}

		List<String> agreement = lines("triage.agreement");
		assertThat(agreement).hasSize(1);
		assertThat(agreement.get(0)).contains("status=OK").contains("llmLabel=" + label).contains("ruleId=T")
			.doesNotContain("Zmiana zdania");
		assertThat(lines("ai.call step=TRIAGE")).singleElement().asString().contains("status=OK");
	}

	@Test
	void chatPhotoIsNotTriaged() {
		loadStubs(wm, DIR, LLM_OK);
		loadStubs(wm, DIR, "triage-return-ok");
		List<SseEvent> created = sse(submitCase(mvc, returnForm(), "phone-1.jpg"));
		UUID caseId = UUID.fromString(first(created, "case").data().path("caseId").asString());
		wm.resetRequests();

		List<SseEvent> events = sse(sendMessage(mvc, caseId, UUID.randomUUID(), null, "phone-3.jpeg"));

		assertTacOrder(events);
		assertThat(names(events)).endsWith(" done");
		assertThat(decisionsApiRequestCount(wm)).isZero();
		assertThat(responsesRequests(wm).stream().map(ItSupport::kindOf).toList()).containsExactly(VISION_KIND,
				DECISION_KIND, STREAM_KIND);
		// T27 gap: a photo-only turn has a trusted marker item in the decision and the message step.
		String marker = "photo attached, no text";
		assertThat(responsesRequests(wm, DECISION_KIND).get(0).getBodyAsString()).contains(marker);
		assertThat(responsesRequests(wm, STREAM_KIND).get(0).getBodyAsString()).contains(marker);
	}

	static Stream<Arguments> triageFailures() {
		return Stream.of(Arguments.of("Decisions 401 (decisions-jev-error-invalid-key)", "triage-401", "UNAVAILABLE"),
				Arguments.of("Decisions delayed past the 8 s timeout", "triage-delayed", "TIMEOUT"),
				Arguments.of("Decisions connection reset", "triage-reset", "UNAVAILABLE"));
	}

	/** TAC-004-02: a failed triage gives the normal LLM decision, no error event, within the deadline. */
	@ParameterizedTest(name = "{0}")
	@MethodSource("triageFailures")
	void triageFailure_normalDecision_noErrorEvent_deadlineMet(String scenario, String stub, String status) {
		loadStubs(wm, DIR, LLM_OK);
		loadStubs(wm, DIR, stub);

		long start = System.nanoTime();
		List<SseEvent> events = sse(submitCase(mvc, returnForm(), "phone-1.jpg"));
		Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

		assertTacOrder(events);
		assertThat(names(events)).doesNotContain("error").endsWith(" done");
		assertThat(first(events, "decision").data().path("label").asString()).isNotBlank();
		assertThat(elapsed).as("decision within the 55 s budget").isLessThan(DEADLINE);
		assertThat(responsesRequests(wm).stream().map(ItSupport::kindOf).toList()).containsExactly(VISION_KIND,
				DECISION_KIND, STREAM_KIND);
		assertThat(responsesRequests(wm, VISION_KIND).get(0).getBodyAsString()).doesNotContain(SIGNALS);
		assertThat(lines("ai.call step=TRIAGE")).singleElement().asString()
			.contains("status=" + (status.equals("TIMEOUT") ? "TIMEOUT" : "ERROR"));
		assertThat(lines("triage.agreement")).singleElement().asString().contains("ruleId=T0")
			.contains("status=" + status);
	}
}
