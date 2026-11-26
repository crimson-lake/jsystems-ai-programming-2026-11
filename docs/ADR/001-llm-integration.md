# ADR-001: LLM Integration — OpenAI Java SDK, OpenRouter, Prompts and Decision Pipeline

**Date:** 2026-11-25
**Status:** Accepted
**Relates to:** [`000-main-architecture.md`](000-main-architecture.md), [`docs/PRD.md`](../PRD.md) Sections 6 (AC-16…AC-29, AC-34…AC-36) and 11

---

## 1. Scope

**Covers:**
- How the backend calls models through OpenRouter with the OpenAI Java SDK.
- The choice between the Responses API and Chat Completions.
- Model configuration.
- Prompt files and context assembly.
- The two-step decision pipeline.
- Deterministic guardrails.
- Streaming and error mapping.
- AI evals.

**Does not cover** HTTP endpoints, SSE transport or case storage (ADR-002), or the UI rendering of messages (ADR-003).

---

## 2. Technology Documentation References

| Library | Context7 ID or official docs | Used for |
|---|---|---|
| OpenAI Java SDK 4.113.0 (`com.openai:openai-java`) | `/openai/openai-java`; https://github.com/openai/openai-java; https://developers.openai.com/api/docs/libraries?language=java | Client, Responses API, streaming, structured outputs, image input |
| OpenRouter Responses API | `/websites/openrouter_ai`; https://openrouter.ai/docs/api/reference/responses/overview | Endpoint semantics: stateless, input items, `text.format`, streaming events |
| OpenRouter streaming | https://openrouter.ai/docs/api_reference/streaming | SSE comments, mid-stream errors, cancellation |
| OpenRouter errors | https://openrouter.ai/docs/api_reference/errors-and-debugging | Status codes, error body, retry rules |
| OpenRouter structured outputs | https://openrouter.ai/docs/guides/features/structured-outputs | `json_schema`, `provider.require_parameters` |
| OpenRouter image understanding | https://openrouter.ai/docs/guides/overview/multimodal/image-understanding | `input_image` with a base64 data URL |

---

## 3. Component Design

### 3.1 Classes and responsibilities (package `…copilot.llm`, `…analysis`, `…decision`, `…prompts`)

| Component | Responsibility |
|---|---|
| `OpenRouterProperties` | Typed configuration: `apiKey`, `baseUrl`, `visionModel`, `decisionModel`, `decisionReasoningEffort`, `appTitle`, `appUrl`, `timeouts` |
| `OpenAiClientConfig` | Defines **one** `OpenAIClient` bean (README: "Don't create more than one client"). Built with `OpenAIOkHttpClient.builder()` with `apiKey(OPENROUTER_API_KEY)`, `baseUrl(OPENROUTER_BASE_URL)`, **`maxRetries(0)`** and a client timeout of 60 s. Each call additionally sets its own `RequestOptions` timeout from the budget in §3.6. Retries are never left to the SDK, because they would break the time budget. The `HTTP-Referer` and `X-OpenRouter-Title` headers are added for every request. **The SDK default env vars `OPENAI_API_KEY`/`OPENAI_BASE_URL` are not used** |
| `LlmGateway` (interface) | Provider-neutral port with three operations: `structured(request, Class<T>) → T`, `stream(request, deltaConsumer, cancelToken) → StreamResult`, and `describe()` for logging |
| `ResponsesApiGateway` | The only class importing the SDK. It maps an `LlmRequest` (model, instructions, input items, images, reasoning effort, JSON-schema class) to `ResponseCreateParams` and calls `client.responses().create(...)` or `.createStreaming(...)`. It maps stream events and SDK exceptions to domain results |
| `ChatCompletionsGateway` | **Fallback only**. Not built unless spike S-1 fails (§6 D-L1) |
| `PromptRepository` | Loads `classpath:prompts/*.md` at startup. Fails fast if a file is missing. Fills named placeholders (`{{policy}}`, `{{today}}` …) by plain string replacement |
| `PolicyRepository` | Loads `classpath:policies/return-policy.md` and `complaint-policy.md`. Extracts the **set of valid rule IDs** per policy with the patterns `^## (R\|C)\d+\.` (section IDs, e.g. `R6`, `C8`) and `^(R\|C)\d+\.\d+\.` (sub-rule IDs, e.g. `R2.3`). Currently valid: `R1–R7, R1.1…R5.3, R7.1` and `C1–C8, C1.1…C7.1` |
| `ImageAnalysisService` | Builds the vision request for the case type and returns a typed `ImageAnalysis` |
| `DecisionService` | Step 1 makes the structured decision, step 2 runs `RuleIdValidator` and then `Guardrails`, and step 3 streams the message through `RuleIdStreamFilter` |
| `RuleIdStreamFilter` | Enforces AC-23 on the streamed text (§3.4).<br>• The message prompts require rule IDs to be written **in square brackets**, e.g. `[R2.3]`. The filter acts **only on bracketed tokens** `\[[RC]\d{1,2}(\.\d{1,2})?\]`, so model names such as "LG C3" or "Canon R5" are never touched.<br>• It passes deltas through, but holds back a partial `[`… token until the token is complete or proves not to be an ID.<br>• It removes any bracketed ID that is not in `decision.ruleIds` and logs each removal at WARN with the case ID.<br>• **The filtered text** is what is stored in history and used for replay.<br>• Unbracketed ID-like text is left as is but counted in the eval report |
| `Guardrails` | Deterministic overrides (§3.4). Pure functions with no I/O |
| `CaseFacts` | Computed facts: `today` (Europe/Warsaw), `daysSincePurchase`, `returnWindowExceeded`, `complaintPeriodExceeded` |

