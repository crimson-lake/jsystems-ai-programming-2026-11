package com.hsdc.copilot.web;

import static com.hsdc.copilot.web.ItSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
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

import com.hsdc.copilot.cases.CaseForm;
import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.cases.Category;
import com.hsdc.copilot.web.ItSupport.SseEvent;

/** T29, TAC-004-01: with {@code triage.mode=off} the first submission makes no Decisions API call. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnableWireMock(@ConfigureWireMock(name = "openrouter", filesUnderClasspath = "wiremock/case-submission",
		baseUrlProperties = "it.wiremock.url"))
@TestPropertySource(properties = { "openrouter.base-url=${it.wiremock.url}/api/v1",
		"triage.base-url=${it.wiremock.url}/api/alpha/decisions", "triage.mode=off" })
class TriageOffIT {

	/** The CASE FACTS key as it appears JSON-escaped in the request body (the prompts also mention the word). */
	private static final String SIGNALS = "\\\"triageSignals\\\":[";

	private static final String DIR = "case-submission";

	@Autowired
	private MockMvcTester mvc;

	@InjectWireMock("openrouter")
	private WireMockServer wm;

	@Test
	void off_firstSubmission_makesZeroDecisionsCalls_andSendsNoSignals() {
		wm.resetAll();
		loadStubs(wm, DIR, "vision-ok", "decision-ok", "stream-ok", "triage-return-ok");
		CaseForm form = new CaseForm(CaseType.RETURN, Category.SMARTPHONE, "Galaxy S23",
				LocalDate.now(ZoneId.of("Europe/Warsaw")).minusDays(10), "Zmiana zdania");

		List<SseEvent> events = sse(submitCase(mvc, form, "phone-1.jpg"));

		assertTacOrder(events);
		assertThat(names(events)).endsWith(" done");
		assertThat(decisionsApiRequestCount(wm)).isZero();
		responsesRequests(wm).forEach(r -> assertThat(r.getBodyAsString()).doesNotContain(SIGNALS));
	}
}
