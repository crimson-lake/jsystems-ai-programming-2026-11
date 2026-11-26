# OpenRouter captures

Verbatim recordings of real OpenRouter calls (spikes S-1 and S-2). WireMock stubs in later tasks are derived **only**
from these files; nothing is written from documentation.

Status: recorded on **2026-11-26** by the orchestrator (S-1: 7 calls, all as expected; S-2: 4 calls, the decisions model is not servable).
Redaction: the only edit to any body is `user_id` in `responses-f-invalid-model.json`, replaced with `user_REDACTED` (an account identifier). Everything else is byte for byte.
Cost: S-1 upstream inference cost ≈ $0.0007 in total (`usage.cost_details`); S-2 failed before inference.

## File layout (per capture `<name>`)

| File | Content |
|---|---|
| `<name>.json` or `<name>.sse` | Response body, byte for byte (`.sse` for `text/event-stream`) |
| `<name>.request.json` | Request body as sent (image base64 elided) |
| `<name>.meta.json` | Date, endpoint, model, request summary, HTTP status, latency, request headers and response headers |

`Authorization`, `Cookie` and `Set-Cookie` headers are never stored.

## How to record

From `app/backend`, with the key in the root `.env` (never commit it) and profile `real-api`:

```
./mvnw -Pno-frontend verify -Dtest.excludedGroups= -Dgroups=capture -Dit.test=OpenRouterCaptureIT   # 7 real calls
./mvnw -Pno-frontend verify -Dtest.excludedGroups= -Dgroups=capture -Dit.test=DecisionsCaptureIT    # 4-5 real calls
```

## Capture index (fill in when run: date, status, latency, cost)

### S-1 Responses API (`OpenRouterCaptureIT`, model `LLM_MODEL`, e.g. `openai/gpt-6-luna`)

| Name | What | Recorded |
|---|---|---|
| `responses-a-vision-structured` | (a) image (`phone-1.jpg`) + `text(ImageAnalysisResult)` | 2026-11-26, 200, 5.6 s — parsed into `ImageAnalysisResult` |
| `responses-b-decision-structured` | (b) `text(DecisionResult)` + `provider.require_parameters` | 2026-11-26, 200, 7.3 s — parsed into `DecisionResult`; `provider.require_parameters` accepted |
| `responses-c-streaming-text` | (c) `createStreaming`, plain text | 2026-11-26, 200, 2.3 s — 34 `response.output_text.delta` events, reasoning events present |
| `responses-d-history-three-turns` | (d) user / assistant / user history, plain role-based items | 2026-11-26, 200, 1.5 s — plain role-based assistant items accepted |
| `responses-e-invalid-key` | (e) fake key, 401 | 2026-11-26, 401 `User not found.` |
| `responses-f-invalid-model` | (f) non-existent model id | 2026-11-26, 400 `… is not a valid model ID` |
| `responses-g-incomplete-stream` | (g) `max_output_tokens=16`, streaming | 2026-11-26, 200 stream ending in `response.incomplete` (`max_output_tokens`) |

### S-2 Decisions API (`DecisionsCaptureIT`, model `openai/gpt-6-luna-decisions`)

| Name | What | Recorded |
|---|---|---|
| `decisions-return-phone-1` | RETURN, `phone-1.jpg`, noul + choice + score questions | 2026-11-26, **401** upstream `account_deactivated` (model not servable) |
| `decisions-complaint-laptop-1` | COMPLAINT, `laptop-1.png` (JPEG content), damage questions | 2026-11-26, **401** upstream `account_deactivated` |
| `decisions-error-invalid-key` | fake key, 401 | 2026-11-26, 401 `User not found.` |
| `decisions-error-invalid-model` | non-existent model id | 2026-11-26, 400 `Model … does not exist` |

## Missing captures (shapes without a real recording are not stubbed)

- **S-2 outcome:** no successful Decisions answer exists, because `openai/gpt-6-luna-decisions` returned an upstream 401 `account_deactivated` for both real photo calls. Triage is therefore off (plan fallback F1, ADR-004 Q-T1); no Decisions success stub exists.
- `response.failed` was not provoked; (g) produced `response.incomplete` instead.

Shapes that cannot be provoked on purpose and will stay missing unless OpenRouter returns them during the spikes:

- a real 5xx from `/responses` or `/decisions` (502 provider error, 524 timeout, 529 overloaded);
- a real 429 rate-limit response;
- `response.failed` mid-stream (only (g) tries to provoke `response.incomplete`; `failed` may stay missing);
- a Decisions answer of `type: "refusal"`.

Their error mapping is covered only by the generic HTTP-status mapping tested with the real 401 and invalid-model captures
(WireMock transport faults such as a fixed delay or `CONNECTION_RESET_BY_PEER` need no recorded shape).
