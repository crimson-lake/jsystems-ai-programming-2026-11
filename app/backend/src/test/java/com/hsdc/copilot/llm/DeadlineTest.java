package com.hsdc.copilot.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class DeadlineTest {

    @Test
    void callTimeoutIsThePerCallLimitWhileEnoughBudgetRemains() {
        Deadline deadline = Deadline.in(Duration.ofSeconds(55));
        assertThat(deadline.callTimeout(Duration.ofSeconds(20))).isEqualTo(Duration.ofSeconds(20));
        assertThat(deadline.isExpired()).isFalse();
    }

    @Test
    void callTimeoutShrinksToTheRemainingBudget() {
        Deadline deadline = Deadline.in(Duration.ofSeconds(5));
        assertThat(deadline.callTimeout(Duration.ofSeconds(20))).isLessThanOrEqualTo(Duration.ofSeconds(5))
            .isPositive();
    }

    @Test
    void anExpiredDeadlineThrowsTimeout() {
        Deadline deadline = Deadline.in(Duration.ZERO);
        assertThat(deadline.isExpired()).isTrue();
        assertThatThrownBy(deadline::check).isInstanceOf(LlmTimeoutException.class);
        assertThatThrownBy(() -> deadline.callTimeout(Duration.ofSeconds(20))).isInstanceOf(LlmTimeoutException.class);
    }
}
