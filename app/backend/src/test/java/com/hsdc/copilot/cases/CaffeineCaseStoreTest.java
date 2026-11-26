package com.hsdc.copilot.cases;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** T24: TTL (expireAfterAccess) and size limit of the in-memory case store (ADR-002 section 3.4). */
class CaffeineCaseStoreTest {

    private static final Duration TTL = Duration.ofMinutes(60);

    /** Fake time source; Caffeine reads it for expiry. */
    private static final class FakeTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration d) {
            nanos.addAndGet(d.toNanos());
        }
    }

    private static CaseSession session() {
        return new CaseSession(UUID.randomUUID(),
                new CaseForm(CaseType.RETURN, Category.LAPTOP, "ThinkPad T14", LocalDate.of(2026, 11, 16), null),
                Instant.parse("2026-11-26T10:00:00Z"));
    }

    private static CaffeineCaseStore store(FakeTicker ticker, int maximumSize) {
        return new CaffeineCaseStore(TTL, maximumSize, ticker, Runnable::run);
    }

    @Test
    void savedSessionIsFoundAndRemovedSessionIsGone() {
        CaffeineCaseStore store = store(new FakeTicker(), 500);
        CaseSession session = session();

        store.save(session);

        assertThat(store.find(session.getCaseId())).containsSame(session);
        store.remove(session.getCaseId());
        assertThat(store.find(session.getCaseId())).isEmpty();
    }

    @Test
    void unknownIdIsEmpty() {
        assertThat(store(new FakeTicker(), 500).find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void sessionIsEvictedAfterSixtyMinutesIdle() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCaseStore store = store(ticker, 500);
        CaseSession session = session();
        store.save(session);

        ticker.advance(TTL.minusSeconds(1));
        assertThat(store.find(session.getCaseId())).as("still alive just before the TTL").isPresent();

        ticker.advance(TTL.plusSeconds(1));
        assertThat(store.find(session.getCaseId())).as("idle for more than the TTL").isEmpty();
    }

    @Test
    void accessRestartsTheIdleClock() {
        FakeTicker ticker = new FakeTicker();
        CaffeineCaseStore store = store(ticker, 500);
        CaseSession session = session();
        store.save(session);

        for (int i = 0; i < 5; i++) {
            ticker.advance(Duration.ofMinutes(40));
            assertThat(store.find(session.getCaseId())).as("access %d keeps it alive", i).isPresent();
        }
    }

    @Test
    void oneSessionIsEvictedWhenTheFiveHundredAndFirstIsStored() {
        CaffeineCaseStore store = store(new FakeTicker(), 500);
        List<CaseSession> sessions = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            CaseSession s = session();
            sessions.add(s);
            store.save(s);
        }
        store.cleanUp();

        long present = sessions.stream().filter(s -> store.find(s.getCaseId()).isPresent()).count();

        assertThat(present).isEqualTo(500);
    }

    @Test
    void sizeNeverExceedsTheLimitUnderLoad() {
        CaffeineCaseStore store = store(new FakeTicker(), 500);
        for (int i = 0; i < 1500; i++) {
            store.save(session());
        }
        store.cleanUp();

        assertThat(store.estimatedSize()).isLessThanOrEqualTo(500);
    }
}
