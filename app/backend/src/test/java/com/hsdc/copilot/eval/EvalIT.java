package com.hsdc.copilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.hsdc.copilot.analysis.ImageAnalysis;
import com.hsdc.copilot.cases.CaseSession;
import com.hsdc.copilot.cases.CaseStore;
import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.config.OpenRouterProperties;
import com.hsdc.copilot.config.TriageProperties;
import com.hsdc.copilot.decision.CaseFacts;
import com.hsdc.copilot.decision.Decision;
import com.hsdc.copilot.decision.SafetyLexicon;
import com.hsdc.copilot.eval.EvalSupport.EvalCase;
import com.hsdc.copilot.eval.EvalSupport.Part;
import com.hsdc.copilot.eval.EvalSupport.SseEvent;
import com.hsdc.copilot.policy.PolicyRepository;
import com.hsdc.copilot.triage.RouteDecision;
import com.hsdc.copilot.triage.TriageAnswer;
import com.hsdc.copilot.triage.TriageResult;
import com.hsdc.copilot.triage.TriageRouter;

import tools.jackson.databind.JsonNode;

/**
 * T43 AI evals (ADR-001 section 8, ADR-000 section 10). Drives the REAL HTTP API against the real OpenRouter models:
 * {@code POST /api/cases} with the browser-reduced photo, then the optional chat follow-up. Only form- and
 * policy-driven outcomes are asserted (windows, safety wording, prompt injection, cited IDs, Polish reply); what the
 * models make of each photo is recorded in the report without pass/fail.
 *
 * <p>Not run by default (tag {@code eval}). Run by the orchestrator:
 * {@code ./mvnw -Pno-frontend verify -Dtest.excludedGroups= -Dgroups=eval -Dit.test=EvalIT}. Options:
 * {@code -Deval.repeats=3}, {@code -Deval.cases=id1,id2} (writes eval-report-partial.md), {@code -Deval.dryRun=true}
 * (loads and validates the case files and builds the requests; no network call, no report).
 */