### 3.2 Prompt files (`app/backend/src/main/resources/prompts/`)

The PRD requires four distinct instruction sets (PRD §8). Two auxiliary prompts are added for the streamed message step.

| File | Used by | Content (English instructions; the output language is specified per file) |
|---|---|---|
| `vision-return.md` | Image analysis, RETURN | Describe only what is visible. Report: usable photo, category match, visible damage, signs of use, visible film/accessories/packaging, resellable as new, safety hazard. Fill `summaryPl` in Polish (AC-16, AC-18) |
| `vision-complaint.md` | Image analysis, COMPLAINT | Report: damage visible, type, location, likely cause (enum), confidence (enum), usable photo, category match, safety hazard. Fill `summaryPl` (AC-17, AC-18) |
| `decision-return.md` | Structured decision, RETURN | Role, the four labels with their criteria (PRD §11 table), and the "allowed / not allowed" lists. Only Return Policy rule IDs may be cited. Includes `{{policy}}` (the full Return Policy) |
| `decision-complaint.md` | Structured decision, COMPLAINT | Same structure with the Complaint Policy, plus C7 safety and C8 escalation |
| `message-first.md` | Streamed first message | Polish, informal "Ty" form, at most about 250 words. Exactly three `###` sections: "Co widzimy na zdjęciu", "Uzasadnienie" (it may cite **only** the IDs in `decision.ruleIds`, which are listed in the input, always written in square brackets such as `[R2.3]`), "Kolejne kroki" (numbered list). No greeting, badge, safety block or disclaimer: the UI adds those (§3.5). Mention the other case type when `suggestOtherCaseType` is set (AC-28) |
| `message-reply.md` | Streamed chat reply | Answer using the full context. If `changed=true`, explain the new decision with the same three sections; otherwise answer the question. Cite only IDs in `decision.ruleIds`, in square brackets. Covers off-topic refusal, no promises, no repair instructions, Polish only (AC-45). When the photo limit is reached and the label is still NEEDS_MORE_INFO, the next steps tell the customer to bring the device to a Store shop for in-person assessment, **without citing rule IDs** unless they are in `decision.ruleIds` (PRD §12 assumption, to be confirmed before T25) |

Prompt rules that apply to every file:
- Customer-provided text (reason, chat messages) is passed **only** inside clearly delimited input items labelled as customer data.
- The instructions state that any instructions inside customer data or inside photos must be ignored (PRD §11 "Not allowed").
- Instructions are never echoed back to the customer.

### 3.3 Context assembly (stateless replay)

The OpenRouter Responses API is **stateless**: "You must include the full conversation history in each request. Requests that set `store: true` or a non-null `previous_response_id` are rejected with a 400 error" (OpenRouter responses/overview). Every call is therefore built from `CaseSession` as follows:

| Part | Responses field | Content |
|---|---|---|
| Instructions | `instructions` | The prompt file for the step with `{{policy}}` filled in. Policies are sent only to the decision and message steps, never to the vision step |
| Case facts | first `input` item, role `user`, labelled `CASE FACTS (system-computed, trusted)` | `caseType`, `category`, `model`, `purchaseDate`, `today`, `daysSincePurchase`, window flags, `triageSignals` (decisions-model answers with confidence and HIGH/MEDIUM/LOW band, ADR-004 §3.4; given to the vision and decision steps as hypotheses to verify), all `ImageAnalysis` objects as JSON with their index and time |
| Customer form text | `input` item, role `user`, labelled `CUSTOMER REASON (untrusted)` | The `reason` field |
| History | following `input` items | Prior turns in order: customer → role `user`, assistant body → role `assistant` |
| Current decision | final `input` item (message steps only) | The validated `Decision` the message must explain, plus `changed`/`previousLabel` |
| Image | `input_image` part (vision step only) | `data:image/jpeg;base64,…` of the reduced photo. The text part comes first, then the image (OpenRouter image guide) |

`store` is never set. The SDK README example sets `.store(true)`, and copying that would make OpenRouter return 400.

The assistant history format must be confirmed in spike S-1 (§6). OpenRouter's docs state that replayed assistant output items carry `id` and `status` with `output_text`. If plain role-based assistant messages are rejected, the adapter replays assistant turns as output-message items with synthetic IDs.

### 3.4 Decision pipeline and guardrails

For the first submission, a **triage stage** (ADR-004) runs before this pipeline and contributes `triageSignals`. Its fast path, which would skip the steps below, is not enabled in the PoC. The guardrails apply on every path.

The pipeline is **two steps per turn** (decision D-L2):

1. **Structured decision**:
   - Call `responses.create` with `text(DecisionResult.class)` (JSON schema derived from the class by the SDK, `strict` on).
   - Use model `LLM_DECISION_MODEL` with reasoning effort `LLM_DECISION_REASONING_EFFORT` (default `medium`).
   - Set `provider.require_parameters: true` (an OpenRouter extension, added with `putAdditionalBodyProperty`), so the request routes only to endpoints that support structured outputs.
