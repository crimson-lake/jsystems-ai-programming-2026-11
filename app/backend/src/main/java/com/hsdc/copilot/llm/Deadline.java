package com.hsdc.copilot.llm;

import java.time.Duration;

/**
 * Per-turn time budget until the {@code decision} event (ADR-001 section 3.6: 55 s). Each AI call before the decision
 * gets {@code min(perCallTimeout, remaining)}; when nothing is left the turn fails with {@link LlmTimeoutException}.
 */
public final class Deadline {

    private final long expiresAtNanos;

    private Deadline(long expiresAtNanos) {
        this.expiresAtNanos = expiresAtNanos;
    }

    public static Deadline in(Duration budget) {
        return new Deadline(System.nanoTime() + budget.toNanos());
    }

    /** Time left; zero or negative once expired. */
    public Duration remaining() {
        return Duration.ofNanos(expiresAtNanos - System.nanoTime());
    }

    public boolean isExpired() {
        return remaining().compareTo(Duration.ZERO) <= 0;
    }

    /** Throws {@link LlmTimeoutException} when the budget is used up. */
    public void check() {
        if (isExpired()) {
            throw new LlmTimeoutException("Turn deadline exceeded");
        }
    }

    /** The timeout for the next call: the smaller of {@code perCall} and the remaining budget. */
    public Duration callTimeout(Duration perCall) {
        check();
        Duration remaining = remaining();
        return remaining.compareTo(perCall) < 0 ? remaining : perCall;
    }
}
