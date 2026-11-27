package com.hsdc.copilot.web;

import static com.hsdc.copilot.web.ItSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import com.hsdc.copilot.cases.CaseForm;
import com.hsdc.copilot.cases.CaseSession;
import com.hsdc.copilot.cases.CaseStore;
import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.cases.Category;
import com.hsdc.copilot.web.ItSupport.SseEvent;

import tools.jackson.databind.JsonNode;

/**
 * T16: SSE/HTTP contract of POST /api/cases/{id}/messages and DELETE /api/cases/{id}
 * (ADR-002 sections 3.4, 5; ADR-001 section 3.4; AC-31..36, AC-41).
 * Stubs are verbatim S-1 captures (wiremock/chat-turn/**). Red until T27 (and T26, which creates the case).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnableWireMock(@ConfigureWireMock(name = "openrouter", filesUnderClasspath = "wiremock/chat-turn",
		baseUrlProperties = "it.wiremock.url"))
@TestPropertySource(properties = { "openrouter.base-url=${it.wiremock.url}/api/v1",
		"triage.base-url=${it.wiremock.url}/api/alpha/decisions" })
class ChatTurnIT {

	private static final String DIR = "chat-turn";
	private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
	private static final String[] ALL_OK = { "vision-ok", "decision-ok", "stream-ok" };
	private static final String PLAIN_QUESTION = "Jak długo trwa rozpatrzenie zgłoszenia?";
	private static final String SWOLLEN = "Bateria spuchła i telefon się grzeje";

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

	private CaseStore store() {
		return caseStores.getObject();
	}

	private static LocalDate purchaseDate() {
		return LocalDate.now(WARSAW).minusDays(10);
	}

	private static CaseForm form() {
		return new CaseForm(CaseType.RETURN, Category.SMARTPHONE, "Galaxy S23", purchaseDate(), "Zmiana zdania");
	}

	/** First submission through the real endpoint with the all-OK stubs; returns the case ID and first decision. */
	private record Created(UUID caseId, JsonNode decision) {
	}

	private Created createCase() {
		loadStubs(wm, DIR, ALL_OK);
		List<SseEvent> events = sse(submitCase(mvc, form(), "phone-1.jpg"));
		assertTacOrder(events);
		assertThat(names(events)).endsWith(" done");
		return new Created(UUID.fromString(first(events, "case").data().path("caseId").asString()),
				first(events, "decision").data());
	}

	private int messageCount(UUID caseId) {
		return store().find(caseId).orElseThrow().getMessages().size();
	}

	private static void assertJsonError(MvcTestResult result, int status, String code) {
		assertThat(result.getResponse().getStatus()).as(body(result)).isEqualTo(status);
		assertThat(result.getResponse().getContentType()).startsWith("application/json");
		assertThat(json(result).path("code").asString()).isEqualTo(code);
		assertNoInternals(body(result));
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

	@Test
	void delayedStreamStubReallyDelaysTheResponse() throws Exception {
		// Concurrency tests below rely on the stream call still being in flight about a second after the decision.
		loadStubs(wm, DIR, "stream-delayed");
		HttpRequest request = HttpRequest.newBuilder(URI.create(wm.baseUrl() + RESPONSES_PATH))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(read("openrouter-captures/responses-c-streaming-text.request.json")))
			.build();
		CompletableFuture<HttpResponse<String>> pending = HttpClient.newHttpClient().sendAsync(request,
				HttpResponse.BodyHandlers.ofString());
		Thread.sleep(1500);
		assertThat(pending).as("response is still delayed").isNotDone();
		assertThat(pending.get().statusCode()).isEqualTo(200);
	}

	// ------------------------------------------------------------ no revision

	@Test
	void plainQuestion_keepsTheDecision_changedFalse() {
		Created created = createCase();
		int before = messageCount(created.caseId());
		wm.resetRequests();

		MvcTestResult result = sendMessage(mvc, created.caseId(), UUID.randomUUID(), PLAIN_QUESTION, null);
		List<SseEvent> events = sse(result);

		assertTacOrder(events);
		assertThat(names(events)).doesNotContain("case").endsWith(" done");
		assertThat(progressSteps(events)).containsExactly("PREPARING_DECISION", "WRITING_REPLY");
		JsonNode decision = first(events, "decision").data();
		assertThat(decision.path("label").asString()).isEqualTo(created.decision().path("label").asString());
		assertThat(decision.path("changed").asBoolean(true)).isFalse();
		assertThat(isNullOrMissing(decision.get("previousLabel"))).isTrue();
		assertThat(responsesRequests(wm).stream().map(ItSupport::kindOf).toList()).containsExactly(DECISION_KIND,
				STREAM_KIND);
		assertThat(decisionsApiRequestCount(wm)).isZero();
		assertThat(messageCount(created.caseId())).isEqualTo(before + 2);
		assertNoInternals(body(result));
	}

	// ------------------------------------------------------------ revision forced by guardrail G1

	@Test
	void safetyWordingInChat_forcesRevisionToEscalated_withFixedG1Reason() throws IOException {
		Created created = createCase();
		assumeThat(created.decision().path("label").asString()).as("first decision is not already ESCALATED")
			.isNotEqualTo("ESCALATED");
		Properties reasons = new Properties();
		try (Reader in = new InputStreamReader(new ClassPathResource("messages/guardrail-reasons.pl.properties")
			.getInputStream(), StandardCharsets.UTF_8)) {
			reasons.load(in);
		}
		wm.resetRequests();

		MvcTestResult result = sendMessage(mvc, created.caseId(), UUID.randomUUID(), SWOLLEN, null);
		List<SseEvent> events = sse(result);

		assertTacOrder(events);
		JsonNode decision = first(events, "decision").data();
		assertThat(decision.path("label").asString()).isEqualTo("ESCALATED");
		assertThat(decision.path("safety").asBoolean()).isTrue();
		assertThat(decision.path("changed").asBoolean()).isTrue();
		assertThat(decision.path("previousLabel").asString()).isEqualTo(created.decision().path("label").asString());
		assertThat(decision.path("changeReasonPl").asString()).isEqualTo(reasons.getProperty("G1"));
		assertThat(decision.path("ruleIds").toString()).contains("R7.1");
		assertThat(store().find(created.caseId()).orElseThrow().getCurrentDecision().label().name())
			.isEqualTo("ESCALATED");
		assertNoInternals(body(result));
	}

	// ------------------------------------------------------------ photo limit and replay

	@Test
	void fourthPhoto_isRejectedWith422PhotoLimit_afterThreeCommittedPhotoTurns() {
		Created created = createCase();
		String[] photos = { "phone-2.jpeg", "phone-3.jpeg", "laptop-2.webp" };
		for (String photo : photos) {
			List<SseEvent> events = sse(sendMessage(mvc, created.caseId(), UUID.randomUUID(), "Dodaję zdjęcie", photo));
			assertTacOrder(events);
			assertThat(progressSteps(events)).startsWith("ANALYZING_PHOTO", "PREPARING_DECISION");
			assertThat(names(events)).endsWith(" done");
		}
		assertThat(store().find(created.caseId()).orElseThrow().getAdditionalPhotoCount()).isEqualTo(3);
		int calls = responsesRequests(wm).size();

		MvcTestResult fourth = sendMessage(mvc, created.caseId(), UUID.randomUUID(), "Kolejne zdjęcie", "phone-2.jpeg");

		assertJsonError(fourth, 422, "PHOTO_LIMIT");
		assertThat(responsesRequests(wm)).as("no AI call for a rejected turn").hasSize(calls);
		assertThat(store().find(created.caseId()).orElseThrow().getAdditionalPhotoCount()).isEqualTo(3);
	}

	@Test
	void retriedThirdPhotoTurn_withCommittedClientMessageId_replaysInsteadOf422() {
		Created created = createCase();
		sse(sendMessage(mvc, created.caseId(), UUID.randomUUID(), "Zdjęcie 1", "phone-2.jpeg"));
		sse(sendMessage(mvc, created.caseId(), UUID.randomUUID(), "Zdjęcie 2", "phone-3.jpeg"));
		UUID third = UUID.randomUUID();
		List<SseEvent> original = sse(sendMessage(mvc, created.caseId(), third, "Zdjęcie 3", "laptop-2.webp"));
		assertThat(names(original)).endsWith(" done");
		int calls = responsesRequests(wm).size();
		int messages = messageCount(created.caseId());

		MvcTestResult retry = sendMessage(mvc, created.caseId(), third, "Zdjęcie 3", "laptop-2.webp");
		List<SseEvent> replay = sse(retry);

		assertThat(names(replay)).as("replay = decision, one delta, done (TAC-002-04)").isEqualTo("decision delta done");
		assertThat(responsesRequests(wm)).as("replay makes no AI call").hasSize(calls);
		assertThat(first(replay, "decision").data().path("label").asString())
			.isEqualTo(first(original, "decision").data().path("label").asString());
		assertThat(first(replay, "done").data().path("messageId").asString())
			.isEqualTo(first(original, "done").data().path("messageId").asString());
		assertThat(joinedDeltas(replay)).isEqualTo(joinedDeltas(original));
		assertThat(messageCount(created.caseId())).as("replay commits nothing").isEqualTo(messages);
		assertNoInternals(body(retry));
	}

	// ------------------------------------------------------------ delete

	@Test
	void delete_returns204_andLaterMessagesGet404CaseNotFound() {
		Created created = createCase();

		assertThat(mvc.delete().uri("/api/cases/{id}", created.caseId()).exchange().getResponse().getStatus())
			.isEqualTo(204);
		assertThat(store().find(created.caseId())).isEmpty();

		MvcTestResult after = sendMessage(mvc, created.caseId(), UUID.randomUUID(), PLAIN_QUESTION, null);
		assertJsonError(after, 404, "CASE_NOT_FOUND");
		assertThat(mvc.delete().uri("/api/cases/{id}", UUID.randomUUID()).exchange().getResponse().getStatus())
			.as("DELETE always returns 204").isEqualTo(204);
	}

	@Test
	void deleteDuringARunningTurn_keepsTheCaseDeleted() throws Exception {
		Created created = createCase();
		loadStubs(wm, DIR, "stream-delayed");
		wm.resetRequests();

		MvcTestResult running = sendMessageAsync(mvc, created.caseId(), UUID.randomUUID(), PLAIN_QUESTION, null);
		awaitTurnInStreamStep(wm, Duration.ofSeconds(20));
		assertThat(mvc.delete().uri("/api/cases/{id}", created.caseId()).exchange().getResponse().getStatus())
			.isEqualTo(204);
		awaitCompletion(running);

		assertThat(store().find(created.caseId())).as("the finishing turn must not resurrect the case").isEmpty();
		assertJsonError(sendMessage(mvc, created.caseId(), UUID.randomUUID(), PLAIN_QUESTION, null), 404,
				"CASE_NOT_FOUND");
	}

	// ------------------------------------------------------------ concurrency

	@Test
	void concurrentTurn_getsBusy409_andSameClientMessageIdWhileRunningIsBusyToo() throws Exception {
		Created created = createCase();
		loadStubs(wm, DIR, "stream-delayed");
		wm.resetRequests();
		UUID running = UUID.randomUUID();

		MvcTestResult first = sendMessageAsync(mvc, created.caseId(), running, PLAIN_QUESTION, null);
		awaitTurnInStreamStep(wm, Duration.ofSeconds(20));

		assertJsonError(sendMessage(mvc, created.caseId(), UUID.randomUUID(), "Druga wiadomość", null), 409, "CASE_BUSY");
		assertJsonError(sendMessage(mvc, created.caseId(), running, PLAIN_QUESTION, null), 409, "CASE_BUSY");

		awaitCompletion(first);
		List<SseEvent> events = parseSse(body(first));
		assertTacOrder(events);
		assertThat(names(events)).as("the running turn is unaffected").endsWith(" done");
		// the lock is released: the next turn is accepted
		loadStubs(wm, DIR, "stream-ok");
		assertThat(names(sse(sendMessage(mvc, created.caseId(), UUID.randomUUID(), "Trzecia wiadomość", null))))
			.endsWith(" done");
	}

	// ------------------------------------------------------------ failure: nothing committed

	@Test
	void failedTurn_commitsNothing_andItsTextIsNotReplayedInTheNextRequest() {
		Created created = createCase();
		CaseSession before = store().find(created.caseId()).orElseThrow();
		int messages = before.getMessages().size();
		int analyses = before.getAnalyses().size();
		int photos = before.getAdditionalPhotoCount();
		String failedText = "Czy mogę prosić o fakturę numer ZX-4417?";
		UUID failedId = UUID.randomUUID();
		loadStubs(wm, DIR, "decision-401");

		MvcTestResult failed = sendMessage(mvc, created.caseId(), failedId, failedText, "phone-2.jpeg");
		List<SseEvent> events = sse(failed);

		assertTacOrder(events);
		SseEvent last = events.get(events.size() - 1);
		assertThat(last.name()).isEqualTo("error");
		assertThat(last.data().path("code").asString()).isEqualTo("AI_UNAVAILABLE");
		assertThat(names(events)).doesNotContain("decision", "done");
		CaseSession after = store().find(created.caseId()).orElseThrow();
		assertThat(after.getMessages()).hasSize(messages);
		assertThat(after.getAnalyses()).hasSize(analyses);
		assertThat(after.getAdditionalPhotoCount()).isEqualTo(photos);
		assertThat(after.getCommittedClientMessageIds()).doesNotContain(failedId);
		assertNoInternals(body(failed));

		// The next turn's history must not contain the failed message (AC-41: a clean re-run).
		loadStubs(wm, DIR, "decision-ok");
		wm.resetRequests();
		List<SseEvent> next = sse(sendMessage(mvc, created.caseId(), UUID.randomUUID(), "Dziękuję", null));
		assertThat(names(next)).endsWith(" done");
		assertThat(responsesRequests(wm)).isNotEmpty();
		responsesRequests(wm).forEach(r -> assertThat(r.getBodyAsString()).doesNotContain("ZX-4417"));

		// Retrying the failed clientMessageId is processed normally, not replayed (ADR-002 section 8).
		wm.resetRequests();
		List<SseEvent> retry = sse(sendMessage(mvc, created.caseId(), failedId, failedText, "phone-2.jpeg"));
		assertThat(names(retry)).startsWith("progress").endsWith(" done");
		assertThat(responsesRequests(wm)).isNotEmpty();
	}

	// ------------------------------------------------------------ full context (AC-34)

	@Test
	void followUpAboutPurchaseDate_sendsTheFormPurchaseDateToTheModel() {
		Created created = createCase();
		wm.resetRequests();
		String question = "Jaką datę zakupu podałem?";

		List<SseEvent> events = sse(sendMessage(mvc, created.caseId(), UUID.randomUUID(), question, null));

		assertThat(names(events)).endsWith(" done");
		List<LoggedRequest> decisionRequests = responsesRequests(wm, DECISION_KIND);
		List<LoggedRequest> streamRequests = responsesRequests(wm, STREAM_KIND);
		assertThat(decisionRequests).hasSize(1);
		assertThat(streamRequests).hasSize(1);
		for (LoggedRequest request : List.of(decisionRequests.get(0), streamRequests.get(0))) {
			assertThat(request.getBodyAsString()).contains(purchaseDate().toString()).contains(question);
		}
	}

	// ------------------------------------------------------------ helpers

	private static void awaitCompletion(MvcTestResult result) throws Exception {
		result.getMvcResult().getAsyncResult(TURN_TIMEOUT.toMillis());
	}
}