2. **Validate and enforce**, in this fixed order:
   1. **G6 rule-ID validation** (`RuleIdValidator`):
      - If `ruleIds` contains an ID outside the applied policy, make one corrective re-ask that lists the valid IDs, but only if ≥ 15 s remain in the deadline.
      - If the IDs are still invalid, or there is no time for a re-ask, drop the unknown IDs.
      - If none remain, the label becomes `ESCALATED` with `R6` (return) or `C8` (complaint).
   2. **G1–G5**: the first matching rule wins and records `overriddenBy`.
      - An override **replaces** `ruleIds` with the enforced ID(s) from the table, followed by the model's remaining IDs, which are kept only if the overriding rule is G4 (photo problem, where the model's IDs still explain the request). At most 5 IDs.
      - An override also sets `reasonCode` (G1 → `SAFETY`, G2/G3 → `OUTSIDE_WINDOW`, G4 → `PHOTO_UNUSABLE` or `CATEGORY_MISMATCH`, G5 → `UNCERTAIN_CAUSE`).
   3. **`changeReasonPl`**: when the final label differs from `currentDecision.label`:
      - if the label was set by a guardrail, it is replaced by a **fixed Polish text per guardrail** from `classpath:messages/guardrail-reasons.pl.properties`;
      - if the model changed the label but left the reason empty, the fallback key `model.default` is used: "Na podstawie nowych informacji zmieniliśmy ocenę Twojego zgłoszenia.";
      - otherwise the model's text is kept. Strict-schema limits (1–5 IDs, ≤ 300 chars) are also enforced in code, because structured-output compliance "is not guaranteed on every endpoint".
3. **Streamed message**:
   - Call `responses.createStreaming` with `message-first.md` or `message-reply.md`.
   - Use reasoning effort `low`.
   - Forward only `response.output_text.delta` text, passed through `RuleIdStreamFilter`, so that no rule ID outside `decision.ruleIds` reaches the customer (AC-23).

`DecisionResult` (model output schema):

| Field | Type | Notes |
|---|---|---|
| `label` | enum `ACCEPTED_PRELIMINARY \| REJECTED \| NEEDS_MORE_INFO \| ESCALATED` | AC-20 |
| `ruleIds` | array of string, 1–5 items | Must belong to the applied policy (AC-21, AC-23) |
| `reasonCode` | enum `RESELLABLE \| NOT_RESELLABLE \| OUTSIDE_WINDOW \| COVERED_DEFECT \| EXCLUDED_DAMAGE \| PHOTO_UNUSABLE \| CATEGORY_MISMATCH \| MISSING_INFO \| UNCERTAIN_CAUSE \| CONTRADICTION \| SAFETY \| OTHER` | Used by evals and the message prompt |
| `suggestOtherCaseType` | enum `NONE \| RETURN \| COMPLAINT` | AC-28 |
| `changeReasonPl` | string ≤ 300 chars, may be empty | Polish reason shown in the "Zmiana decyzji" block (AC-35) |
| `missingInfoPl` | string ≤ 300 chars, may be empty | What photo or information is needed (AC-19) |

`Guardrails` G1–G5: deterministic rules, run after G6 (step 2 above), in the order of PRD §11 "Precedence when criteria conflict". The first matching rule wins.

| # | Condition (computed, not asked from the model) | Enforced result | PRD |
|---|---|---|---|
| G1 | **Any** of these, in every `TRIAGE_MODE`:<br>• any analysis has `safetyHazard=true`;<br>• triage `safety_hazard` P(true) ≥ `TRIAGE_SAFETY_PROBABILITY` (ADR-004);<br>• the customer text, lower-cased and without diacritics, matches the safety lexicon: `spuch*`, `napuch*`, `puchnie`, `wybrzusz*`, `dym*`, `iskr*`, `przypal*`, `nadpal*`, `spalon*`, `spalenizn*`, `swad*`, `pali sie`, `zapalil*`, `ogien`, `pozar`, `przegrzew*`, `goraca bateria`, `odslonie* przewod*` | `ESCALATED`, `safety=true`; ruleIds = `R7.1` (return) or `C7.1` (complaint) | AC-26, R7.1, C7.1 |
| G2 | RETURN and `daysSincePurchase > 30` (day 1 = the day after purchase, R2.3) | `REJECTED`, ruleIds include `R2.3` | AC-24 |
| G3 | COMPLAINT and `today > purchaseDate + 2 years` | `REJECTED`, ruleIds include `C2.3` | AC-25 |
| G4 | First decision, and the analysis has `photoUsable=false` or `matchesCategory=false` | `NEEDS_MORE_INFO` | AC-19 |
| G5 | COMPLAINT, model `label=REJECTED`, latest analysis `causeConfidence=LOW` | `ESCALATED`, ruleIds include `C8` | AC-27 |
G6 (rule-ID validation) is described in step 2.1 above and always runs **before** G1–G5.

