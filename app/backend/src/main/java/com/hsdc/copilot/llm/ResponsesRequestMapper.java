package com.hsdc.copilot.llm;

import com.hsdc.copilot.llm.LlmRequest.InputItem;
import com.openai.core.JsonValue;
import com.openai.models.Reasoning;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputText;
import com.openai.models.responses.StructuredResponseCreateParams;
import java.util.List;
import java.util.Map;

/**
 * Maps the provider-neutral {@link LlmRequest} to Responses API parameters (ADR-001 sections 3.3, 3.4).
 * Requests are stateless: {@code store} and {@code previous_response_id} are never set.
 */
final class ResponsesRequestMapper {

    private ResponsesRequestMapper() {
    }

    /** Structured call: strict JSON schema of {@code type}, and {@code provider.require_parameters=true}. */
    static <T> StructuredResponseCreateParams<T> structured(LlmRequest request, Class<T> type) {
        return base(request)
                .putAdditionalBodyProperty("provider", JsonValue.from(Map.of("require_parameters", true)))
                .text(type)
                .build();
    }

    /** Streaming text call (no schema); the caller streams it with {@code createStreaming}. */
    static ResponseCreateParams stream(LlmRequest request) {
        return base(request).build();
    }

    private static ResponseCreateParams.Builder base(LlmRequest request) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(request.model())
                .instructions(request.instructions())
                .input(ResponseCreateParams.Input.ofResponse(items(request.inputItems())));
        if (request.reasoningEffort() != null) {
            builder.reasoning(Reasoning.builder()
                    .effort(com.openai.models.ReasoningEffort.of(request.reasoningEffort().wireValue())).build());
        }
        return builder;
    }

    private static List<ResponseInputItem> items(List<InputItem> items) {
        return items.stream().map(ResponsesRequestMapper::item).toList();
    }

    private static ResponseInputItem item(InputItem item) {
        EasyInputMessage.Role role = item.role() == InputItem.Role.ASSISTANT
                ? EasyInputMessage.Role.ASSISTANT
                : EasyInputMessage.Role.USER;
        EasyInputMessage.Builder message = EasyInputMessage.builder().role(role);
        if (item.imageDataUrl() == null) {
            message.content(item.text());
        } else {
            // Text part first, then the image (OpenRouter image guide).
            message.content(EasyInputMessage.Content.ofResponseInputMessageContentList(List.of(
                    ResponseInputContent.ofInputText(ResponseInputText.builder().text(item.text()).build()),
                    ResponseInputContent.ofInputImage(ResponseInputImage.builder()
                            .imageUrl(item.imageDataUrl()).detail(ResponseInputImage.Detail.AUTO).build()))));
        }
        return ResponseInputItem.ofEasyInputMessage(message.build());
    }
}
