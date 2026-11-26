package com.hsdc.copilot.llm;

import java.util.function.Supplier;
import org.slf4j.MDC;

/**
 * Passes the log context of an AI call to the gateway without changing the frozen {@link LlmRequest}.
 *
 * <p>Two SLF4J MDC keys are read by {@link ResponsesApiGateway} for its {@code ai.call} log line:
 * <ul>
 *   <li>{@value #CASE_ID_KEY}: the case ID, set by the caller for the whole turn (ADR-002 section 3.7);</li>
 *   <li>{@value #STEP_KEY}: the {@link AiStep} name, set around one call with {@link #withStep}.</li>
 * </ul>
 * MDC is thread-local; the gateway calls are synchronous on the caller's (virtual) thread, so the values
 * are visible there. A missing step is logged as {@code UNKNOWN}, a missing case ID as {@code -}.
 */
public final class AiCallContext {

    public static final String CASE_ID_KEY = "caseId";
    public static final String STEP_KEY = "aiStep";

    private AiCallContext() {
    }

    /** Runs {@code call} with the step in MDC and restores the previous value afterwards. */
    public static <T> T withStep(AiStep step, Supplier<T> call) {
        String previous = MDC.get(STEP_KEY);
        MDC.put(STEP_KEY, step.name());
        try {
            return call.get();
        } finally {
            if (previous == null) {
                MDC.remove(STEP_KEY);
            } else {
                MDC.put(STEP_KEY, previous);
            }
        }
    }
}
