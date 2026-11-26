package com.hsdc.copilot.llm;

import java.util.function.Consumer;

/**
 * Provider-neutral port to the language model (ADR-001 section 3.1).
 * Implementations throw {@link LlmUnavailableException}, {@link LlmTimeoutException}
 * or {@link LlmCancelledException}.
 */
public interface LlmGateway {

    /** Structured call: the response is parsed into {@code type}. */
    <T> T structured(LlmRequest request, Class<T> type);

    /** Streaming call: only visible text deltas are passed to {@code deltaConsumer}. */
    StreamResult stream(LlmRequest request, Consumer<String> deltaConsumer, CancelToken cancelToken);

    /** Short description for logging (provider and models), never secrets. */
    String describe();
}