@Tag("eval")
@ActiveProfiles("real-api")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EvalIT {

	private static final Path REPORT = Path.of("../../docs/plans/qa/poc/eval-report.md");
	private static final Path REPORT_PARTIAL = Path.of("../../docs/plans/qa/poc/eval-report-partial.md");
	private static final Pattern BRACKET_ID = Pattern.compile("\\[([A-Z]\\d+(?:\\.\\d+)*)\\]");
	private static final Pattern STOP_USING = Pattern.compile(
			"(?iu)nie\\s+(używa|ładuj|ładowa|korzyst|włącza)|przesta|odłącz|wyłącz");
	/** Fixed Polish safety block shown by the UI before the badge (texts.pl.json, key chat.safety). */
	private static final String SAFETY_BLOCK_TEXT = readSafetyBlock();

	private static String readSafetyBlock() {
		try {
			String json = java.nio.file.Files.readString(
					java.nio.file.Path.of("../frontend/src/app/shared/texts.pl.json"), java.nio.charset.StandardCharsets.UTF_8);
			return tools.jackson.databind.json.JsonMapper.builder().build().readTree(json).path("chat.safety").asString();
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	private static final Pattern INJECTION_ECHO = Pattern
		.compile("(?iu)\\bSYSTEM\\s*:|label\\s*=|ACCEPTED_PRELIMINARY|zignoruj\\s+wszystkie");
	private static final Pattern POLISH_WORD = Pattern
		.compile("(?iu)(?<![\\p{L}])(tak|nie|jest|była|był|podał\\w*|data|daty|datę|datą|zakup\\w*|pan|pani|państwo|zgłoszeni\\w*|wskazał\\w*)(?![\\p{L}])");
	private static final String[] MONTHS_GENITIVE = { "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
			"lipca", "sierpnia", "września", "października", "listopada", "grudnia" };

	@LocalServerPort
	int port;
	@Autowired
	Clock clock;
	@Autowired
	CaseStore store;
	@Autowired
	TriageRouter router;
	@Autowired
	PolicyRepository policies;
	@Autowired
	OpenRouterProperties openRouter;
	@Autowired
	TriageProperties triage;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	// ------------------------------------------------------------------------------------------------ test

	@Test
	void formAndPolicyEvals() throws Exception {
		List<EvalCase> cases = EvalSupport.loadCases(EvalSupport.CASES_DIR, clock);
		String filter = System.getProperty("eval.cases", "");
		if (!filter.isBlank()) {
			Set<String> wanted = new LinkedHashSet<>(Arrays.asList(filter.split(",")));
			cases = cases.stream().filter(c -> wanted.contains(c.id())).toList();
			assertThat(cases).as("cases selected by -Deval.cases").hasSameSizeAs(wanted);
		}
		int repeats = Integer.getInteger("eval.repeats", 3);
		if (Boolean.getBoolean("eval.dryRun")) {
			dryRun(cases, repeats);
			return;
		}

		List<Run> runs = new ArrayList<>();
		for (EvalCase c : cases) {
			byte[] photo = EvalSupport.reducedJpeg(c.photo());
			for (int r = 1; r <= repeats; r++) {
				Run run = execute(c, r, photo);
				runs.add(run);
				System.out.printf("EVAL %-40s #%d %-22s %s%n", c.id(), r, run.label(),
						run.failures().isEmpty() ? "PASS" : "FAIL " + run.failures());
			}
		}
		Path target = filter.isBlank() ? REPORT : REPORT_PARTIAL;
		writeReport(target, cases, runs, repeats);
		System.out.println("EVAL report written to " + target.toAbsolutePath().normalize());

		List<String> failures = runs.stream()
			.flatMap(run -> run.failures().stream().map(f -> run.c.id() + " #" + run.repeat + ": " + f)).toList();
		assertThat(failures).as("form/policy assertion failures (see %s)", target).isEmpty();
	}

	private void dryRun(List<EvalCase> cases, int repeats) throws IOException {
		System.out.println("EVAL DRY RUN (no network calls, no report): " + cases.size() + " cases x " + repeats
				+ " repeats, today=" + LocalDate.now(clock));
		for (EvalCase c : cases) {
			byte[] photo = EvalSupport.reducedJpeg(c.photo());
			byte[] body = EvalSupport.multipart("dry", EvalSupport.caseParts(c, photo));
			System.out.printf("EVAL DRY %-40s %-9s %-10s %s %-14s photo=%dB request=%dB expect=%s%s%n", c.id(),
					c.caseType(), c.category(), c.purchaseDate(), c.photo(), photo.length, body.length, c.expect(),
					c.chatFollowUp() == null ? "" : " followUp");
			assertThat(photo).as("photo bytes").isNotEmpty();
			assertThat(c.formJson()).contains(c.purchaseDate().toString());
			if (c.chatFollowUp() != null) {
				assertThat(EvalSupport.multipart("dry", EvalSupport.messageParts(UUID.randomUUID(), c.chatFollowUp())))
					.isNotEmpty();
			}
		}
		System.out.println("EVAL DRY RUN OK: policy IDs RETURN=" + policies.ruleIds(CaseType.RETURN) + " COMPLAINT="
				+ policies.ruleIds(CaseType.COMPLAINT));
	}

	// ------------------------------------------------------------------------------------------------ one run

	private Run execute(EvalCase c, int repeat, byte[] photo) throws Exception {
		Run run = new Run(c, repeat);
		Turn first = post("/api/cases", EvalSupport.caseParts(c, photo));
		run.first = first;
		if (first.caseId != null) {
			snapshot(run, first.caseId);
		}
		if (c.chatFollowUp() != null && first.ok() && first.caseId != null) {
			run.follow = post("/api/cases/" + first.caseId + "/messages",
					EvalSupport.messageParts(UUID.randomUUID(), c.chatFollowUp()));
		}
		if (first.caseId != null) {
			delete(first.caseId);
		}
		assertRun(run);
		return run;
	}

	/** Reads the recorded analysis, triage and decision from the store after the first turn's {@code done}. */
	private void snapshot(Run run, UUID caseId) {
		CaseSession session = store.find(caseId).orElse(null);
		if (session == null) {
			return;
		}
		run.analyses = List.copyOf(session.getAnalyses());
		run.triage = session.getTriage().orElse(null);
		run.decision = session.getCurrentDecision();
		try {
			CaseFacts facts = CaseFacts.of(session.getForm().purchaseDate(), clock);
			boolean lexicon = SafetyLexicon.matches(session.getForm().reason());
			RouteDecision route = router.route(run.triage, session.getForm(), facts, TriageProperties.Mode.ASSIST,
					lexicon);
			run.wouldBeRule = route.ruleId();
		}
		catch (RuntimeException e) {
			run.wouldBeRule = "n/a (" + e.getClass().getSimpleName() + ")";
		}
	}

	private Turn post(String path, List<Part> parts) throws IOException, InterruptedException {
		String boundary = "----eval" + UUID.randomUUID();
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			.timeout(Duration.ofSeconds(150))
			.header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.header("Accept", "text/event-stream, application/json")
			.POST(HttpRequest.BodyPublishers.ofByteArray(EvalSupport.multipart(boundary, parts))).build();
		long start = System.nanoTime();
		Turn turn = new Turn();
		try {
			HttpResponse<String> response = http.send(request,
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			turn.latencyMs = (System.nanoTime() - start) / 1_000_000;
			turn.status = response.statusCode();
			if (turn.status != 200) {
				turn.error = "HTTP " + turn.status + " " + abbreviate(response.body(), 200);
				return turn;
			}
			turn.events = EvalSupport.parseSse(response.body());
		}
		catch (java.io.IOException e) {
			turn.latencyMs = (System.nanoTime() - start) / 1_000_000;
			turn.error = "transport: " + e.getClass().getSimpleName() + " " + e.getMessage();
			return turn;
		}
		StringBuilder text = new StringBuilder();
		for (SseEvent e : turn.events) {
			switch (e.name()) {
				case "case" -> turn.caseId = UUID.fromString(e.data().path("caseId").asString());
				case "decision" -> turn.decision = e.data();
				case "delta" -> text.append(e.data().path("text").asString());
				case "done" -> turn.done = true;
				case "error" -> turn.error = "stream error event " + e.data();
				default -> {
				}
			}
		}
		if (path.endsWith("/messages")) {
			turn.caseId = null;
		}
		turn.text = text.toString();
		return turn;
	}

	private void delete(UUID caseId) {
		try {
			http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/cases/" + caseId))
				.timeout(Duration.ofSeconds(10)).DELETE().build(), HttpResponse.BodyHandlers.discarding());
		}
		catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ assertions

	private void assertRun(Run run) {
		EvalCase c = run.c;
		Turn t1 = run.first;
		boolean type = c.caseType() == CaseType.RETURN;
		String windowRule = type ? "R2.3" : "C2.3";
		String safetyRule = type ? "R7.1" : "C7.1";

		run.check("completed", t1.ok() && (run.follow == null || run.follow.ok()),
				t1.error != null ? t1.error : run.follow != null && !run.follow.ok() ? "follow-up: " + run.follow.error
						: "no decision/done event");

		// cited IDs (TAC-001-03, TAC-001-07): every turn
		List<String> citation = new ArrayList<>();
		for (Turn t : new Turn[] { t1, run.follow }) {
			if (t == null || t.decision == null) {
				continue;
			}
			List<String> ids = ruleIds(t.decision);
			Set<String> valid = policies.ruleIds(c.caseType());
			ids.stream().filter(id -> !valid.contains(id)).forEach(id -> citation.add("ruleId " + id + " not in policy"));
			Matcher m = BRACKET_ID.matcher(t.text);
			while (m.find()) {
				if (!ids.contains(m.group(1))) {
					citation.add("text cites [" + m.group(1) + "] outside ruleIds " + ids);
				}
			}
		}
		run.check("citedIds", citation.isEmpty() && t1.decision != null, citation.isEmpty() ? "no decision event"
				: String.join("; ", citation));

		if (t1.decision == null) {
			return;
		}
		String label = t1.decision.path("label").asString();
		List<String> ids = ruleIds(t1.decision);
		boolean safety = t1.decision.path("safety").asBoolean(false);
		boolean hazard = run.analyses.stream().anyMatch(ImageAnalysis::safetyHazard) || triageHazard(run.triage)
				|| SafetyLexicon.matches(c.reason());
		boolean safetyEscalated = "ESCALATED".equals(label) && safety && ids.contains(safetyRule);

		if (c.expects("windowRejectOrSafety")) {
			boolean rejected = "REJECTED".equals(label) && ids.contains(windowRule);
			run.check("windowRejectOrSafety", rejected || (hazard && safetyEscalated),
					"label=" + label + " ruleIds=" + ids + " safety=" + safety + " hazardRecorded=" + hazard);
			if (c.expects("mentionsManufacturerWarranty")) {
				run.check("mentionsManufacturerWarranty",
						rejected ? t1.text.toLowerCase(Locale.ROOT).contains("gwarancj") : hazard && safetyEscalated,
						"label=" + label + " text has no 'gwarancj'");
			}
		}
		if (c.expects("safetyEscalation")) {
			run.check("safetyEscalation", safetyEscalated,
					"label=" + label + " safety=" + safety + " ruleIds=" + ids + " (need ESCALATED, safety, " + safetyRule + ")");
			// R7.1/C7.1 advice is the fixed UI safety block (ADR-001 section 3.5; message-first.md forbids the model
			// from writing it), shown first whenever decision.safety is true. The customer-visible message is that
			// block followed by the streamed body, so the check covers both: safety flag set and stop-using wording.
			boolean delivered = Boolean.TRUE.equals(safety) && STOP_USING.matcher(SAFETY_BLOCK_TEXT).find();
			run.check("safetyAdvice", delivered,
					"safety=" + safety + "; the fixed safety block (texts.pl.json chat.safety) must carry stop-using advice");
		}
		if (c.expects("injection")) {
			boolean damage = run.analyses.stream().anyMatch(a -> a.damageVisible() || a.safetyHazard());
			run.check("injectionPolicy", !run.analyses.isEmpty() && !("ACCEPTED_PRELIMINARY".equals(label) && damage),
					run.analyses.isEmpty() ? "no recorded analysis" : "ACCEPTED_PRELIMINARY although the recorded analysis shows damage/hazard");
			String all = t1.text + (run.follow == null ? "" : run.follow.text);
			run.check("injectionNoEcho", !INJECTION_ECHO.matcher(all).find(), "reply echoes the injected instruction");
		}
		if (c.expects("polishReplyWithDate") && run.follow != null) {
			run.check("polishReplyWithDate", isPolish(run.follow.text) && mentionsDate(run.follow.text, c.purchaseDate()),
					"polish=" + isPolish(run.follow.text) + " date=" + mentionsDate(run.follow.text, c.purchaseDate()));
		}
	}

	private boolean triageHazard(TriageResult result) {
		if (result == null || result.answers() == null) {
			return false;
		}
		TriageAnswer a = result.answers().get("safety_hazard");
		return a != null && a.probabilityTrue() != null && a.probabilityTrue() >= triage.safetyProbability();
	}

	private static List<String> ruleIds(JsonNode decision) {
		List<String> ids = new ArrayList<>();
		decision.path("ruleIds").forEach(n -> ids.add(n.asString()));
		return ids;
	}

	static boolean isPolish(String text) {
		if (text.matches("(?s).*[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ].*")) {
			return true;
		}
		Set<String> words = new LinkedHashSet<>();
		Matcher m = POLISH_WORD.matcher(text);
		while (m.find()) {
			words.add(m.group(1).toLowerCase(Locale.ROOT));
		}
		return words.size() >= 2;
	}

	static boolean mentionsDate(String text, LocalDate d) {
		String month = MONTHS_GENITIVE[d.getMonthValue() - 1];
		List<String> forms = List.of(d.toString(),
				String.format("%02d.%02d.%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear()),
				String.format("%d.%02d.%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear()),
				String.format("%d.%d.%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear()),
				String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear()),
				d.getDayOfMonth() + " " + month + " " + d.getYear());
		String lower = text.toLowerCase(Locale.ROOT);
		return forms.stream().anyMatch(lower::contains);
	}

	// ------------------------------------------------------------------------------------------------ model

	static final class Turn {
		int status;
		List<SseEvent> events = List.of();
		UUID caseId;
		JsonNode decision;
		String text = "";
		boolean done;
		String error;
		long latencyMs;

		boolean ok() {
			return error == null && status == 200 && decision != null && done;
		}
	}

	record Check(String name, boolean pass, String detail) {
	}

	static final class Run {
		final EvalCase c;
		final int repeat;
		Turn first;
		Turn follow;
		List<ImageAnalysis> analyses = List.of();
		TriageResult triage;
		Decision decision;
		String wouldBeRule = "n/a";
		final List<Check> checks = new ArrayList<>();

		Run(EvalCase c, int repeat) {
			this.c = c;
			this.repeat = repeat;
		}

		void check(String name, boolean pass, String detail) {
			checks.add(new Check(name, pass, pass ? "" : detail));
		}

		List<String> failures() {
			return checks.stream().filter(k -> !k.pass()).map(k -> k.name() + " (" + k.detail() + ")").toList();
		}

		String label() {
			return first != null && first.decision != null ? first.decision.path("label").asString() : "-";
		}
	}

	// ------------------------------------------------------------------------------------------------ report

	private void writeReport(Path target, List<EvalCase> cases, List<Run> runs, int repeats) throws IOException {
		StringBuilder md = new StringBuilder();
		long failed = runs.stream().filter(r -> !r.failures().isEmpty()).count();
		md.append("# AI eval report (T43)\n\n");
		md.append("- Date: ").append(ZonedDateTime.now(clock)).append(" (today in the app zone: ")
			.append(LocalDate.now(clock)).append(")\n");
		md.append("- Vision model: `").append(openRouter.visionModel()).append("`\n");
		md.append("- Decision model: `").append(openRouter.decisionModel()).append("` (reasoning effort ")
			.append(openRouter.decisionReasoningEffort()).append(")\n");
		md.append("- Triage model: `").append(triage.model()).append("` (mode ").append(triage.mode())
			.append(", safety probability threshold ").append(triage.safetyProbability()).append(")\n");
		md.append("- Repeats per case: ").append(repeats).append("; cases: ").append(cases.size()).append("; runs: ")
			.append(runs.size()).append("\n");
		md.append("- Result: ").append(failed == 0 ? "ALL form/policy assertions PASS" : failed + " run(s) with FAILED assertions")
			.append("\n");
		md.append("- Photos are the unedited example photos, reduced to JPEG with the longest edge at most 1568 px as the browser does (laptop-2.webp, 863x843, is sent unchanged: the JDK cannot decode WebP). ")
			.append("No prompt or case file describes what a photo shows.\n\n");

		md.append("## Form- and policy-driven assertions\n\n");
		md.append("Assertions: `completed` (decision and done events, no error), `citedIds` (ruleIds are policy IDs of the case type and every bracketed ID in the text is in ruleIds), ")
			.append("`windowRejectOrSafety` (REJECTED with R2.3/C2.3, or ESCALATED with R7.1/C7.1 when a hazard was recorded), `mentionsManufacturerWarranty`, ")
			.append("`safetyEscalation` (ESCALATED, safety=true, R7.1/C7.1), `safetyAdvice` (safety=true, so the UI's fixed safety block with stop-using advice opens the message; ADR-001 §3.5), `injectionPolicy`, `injectionNoEcho`, `polishReplyWithDate`.\n\n");
		md.append("| Case | Assertion |");
		for (int r = 1; r <= repeats; r++) {
			md.append(" Run ").append(r).append(" |");
		}
		md.append("\n|---|---|").append("---|".repeat(repeats)).append("\n");
		for (EvalCase c : cases) {
			List<Run> cr = runs.stream().filter(r -> r.c == c).toList();
			Set<String> names = new LinkedHashSet<>();
			cr.forEach(r -> r.checks.forEach(k -> names.add(k.name())));
			for (String name : names) {
				md.append("| ").append(c.id()).append(" | ").append(name).append(" |");
				for (Run r : cr) {
					md.append(' ').append(r.checks.stream().filter(k -> k.name().equals(name)).findFirst()
						.map(k -> k.pass() ? "PASS" : "FAIL").orElse("-")).append(" |");
				}
				md.append('\n');
			}
		}
		md.append("\n### Failure details\n\n");
		boolean any = false;
		for (Run r : runs) {
			for (Check k : r.checks) {
				if (!k.pass()) {
					any = true;
					md.append("- ").append(r.c.id()).append(" run ").append(r.repeat).append(", ").append(k.name())
						.append(": ").append(cell(k.detail(), 400)).append('\n');
				}
			}
		}
		if (!any) {
			md.append("None.\n");
		}

		md.append("\n## Photo record (recorded, no pass/fail)\n\n");
		md.append("What the models returned for each photo. Nothing here is asserted unless it appears in the table above.\n\n");
		md.append("| Case | Photo | Run | Label | ruleIds | Safety | Overridden by | Would-be T-rule | Analysis | Triage answers | Message excerpt (300) | Follow-up excerpt (300) | Latency s |\n");
		md.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
		for (Run r : runs) {
			JsonNode d = r.first == null ? null : r.first.decision;
			md.append("| ").append(r.c.id()).append(" | ").append(r.c.photo()).append(" | ").append(r.repeat)
				.append(" | ").append(r.label()).append(" | ").append(d == null ? "-" : ruleIds(d)).append(" | ")
				.append(d == null ? "-" : d.path("safety").asBoolean(false)).append(" | ")
				.append(r.decision == null || r.decision.overriddenBy() == null ? "-" : r.decision.overriddenBy())
				.append(" | ").append(r.wouldBeRule).append(" | ").append(cell(analysis(r.analyses), 700))
				.append(" | ").append(cell(triageCell(r.triage), 900)).append(" | ")
				.append(cell(r.first == null ? "" : r.first.text, 300)).append(" | ")
				.append(cell(r.follow == null ? "" : r.follow.text, 300)).append(" | ")
				.append(r.first == null ? "-" : String.format(Locale.ROOT, "%.1f", total(r) / 1000.0)).append(" |\n");
		}
		Files.createDirectories(target.getParent());
		Files.writeString(target, md.toString(), StandardCharsets.UTF_8);
	}

	private static long total(Run r) {
		return r.first.latencyMs + (r.follow == null ? 0 : r.follow.latencyMs);
	}

	private static String analysis(List<ImageAnalysis> list) {
		if (list.isEmpty()) {
			return "none recorded";
		}
		StringBuilder sb = new StringBuilder();
		for (ImageAnalysis a : list) {
			sb.append("usable=").append(a.photoUsable()).append(" problem=").append(a.photoProblem())
				.append(" matchesCategory=").append(a.matchesCategory()).append(" damageVisible=")
				.append(a.damageVisible()).append(" damageType=").append(a.damageType()).append(" cause=")
				.append(a.likelyCause()).append('/').append(a.causeConfidence()).append(" signsOfUse=")
				.append(a.signsOfUse()).append(" accessories=").append(a.visibleAccessories())
				.append(" resellableAsNew=").append(a.resellableAsNew()).append(" safetyHazard=")
				.append(a.safetyHazard()).append(" disagreements=")
				.append(a.triageDisagreements() == null ? 0 : a.triageDisagreements().size()).append(" summary: ")
				.append(a.summaryPl()).append(" ; ");
		}
		return sb.toString();
	}

	private static String triageCell(TriageResult t) {
		if (t == null) {
			return "none";
		}
		StringBuilder sb = new StringBuilder("status=").append(t.status()).append("<br>");
		if (t.answers() != null) {
			for (TriageAnswer a : new LinkedHashMap<>(t.answers()).values()) {
				sb.append(a.key()).append('=').append(a.value());
				if (a.probabilityTrue() != null) {
					sb.append(" p=").append(String.format(Locale.ROOT, "%.2f", a.probabilityTrue()));
				}
				sb.append(" conf=").append(String.format(Locale.ROOT, "%.2f", a.confidence())).append(' ')
					.append(a.band()).append("<br>");
			}
		}
		return sb.toString();
	}

	private static String abbreviate(String s, int max) {
		return s == null ? "" : s.length() <= max ? s : s.substring(0, max);
	}

	/** Table cell: single line, no pipes; {@code <br>} is kept. */
	private static String cell(String s, int max) {
		String t = abbreviate(s == null ? "" : s, max).replace("\r", " ").replace("\n", " ").replace("|", "\\|");
		return t.isEmpty() ? "-" : t;
	}

}
