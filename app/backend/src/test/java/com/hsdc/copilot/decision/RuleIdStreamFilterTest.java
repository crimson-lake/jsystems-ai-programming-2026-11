package com.hsdc.copilot.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** AC-23: no bracketed rule ID outside decision.ruleIds reaches the customer (ADR-001 section 3.1). */
class RuleIdStreamFilterTest {

	private static final UUID CASE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
	private final Logger logger = (Logger) LoggerFactory.getLogger(RuleIdStreamFilter.class);

	@BeforeEach
	void attach() {
		logs.start();
		logger.addAppender(logs);
	}

	@AfterEach
	void detach() {
		logger.detachAppender(logs);
	}

	private static RuleIdStreamFilter filter(String... allowed) {
		return new RuleIdStreamFilter(CASE_ID, List.of(allowed));
	}

	private static String run(RuleIdStreamFilter filter, String... deltas) {
		StringBuilder out = new StringBuilder();
		for (String delta : deltas) {
			out.append(filter.accept(delta));
		}
		out.append(filter.flush());
		return out.toString();
	}

	@Test
	void allowedIdPassesAndUnknownIdIsRemovedWhenSplitAcrossThreeDeltas() {
		RuleIdStreamFilter f = filter("R2.3");
		String out = run(f, "…zgodnie z [R", "9.9] i [R2.", "3].");
		assertThat(out).isEqualTo("…zgodnie z  i [R2.3].");
		assertThat(f.filteredText()).isEqualTo(out);
		assertThat(out).doesNotContain("R9.9");
		assertThat(f.removedCount()).isEqualTo(1);
	}

	@Test
	void removalIsLoggedAtWarnWithCaseId() {
		run(filter("R2.3"), "tak [R9.9] nie");
		assertThat(logs.list).hasSize(1);
		ILoggingEvent event = logs.list.get(0);
		assertThat(event.getLevel()).isEqualTo(Level.WARN);
		assertThat(event.getFormattedMessage()).contains(CASE_ID.toString()).contains("R9.9");
	}

	@Test
	void textEndingWithPartialBracketIsFlushedAsIs() {
		RuleIdStreamFilter f = filter("R2.3");
		assertThat(f.accept("koniec [R")).isEqualTo("koniec ");
		assertThat(f.flush()).isEqualTo("[R");
		assertThat(f.filteredText()).isEqualTo("koniec [R");
		assertThat(f.removedCount()).isZero();
	}

	@Test
	void partialTokenIsHeldBackUntilItCompletes() {
		RuleIdStreamFilter f = filter("R2.3");
		assertThat(f.accept("a [")).isEqualTo("a ");
		assertThat(f.accept("R2")).isEmpty();
		assertThat(f.accept(".3")).isEmpty();
		assertThat(f.accept("] b")).isEqualTo("[R2.3] b");
	}

	@Test
	void modelNamesAndNonIdBracketsAreUntouched() {
		RuleIdStreamFilter f = filter("R2.3");
		String text = "Telefon LG C3 i aparat Canon R5, [uwaga] oraz [R2.3a] i [X1] i [R] i [R123] i [C1.234].";
		assertThat(run(f, text)).isEqualTo(text);
		assertThat(f.removedCount()).isZero();
		assertThat(logs.list).isEmpty();
	}

	@Test
	void bracketRestartsTokenAfterFailedPrefix() {
		RuleIdStreamFilter f = filter("R2.3");
		assertThat(run(f, "[[R2.3]", "[[R9.9]")).isEqualTo("[[R2.3][");
	}

	@Test
	void complaintIdsAndSectionIdsWork() {
		RuleIdStreamFilter f = filter("C8", "C2.3");
		assertThat(run(f, "[C8] [C2.3] [C7.1] [C1]")).isEqualTo("[C8] [C2.3]  ");
		assertThat(f.removedCount()).isEqualTo(2);
	}

	@Test
	void filterWorksCharacterByCharacter() {
		RuleIdStreamFilter f = filter("R2.3");
		String text = "Zgodnie z [R2.3] oraz [R9.9] koniec";
		assertThat(run(f, text.split(""))).isEqualTo("Zgodnie z [R2.3] oraz  koniec");
	}

	@Test
	void nothingAllowedRemovesEveryBracketedId() {
		assertThat(run(filter(), "[R2.3] tekst")).isEqualTo(" tekst");
	}

	@Test
	void unbracketedIdLikeTextIsKeptButCounted() {
		RuleIdStreamFilter f = filter("R2.3");
		String out = run(f, "Zgodnie z R2.3 oraz C8, ale [R2.3] jest ok. LG C3.");
		assertThat(out).isEqualTo("Zgodnie z R2.3 oraz C8, ale [R2.3] jest ok. LG C3.");
		assertThat(f.unbracketedIdLikeCount()).isEqualTo(3);
	}

	@Test
	void emptyDeltaIsHarmless() {
		RuleIdStreamFilter f = filter("R2.3");
		assertThat(f.accept("")).isEmpty();
		assertThat(f.flush()).isEmpty();
		assertThat(f.filteredText()).isEmpty();
	}
}