**Revision rule:**
- `changed = (validated label ≠ currentDecision.label)`.
- On the **first decision** there is no current decision, so `changed=false` and `previousLabel=null`.
- The "Zmiana decyzji" block is driven only by this computed flag, never by the model's text (AC-35, AC-36).
- `safety` is a property of the current decision. Once a case is ESCALATED for safety, later replies keep `safety=true` in their decision snapshot, but the UI shows the safety block only on messages that also show a badge (§3.5).

### 3.5 Message composition: what is model-generated and what is fixed

To make AC-22, AC-26 and AC-35 order-proof, the assistant message is rendered from segments. Only the body is generated by the model.

| Order | Segment | Source |
|---|---|---|
| 1 | Safety block (only if `safety`, and only on the first message or a changed reply) | Fixed Polish text in the frontend (ADR-003 §3.7): stop using and charging, keep away from flammable materials (R7.1 / C7.1) |
| 2 | "Zmiana decyzji" block (only if `changed`) | UI component: previous badge → new badge + `changeReasonPl` |
| 3 | Greeting (first message only) | Fixed frontend text |
| 4 | Decision badge (first message and changed replies) | UI component from the `decision` event |
| 5 | Body | **Streamed model markdown** (three sections or a free answer) |
| 6 | Disclaimer (first message and changed replies) | Fixed exact text from PRD §11, in the frontend |

- AC-26 states that safety instructions begin the message, "before the decision badge content", so for a safety case the greeting comes after the safety block.
- AC-35, as amended on 2026-11-26, allows only the safety block before "Zmiana decyzji".

### 3.6 Streaming, cancellation and errors

- **Forwarded events:** the stream is consumed with the SDK `StreamResponse<ResponseStreamEvent>`. Only `outputTextDelta().delta()` is forwarded. Reasoning events are ignored and never sent to the browser (AC-42).
- **End of stream:** `response.completed` ends the message and carries usage. `response.failed` and `response.incomplete` arrive with HTTP 200 and are treated as errors.
- **Call evidence log:** **every** external AI call writes exactly one INFO line when it finishes, `ai.call step=<TRIAGE|VISION|DECISION|DECISION_REASK|STREAM> caseId=… model=… status=<OK|ERROR|TIMEOUT|CANCELLED> latencyMs=… inTokens=… outTokens=…`, with no content. E2E and manual QA use these lines as proof that each step really ran against OpenRouter (ADR-000 §10).
- **SSE comments:** comments such as `: OPENROUTER PROCESSING` are handled by the SDK and must not be forwarded.
- **Cancellation:**
  - When the browser disconnects (SSE heartbeat write fails, ADR-002), the gateway closes the SDK stream (`StreamResponse.close()`).
  - The OpenRouter docs state that aborting stops billing only for some providers, including OpenAI.

**Time budget (AC-29, AC-40).** Every turn, first submission and chat message alike, is tracked by a per-request `Deadline` of **55 s until the `decision` event**. This is the single budget table; ADR-002 and ADR-004 refer to it.

| Step | Per-call timeout | Retries | Runs on |
|---|---|---|---|
| Triage (ADR-004) | 8 s | none; on failure, continue without signals | First submission only |
| Vision | 20 s | none | Turns with a photo |
| Structured decision | 20 s | none | Every turn |
| G6 corrective re-ask | the remaining time, max 15 s | only if ≥ 15 s remain | Only when IDs are invalid |
| Stream: first delta | before the 55 s deadline | none | Every turn |
| Stream: whole message | 60 s after the `decision` event | none | Every turn |

- **Worst case before `decision`:** 8 + 20 + 20 = 48 s, plus the re-ask only when time remains.
- **Retries:** there are no automatic retries (SDK `maxRetries(0)`). The customer retries through "Spróbuj ponownie" (AC-40, AC-41).
- **Deadline:** if it passes before the `decision` event, the result is `TIMEOUT`.
- **Frontend watchdog:** the frontend aborts after 60 s without a `decision` event (ADR-003 §3.5).

**Error mapping:** every case produces a Polish user-facing message and never exposes internals.

| Source | Domain error | User message (SSE `error`) |
|---|---|---|
| HTTP 401/403/402 | `AI_CONFIG` (internal; logged at ERROR). **Sent on the wire as `AI_UNAVAILABLE`** | "Nie udało się przeanalizować zgłoszenia. Spróbuj ponownie." |
| HTTP 408/429/5xx, network failure | `AI_UNAVAILABLE` | same |
| `response.failed` / `incomplete` / schema parse failure | `AI_UNAVAILABLE` | same |
| Deadline exceeded | `TIMEOUT` | same |

- The wire `error.code` set is exactly `AI_UNAVAILABLE | TIMEOUT` (ADR-000 §6).
- The **chat** inline error text differs (ADR-003 §3.7), but uses the same codes and the "Spróbuj ponownie" action (AC-41).

---

## 4. Data Structures

