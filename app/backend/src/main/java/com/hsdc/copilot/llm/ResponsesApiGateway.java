package com.hsdc.copilot.llm;

import com.hsdc.copilot.config.OpenRouterProperties;
import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.core.http.StreamResponse;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseUsage;
import com.openai.models.responses.StructuredResponse;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * {@link LlmGateway} over the OpenRouter Responses API with the OpenAI Java SDK (ADR-001 sections 3.1, 3.3, 3.6).
 * No retries (the client is built with {@code maxRetries(0)}); every call writes one {@code ai.call} INFO line
 * whose step and case ID come from MDC (see {@link AiCallContext}).
 */
@Component
public class ResponsesApiGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(ResponsesApiGateway.class);

    private final OpenAIClient client;
    private final OpenRouterProperties properties;

    public ResponsesApiGateway(OpenAIClient client, OpenRouterProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public <T> T structured(LlmRequest request, Class<T> type) {
        long start = System.nanoTime();
        Usage usage = Usage.NONE;
        String status = "ERROR";
        try {
            StructuredResponse<T> response = client.responses().create(
                    ResponsesRequestMapper.structured(request, type), options(request));
            usage = usage(response.usage());
            if (response.status().filter(s -> !s.equals(ResponseStatus.COMPLETED)).isPresent()) {
                throw new LlmUnavailableException("Model response not completed");
            }
            T parsed = response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .findFirst()
                    .orElseThrow(() -> new LlmUnavailableException("Model response has no structured output"));
            status = "OK";
            return parsed;
        } catch (RuntimeException e) {
            RuntimeException mapped = map(e, null);
            status = statusOf(mapped);
            throw mapped;
        } finally {
            logCall(request, status, start, usage);
        }
    }

    @Override
    public StreamResult stream(LlmRequest request, Consumer<String> deltaConsumer, CancelToken cancelToken) {
        long start = System.nanoTime();
        Usage usage = Usage.NONE;
        String status = "ERROR";
        try {
            if (cancelToken.isCancelled()) {
                throw new LlmCancelledException("Cancelled before the call");
            }
            StringBuilder text = new StringBuilder();
            boolean completed = false;
            try (StreamResponse<ResponseStreamEvent> stream = client.responses()
                    .createStreaming(ResponsesRequestMapper.stream(request), options(request))) {
                cancelToken.onCancel(stream::close);
                for (ResponseStreamEvent event : (Iterable<ResponseStreamEvent>) stream.stream()::iterator) {
                    if (cancelToken.isCancelled()) {
                        throw new LlmCancelledException("Cancelled");
                    }
                    Optional<String> delta = event.outputTextDelta().map(d -> d.delta());
                    if (delta.isPresent()) {
                        text.append(delta.get());
                        deltaConsumer.accept(delta.get());
                    } else if (event.completed().isPresent()) {
                        usage = usage(event.completed().get().response().usage());
                        completed = true;
                    } else if (event.failed().isPresent() || event.incomplete().isPresent()
                            || event.error().isPresent()) {
                        throw new LlmUnavailableException("Model stream ended with a failure event");
                    }
                    // all other events, reasoning among them, are ignored
                }
            }
            if (cancelToken.isCancelled()) {
                throw new LlmCancelledException("Cancelled");
            }
            if (!completed) {
                throw new LlmUnavailableException("Model stream ended without response.completed");
            }
            status = "OK";
            return new StreamResult(text.toString(), usage.in(), usage.out(), StreamResult.FinishStatus.COMPLETED);
        } catch (RuntimeException e) {
            RuntimeException mapped = map(e, cancelToken);
            status = statusOf(mapped);
            throw mapped;
        } finally {
            logCall(request, status, start, usage);
        }
    }

    @Override
    public String describe() {
        return "openrouter-responses vision=" + properties.visionModel() + " decision=" + properties.decisionModel();
    }

    // ------------------------------------------------------------------ helpers

    private static RequestOptions options(LlmRequest request) {
        RequestOptions.Builder options = RequestOptions.builder();
        if (request.timeout() != null) {
            options.timeout(request.timeout());
        }
        return options.build();
    }

    private record Usage(long in, long out) {
        static final Usage NONE = new Usage(0, 0);
    }

    private static Usage usage(Optional<ResponseUsage> usage) {
        return usage.map(u -> new Usage(u.inputTokens(), u.outputTokens())).orElse(Usage.NONE);
    }

    private static String statusOf(RuntimeException mapped) {
        if (mapped instanceof LlmTimeoutException) {
            return "TIMEOUT";
        }
        if (mapped instanceof LlmCancelledException) {
            return "CANCELLED";
        }
        return "ERROR";
    }

    /** Error mapping of ADR-001 section 3.6: only the three gateway exceptions leave this class. */
    private RuntimeException map(RuntimeException e, CancelToken cancelToken) {
        if (e instanceof LlmCancelledException || e instanceof LlmTimeoutException
                || e instanceof LlmUnavailableException) {
            return e;
        }
        if (cancelToken != null && cancelToken.isCancelled()) {
            return new LlmCancelledException("Cancelled", e);
        }
        if (e instanceof OpenAIServiceException service) {
            int code = service.statusCode();
            if (code == 401 || code == 402 || code == 403) {
                log.error("AI_CONFIG: the AI provider rejected the configuration with HTTP {}", code);
                return new LlmUnavailableException("AI provider rejected the configuration (HTTP " + code + ")", e);
            }
            return new LlmUnavailableException("AI provider returned HTTP " + code, e);
        }
        if (e instanceof OpenAIException) {
            return isTimeout(e)
                    ? new LlmTimeoutException("AI call timed out", e)
                    : new LlmUnavailableException("AI call failed", e);
        }
        return e;
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException) {
                return true;
            }
            if (t instanceof InterruptedIOException io && "timeout".equalsIgnoreCase(io.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private void logCall(LlmRequest request, String status, long startNanos, Usage usage) {
        String step = Optional.ofNullable(MDC.get(AiCallContext.STEP_KEY)).orElse("UNKNOWN");
        String caseId = Optional.ofNullable(MDC.get(AiCallContext.CASE_ID_KEY)).orElse("-");
        log.info("ai.call step={} caseId={} model={} status={} latencyMs={} inTokens={} outTokens={}",
                step, caseId, request.model(), status, (System.nanoTime() - startNanos) / 1_000_000, usage.in(),
                usage.out());
    }
}
