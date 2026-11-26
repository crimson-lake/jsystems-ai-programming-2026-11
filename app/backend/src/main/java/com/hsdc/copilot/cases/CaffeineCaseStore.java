package com.hsdc.copilot.cases;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * In-memory case store (ADR-002 section 3.4): a Caffeine cache that expires a case after {@code app.case-ttl}
 * without access and holds at most {@value #MAXIMUM_SIZE} cases. Lookups count as access.
 */
public class CaffeineCaseStore implements CaseStore {

    static final int MAXIMUM_SIZE = 500;

    private final Duration ttl;
    private final Cache<UUID, CaseSession> cache;

    public CaffeineCaseStore(Duration ttl) {
        this(ttl, MAXIMUM_SIZE, Ticker.systemTicker(), null);
    }

    /** For tests: a fake ticker and (optionally) a same-thread executor make expiry and eviction deterministic. */
    CaffeineCaseStore(Duration ttl, int maximumSize, Ticker ticker, Executor executor) {
        this.ttl = ttl;
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .expireAfterAccess(ttl)
                .maximumSize(maximumSize)
                .ticker(ticker);
        if (executor != null) {
            builder.executor(executor);
        }
        this.cache = builder.build();
    }

    @Override
    public Optional<CaseSession> find(UUID caseId) {
        return Optional.ofNullable(cache.getIfPresent(caseId));
    }

    @Override
    public void save(CaseSession session) {
        cache.put(session.getCaseId(), session);
    }

    @Override
    public void remove(UUID caseId) {
        cache.invalidate(caseId);
    }

    public Duration ttl() {
        return ttl;
    }

    void cleanUp() {
        cache.cleanUp();
    }

    long estimatedSize() {
        return cache.estimatedSize();
    }
}