- `LlmRequest`: `model`, `instructions` (string), `inputItems` (ordered list of `{role, text}` or `{role, text, imageDataUrl}`), `reasoningEffort` (`none|low|medium|high`), `schema` (optional Java class), `timeout`.
- **`ImageAnalysis`** (authoritative typed table; it defines the strict JSON schema captured in S-1). All enums are closed. Code enforces the limits too.

  | Field | Type | Notes |
  |---|---|---|
  | `photoUsable` | boolean | AC-18 |
  | `photoProblem` | enum `NONE \| BLURRY \| TOO_DARK \| NO_DEVICE \| DEVICE_NOT_FULLY_VISIBLE \| DAMAGE_NOT_SHOWN \| OTHER` | AC-19 |
  | `matchesCategory` | boolean | AC-18 |
  | `damageVisible` | boolean | AC-16/17 |
  | `damageType` | enum `NONE \| SCREEN_CRACK \| IMPACT_DENT \| BROKEN_PART \| LIQUID \| BURN_OR_SWELLING \| COSMETIC_WEAR \| DISPLAY_DEFECT \| OTHER` | Same values as triage `damage_type` |
  | `damageLocation` | string ≤ 200 chars, may be empty | Free text, e.g. "lewy zawias" |
  | `likelyCause` | enum `MANUFACTURING_DEFECT \| IMPACT \| LIQUID \| WEAR \| UNKNOWN` | AC-17 |
  | `causeConfidence` | enum `HIGH \| MEDIUM \| LOW` | AC-17, G5 |
  | `signsOfUse` | boolean | AC-16 |
  | `visibleAccessories` | list of enum `PROTECTIVE_FILM \| ORIGINAL_BOX \| CHARGER \| CABLE \| MANUAL \| OTHER`, max 6 | AC-16 |
  | `resellableAsNew` | boolean | AC-16 |
  | `safetyHazard` | boolean | G1 |
  | `summaryPl` | string ≤ 500 chars | "Co widzimy na zdjęciu" |
  | `triageDisagreements` | list of `{key: string, triageValue: string, llmValue: string}`, max 10, may be empty | ADR-004 §3.4 |
  | `source` | enum `LLM \| TRIAGE` | Set by code, not by the model (excluded from the model schema) |
- `DecisionResult`: §3.4.
- `Decision` (validated): `DecisionResult` (with `changeReasonPl` possibly replaced, §3.4) + `safety` + `changed` + `previousLabel` + `overriddenBy` (`G1…G6` or empty).
- `StreamResult`: `fullText`, `inputTokens`, `outputTokens`, `finishStatus` (`COMPLETED|FAILED|INCOMPLETE|CANCELLED`).

---

## 5. Interface Contracts

| Operation | Input | Output | Errors |
|---|---|---|---|
| `ImageAnalysisService.analyze(caseType, category, jpegBytes, deadline)` | Case type, category, reduced photo | `ImageAnalysis` | `LlmUnavailable`, `LlmTimeout` |
| `DecisionService.decide(caseSession, turnInput, deadline)` | Case incl. analyses, history and the new user text | validated `Decision` | `LlmUnavailable`, `LlmTimeout` |
| `DecisionService.streamMessage(caseSession, decision, kind=FIRST\|REPLY, onDelta, cancelToken)` | Case, decision | `StreamResult` | `LlmUnavailable`, `LlmTimeout`, `Cancelled` |
| `LlmGateway.structured / stream` | `LlmRequest` | typed object / `StreamResult` | Mapped SDK exceptions (`OpenAIServiceException` subclasses → domain errors) |

---

## 6. Technical Decisions

### D-L1 Responses API (not Chat Completions) for all calls
**Status:** Accepted · **Date:** 2026-11-25
**Context:** The SDK supports both `client.responses()` and `client.chat().completions()`, and OpenRouter exposes both `/api/v1/responses` and `/api/v1/chat/completions`. We need image input, JSON-schema output, token streaming and multi-turn chat.

**Evidence gathered on 2026-11-25:**

| Criterion | Responses API | Chat Completions |
|---|---|---|
| OpenRouter positioning | Quickstart: "The most direct way to use OpenRouter". Described as an OpenAI-compatible drop-in; no beta notice on the overview, basic-usage, reasoning or quickstart pages | Fully documented, with OpenRouter extensions (`models`, `route`, `provider`, `plugins` incl. response-healing); not marked legacy |
| Image input | `input_image` with base64 data URL; png/jpeg/webp/gif. **The image-understanding guide documents images only via Responses** | `image_url.url` accepts a URL or base64 (API schema) |
| Structured output | `text.format: json_schema`. SDK: `text(Class<T>)` | `response_format: json_schema`. SDK: `responseFormat(Class<T>)` |
| Streaming | Typed events (`response.output_text.delta`, `response.completed` with usage, `response.failed`, `response.incomplete`) and separate reasoning-delta events | Chunks with `delta.content`; errors are a chunk with `finish_reason: "error"` |
| State | Stateless on OpenRouter; full history each call; `store`/`previous_response_id` → 400 | Stateless by design |
| Reasoning control | `reasoning.effort` (`none`…`high`) and reasoning summaries, documented | Via the OpenRouter `reasoning` extension |
| SDK direction | The SDK README presents Responses as the primary API, with `ResponseAccumulator` and `text(Class)` | Supported, with `ChatCompletionAccumulator` |

**Decision:**
- Use the Responses API through `ResponsesApiGateway`.
- Typed stream events make the "decision first, then deltas, then done/error" contract (ADR-000 TAC-03) straightforward, and reasoning output stays separated from user-visible text.
- It matches OpenRouter's primary documented path for images.
- The stateless limitation costs nothing extra, because D-05 already keeps history on the server.

**Rejected alternatives:**
- Chat Completions: equally capable for our needs and with richer OpenRouter routing extensions, but it mixes errors into content chunks and is not the documented path for images on OpenRouter. It is kept as a fallback adapter behind `LlmGateway`.
- Spring AI 2.1 (`spring-ai-openai`): adds an abstraction layer with its own release cadence, and the brief asks for the official OpenAI Java SDK.
- `openai-java-spring-boot-starter`: per the README it "targets Spring Boot 2.7 and is OpenAI EOL as of 2026-07-27"; we define our own `OpenAIClient` bean instead.

**Consequences:**
- (+) One API for vision, decision and streaming. Typed events and accumulators are available.
- (−) Some OpenRouter extensions (`provider.require_parameters`, `models` fallback) are documented only on the Chat Completions schema, and whether `/responses` honors them is **unverified**. S-1 must check this.

**Spike S-1 (first implementation task, before any production code):**
- Using the example photos in `assets/example-images/` (real photos, unedited, with no description of their content in the prompt), capture verbatim OpenRouter responses for these calls against `openai/gpt-6-luna`:
  - (a) `responses.create` with an image and `text(ImageAnalysis.class)`;
  - (b) `responses.create` with `text(DecisionResult.class)` and `provider.require_parameters`;
  - (c) `createStreaming` with plain text;
  - (d) a three-turn history including assistant turns;
  - (e) an invalid key → 401;
  - (f) a non-existent model ID → the provider's error shape;
  - (g) if obtainable, any `response.failed` or `response.incomplete` stream event (e.g. with a very low `max_output_tokens`).
- The schema classes used by (a) and (b) are **provisional test-scope records** in the capture test. The production records are created later from the same shape (plan T14).
- Save each capture (status, headers without the key, body or SSE transcript, date, model) under `app/backend/src/test/resources/openrouter-captures/`. WireMock stubs are derived only from these captures (AGENTS.md).
- **No stub is built from documentation** (product owner rule, 2026-11-26). Every stub body, status and header comes from a recorded real call, byte for byte.
- **Allowed without a response shape:** WireMock **transport faults**, which invent nothing:
  - a fixed delay added to a captured response (timeout tests);
  - `CONNECTION_RESET_BY_PEER` / `EMPTY_RESPONSE` (network-failure tests).
- **Real error responses** to capture: (e) 401 invalid key; (f) invalid model; (g) `response.failed` / `response.incomplete`, if obtainable; and any other real error OpenRouter returns during the spike.
- **Shapes that cannot be captured** (for example a real 5xx, or (g) if it could not be provoked) are **not stubbed**. They are listed as "uncovered: missing capture" in `openrouter-captures/README.md` and reported in the T12 summary and the final PR (AGENTS.md: "report the missing prerequisite instead of inventing a response shape"). Their error-mapping code paths stay covered only by the generic HTTP-status mapping tested with the real 401/400 captures.
- If (a), (b) or (d) cannot be made to work, switch to `ChatCompletionsGateway` and update this ADR.

**Review trigger:** S-1 fails; OpenRouter marks Responses as beta or deprecated; or model fallback routing (`models: [...]`) becomes a requirement.

### D-L2 Two calls per turn: structured decision, then a streamed message
**Status:** Accepted · **Date:** 2026-11-25
**Context:**
- The UI must show a machine-readable label (badge, summary panel, AC-30/35/36) and stream Polish text progressively (AC-37).
- Guardrails (AC-24…27) must be enforced in code, not trusted to the model.
**Decision:** Each turn makes a non-streamed structured `DecisionResult` call, runs the guardrails, and then makes a streamed message call that receives the final decision as a fixed fact.
**Rejected alternatives:**
- One streamed structured call `{label, message}`: OpenRouter supports structured output with streaming, but the deltas are partial JSON and would need an incremental parser. The label also arrives too late for the guardrails.
- One streamed call with a `DECISION:` marker line: not schema-guaranteed, and the guardrails could only run after text was already shown.
**Consequences:**
- (+) Guardrails run before any text reaches the customer. The message can never contradict the badge. Evals can score labels without parsing prose.
- (−) Each turn costs about twice the input tokens, since the policy is sent twice, and adds the latency of one extra call. This is acceptable for the PoC and is logged as token usage.
**Review trigger:** The average first-decision latency exceeds 30 s, or the cost per case exceeds the PoC budget.

### D-L3 Separate, configurable vision and decision models
**Status:** Accepted · **Date:** 2026-11-25
**Decision:**
- `LLM_VISION_MODEL` and `LLM_DECISION_MODEL` both default to `LLM_MODEL` (`openai/gpt-6-luna`).
- At startup the backend logs the effective model IDs; they are never sent to the browser.
- Model capabilities (image input, structured outputs) must be checked on the model's OpenRouter page during S-1. The gpt-6-luna capability flags were **not verified** in this research.
**Rejected alternatives:**
- A single hard-wired model: it prevents a cheaper vision model and A/B evals.
**Review trigger:** Evals show that a different model is better or cheaper for a role.

### D-L4 Deterministic guardrails and rule-ID validation in code
**Status:** Accepted · **Date:** 2026-11-25
**Context:** Time windows, safety escalation and the low-confidence rule are hard requirements (AC-24…27), and the model can hallucinate rule IDs (AC-23).
**Decision:** Apply G1–G6 (§3.4) after every structured decision. Pass `daysSincePurchase` and the window flags to the model as trusted facts, so its explanation matches the enforced outcome.
**Consequences:**
- (+) The hard ACs are guaranteed regardless of model behavior and unit-testable without AI.
- (−) The safety lexicon may over-escalate: a false positive leads to a human, which is the safe direction.
**Review trigger:** The policies change their windows, IDs or safety rules. `PolicyRepository` re-extracts the IDs automatically, but G2/G3/G1 reference specific IDs and must be updated.

### D-L5 Prompt-injection and data-handling stance
**Status:** Accepted · **Date:** 2026-11-25
**Decision:**
- Customer text and photo-borne text are always delimited and declared untrusted in the instructions.
- The decision is enforced by guardrails.
- Logs contain the case ID, step, model, token usage, latency and outcome, never the photo, the reason text or the messages.
- Only synthetic data is used in development and evals (PRD §8).
**Review trigger:** Real customer data or production use.

---

## 7. Diagrams

### Component / class diagram

```mermaid
classDiagram
  class LlmGateway {
    <<interface>>
    +structured(LlmRequest, Class~T~) T
    +stream(LlmRequest, DeltaConsumer, CancelToken) StreamResult
  }
  class ResponsesApiGateway
  class ChatCompletionsGateway {
    fallback, not built by default
  }
  class OpenAIClient {
    <<OpenAI Java SDK>>
  }
  class ImageAnalysisService
  class DecisionService
  class Guardrails
  class RuleIdValidator
  class PolicyRepository
  class PromptRepository
  class CaseFacts
  LlmGateway <|.. ResponsesApiGateway
  LlmGateway <|.. ChatCompletionsGateway
  ResponsesApiGateway --> OpenAIClient
  ImageAnalysisService --> LlmGateway
  ImageAnalysisService --> PromptRepository
  DecisionService --> LlmGateway
  DecisionService --> PromptRepository
  DecisionService --> PolicyRepository
  DecisionService --> Guardrails
  DecisionService --> RuleIdValidator
  Guardrails --> CaseFacts
  RuleIdValidator --> PolicyRepository
```

### Decision pipeline (one turn)

```mermaid
flowchart TD
  A[Turn input: form or chat message + optional photo] --> B{Photo?}
  B -- yes --> C[Vision call<br/>vision-return.md / vision-complaint.md<br/>text: ImageAnalysis schema]
  B -- no --> D
  C --> D[Compute CaseFacts<br/>today Europe/Warsaw, daysSincePurchase]
  D --> E[Structured decision call<br/>decision-return.md / decision-complaint.md + policy<br/>text: DecisionResult schema]
  E --> F{G6: rule IDs valid?}
  F -- "no, ≥15 s left" --> F1[One corrective re-ask] --> F2{valid now?}
  F2 -- no --> F3[Drop unknown IDs; none left → ESCALATED R6/C8]
  F -- "no, no time" --> F3
  F2 -- yes --> G
  F3 --> G
  F -- yes --> G[Guardrails G1..G5<br/>first match wins, PRD §11 precedence]
  G --> H[Validated Decision<br/>changed = label != current]
  H --> I[SSE decision event]
  I --> J[Streamed message call<br/>message-first.md / message-reply.md]
  J --> K[SSE delta events]
  K --> L[Commit turn to CaseSession]
```

### Streaming sequence with cancellation

```mermaid
sequenceDiagram
  participant DS as DecisionService
  participant GW as ResponsesApiGateway
  participant SDK as OpenAIClient
  participant OR as OpenRouter
  participant EM as SseEmitter
  DS->>GW: stream(request, onDelta, cancelToken)
  GW->>SDK: responses().createStreaming(params)
  SDK->>OR: POST /api/v1/responses (stream=true)
  loop events
    OR-->>SDK: response.output_text.delta
    SDK-->>GW: ResponseStreamEvent
    GW-->>DS: onDelta(text)
    DS-->>EM: send(delta)
    alt client gone (send throws IOException)
      DS->>GW: cancelToken.cancel()
      GW->>SDK: StreamResponse.close()
    end
  end
  OR-->>SDK: response.completed (usage) or response.failed
  GW-->>DS: StreamResult(status, usage)
```

---

## 8. Testing Strategy

### Test scenarios for this area

| Scenario | Type | Input | Expected output | Edge cases |
|---|---|---|---|---|
| Client wiring | Unit | Properties with key, base URL and title | Client uses the OpenRouter base URL and sends `HTTP-Referer`/`X-OpenRouter-Title`; `store` absent | Missing key → startup fails with a clear message |
| Request mapping | Unit | `LlmRequest` with image + schema | `ResponseCreateParams` has `instructions`, the input text before `input_image`, a data URL, and `text.format` json_schema | Reasoning effort `none` |
| History replay | Unit | Case with 3 turns | Input items in order: facts, reason, user/assistant alternating, decision | Empty reason for RETURN |
| Rule-ID extraction | Unit | Current policy files | The ID sets equal the lists in §3.1 | A new sub-rule added → picked up |
| Guardrail G1 safety | Unit | Analysis `safetyHazard=true`, model ACCEPTED | ESCALATED, `safety`, `C7.1` (complaint) / `R7.1` (return) | Lexicon match in the chat text only, incl. "napuchnięta", "nadpalony", "przegrzewa się", "ogień" with and without diacritics; triage P(true)=0.30/0.29; **RETURN 45 days old + safety → ESCALATED, not REJECTED** |
| Guardrail ID replacement | Unit | G2 overrides a model ACCEPTED with `R3.1` | ruleIds = `[R2.3]`, reasonCode `OUTSIDE_WINDOW`, fixed `changeReasonPl` when the label changed | G4 keeps the model's IDs |
| Stream rule-ID filter | Unit | Deltas `"…zgodnie z [R"`, `"9.9] i [R2."`, `"3]."` with decision IDs `[R2.3]` | Output contains `[R2.3]` and not `[R9.9]`; a WARN is logged; the stored text is the filtered one | ID split across 3 deltas; text ending with a partial `[R`; "LG C3" and "Canon R5" unchanged |
| First decision flags | Unit | No current decision | `changed=false`, `previousLabel=null` | — |
| Empty model change reason | Unit | Model changes the label, `changeReasonPl=""` | Fallback `model.default` text | — |
| Guardrail G2 window | Unit | RETURN, purchase = today − 30 / − 31 days | ACCEPTED allowed / REJECTED with `R2.3` | Purchase today (0 days) |
| Guardrail G3 | Unit | COMPLAINT, purchase = today − 2y / − 2y − 1d | Allowed / REJECTED with `C2.3` | 29 February purchase |
| Guardrail G5 | Unit | COMPLAINT, LOW confidence, model REJECTED | ESCALATED with `C8` | MEDIUM confidence → REJECTED kept |
| Rule-ID validation G6 | Unit | Model returns `C2.3` on a RETURN case | Re-ask once (if ≥ 15 s left), then drop → ESCALATED with `R6` if empty | Valid + invalid mix; < 15 s left → no re-ask |
| Change flag | Unit | Current REJECTED, new ACCEPTED | `changed=true`, `previousLabel=REJECTED` | Same label → `changed=false` |
| Stream mapping | Unit | **Recorded SSE transcripts** from S-1 (c) (and (g) if captured), parsed into SDK events, including any reasoning events the real model emitted | Only text deltas forwarded; a captured `response.failed`/`incomplete` → `LlmUnavailable` | If (g) was not captured, the failed-event case is listed as uncovered (§6 S-1) |
| Structured call via WireMock | Integration | Stub = capture S-1(b) | `DecisionResult` parsed | Real captured 400 (invalid model, (f)) → `AI_UNAVAILABLE`, no automatic retry |
| Streaming via WireMock | Integration | Stub = capture S-1(c) as an SSE body | Deltas in order and usage logged | Real captured 401 (e) → `AI_UNAVAILABLE`; the captured success response with a fixed delay beyond the budget → `TIMEOUT`; `CONNECTION_RESET_BY_PEER` fault → `AI_UNAVAILABLE` |
| AI evals | Eval (real model) | `app/evals/cases/*.json`: about 15 cases, combining the 5 unedited example photos with form data. The **expectations come only from the form and the policy** (PRD §12, 2026-11-26):<br>• return 31 / 45 days → REJECTED `R2.3`, **or** ESCALATED `R7.1` if the recorded analysis or triage triggered G1 (safety precedence; the photo content is never pre-assumed);<br>• complaint > 2 years → REJECTED `C2.3`, with the same safety allowance (`C7.1`);<br>• safety wording in the reason → ESCALATED `R7.1`/`C7.1`;<br>• prompt-injection reason → not ACCEPTED solely because of the injection;<br>• every cited ID ⊆ the applied policy;<br>• Polish reply to an English question (AC-45) | 100% on these assertions. For every photo, the model's `ImageAnalysis` and label are **recorded in the report without pass/fail**, because the README forbids pre-assuming what the model sees | Run 3× to detect flakiness |

### Technical acceptance criteria
- TAC-001-01: Only `ResponsesApiGateway` (and the optional fallback) imports `com.openai.*`, enforced by an ArchUnit-style test or a simple classpath scan test.
- TAC-001-02: No outgoing request body contains `"store":true` or `previous_response_id` (asserted on WireMock request journals).
- TAC-001-03: Every validated decision's `ruleIds` is a non-empty subset of the applied policy's extracted ID set.
- TAC-001-04: G1–G6, `RuleIdStreamFilter` and the ID-replacement logic have 100% branch coverage in unit tests, measured with JaCoCo, which is configured in the POM.
- TAC-001-07: No streamed or replayed text delivered to the browser contains a bracketed rule ID outside `decision.ruleIds` (integration test with a stub whose body cites `[R9.9]`).
- TAC-001-05: The SSE output never contains reasoning text, model names or instruction text (integration test with a stub that includes reasoning events).
- TAC-001-06: Every WireMock stub body, status and header is copied from a capture file that records its date, endpoint, model and request without secrets. The only additions allowed are transport faults (delay, connection reset). No stub is derived from documentation, and a test checks that every stub file references an existing capture.
