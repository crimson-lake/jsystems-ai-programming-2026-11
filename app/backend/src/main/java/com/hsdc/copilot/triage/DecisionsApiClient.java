package com.hsdc.copilot.triage;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.hsdc.copilot.config.OpenRouterProperties;
import com.hsdc.copilot.config.TriageProperties;
import com.hsdc.copilot.triage.TriageAnswer.Band;
import com.hsdc.copilot.triage.TriageAnswer.Type;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The only class that knows the OpenRouter Decisions API (alpha) and its TypeSafe Jev 1.14 wire format
 * (ADR-004 sections 3.2, 3.3 and 4; recorded in {@code openrouter-captures/decisions-jev-*}). Spring {@link RestClient}
 * with the bearer key, the OpenRouter attribution headers and the {@code triage.timeout} (8 s) as read timeout; no
 * retries. Any non-2xx status, timeout, transport error or malformed body becomes {@link TriageUnavailableException}.
 * Answers are mapped fail-closed: a {@code refusal}, a missing key or an unexpected shape gives {@code refused=true}.
 */
@Component
public class DecisionsApiClient {

    /**
     * A successful response.
     *
     * @param model the slug the response reports (dated, e.g. {@code typesafe/jev-1.14-20261014})
     */
    public record DecisionsResponse(String model, Map<String, TriageAnswer> answers, long inputTokens,
            long outputTokens, Double cost) {
    }

    private final TriageProperties triage;
    private final JsonMapper json;
    private final RestClient rest;

    public DecisionsApiClient(TriageProperties triage, OpenRouterProperties openRouter, JsonMapper json) {
        this.triage = triage;
        this.json = json;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(triage.timeout()).build());
        factory.setReadTimeout(triage.timeout());
        RestClient.Builder builder = RestClient.builder()
            .requestFactory(factory)
            .defaultHeader("Authorization", "Bearer " + openRouter.apiKey());
        if (StringUtils.hasText(openRouter.appUrl())) {
            builder.defaultHeader("HTTP-Referer", openRouter.appUrl());
        }
        if (StringUtils.hasText(openRouter.appTitle())) {
            builder.defaultHeader("X-OpenRouter-Title", openRouter.appTitle());
        }
        this.rest = builder.build();
    }

    /**
     * Asks the questions about one photo. {@code facts} becomes the first {@code state} item; the photo is sent once
     * as an asset under a fresh UUID that is the second {@code state} item.
     */
    public DecisionsResponse ask(String model, Map<String, Object> facts, byte[] photo, List<TriageQuestion> questions) {
        String body = json.writeValueAsString(requestBody(model, facts, UUID.randomUUID().toString(), photo, questions));
        String response;
        try {
            response = rest.post()
                .uri(triage.baseUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        }
        catch (RestClientResponseException e) {
            throw new TriageUnavailableException("Decisions API returned HTTP " + e.getStatusCode().value(), false);
        }
        catch (RestClientException e) {
            boolean timeout = causedByTimeout(e);
            throw new TriageUnavailableException(
                    timeout ? "Decisions API timed out" : "Decisions API transport error: " + e.getClass().getSimpleName(),
                    timeout);
        }
        return parse(response, questions, triage.lowConfidence(), triage.highConfidence(), json);
    }

    // ------------------------------------------------------------------ request

    ObjectNode requestBody(String model, Map<String, Object> facts, String assetId, byte[] photo,
            List<TriageQuestion> questions) {
        ObjectNode root = json.createObjectNode();
        root.put("model", model);
        ArrayNode state = root.putArray("state");
        state.add(json.valueToTree(facts));
        state.add(assetId);
        ObjectNode asset = root.putObject("assets").putObject(assetId);
        asset.put("type", "image");
        asset.put("format", format(photo));
        asset.put("data_base64", Base64.getEncoder().encodeToString(photo));
        ObjectNode qs = root.putObject("questions");
        for (TriageQuestion q : questions) {
            ObjectNode node = qs.putObject(q.key());
            node.put("type", q.type().name().toLowerCase(Locale.ROOT));
            node.put("instructions", q.instructions());
            node.set("criteria", q.criteria());
        }
        return root;
    }

    /** Jev accepts PNG and JPEG only; S-2 sent {@code "jpeg"} (the recorded wire value). */
    static String format(byte[] photo) {
        return isPng(photo) ? "png" : "jpeg";
    }

    static boolean isSupported(byte[] photo) {
        return isPng(photo) || (photo != null && photo.length >= 3 && (photo[0] & 0xFF) == 0xFF
                && (photo[1] & 0xFF) == 0xD8 && (photo[2] & 0xFF) == 0xFF);
    }

    private static boolean isPng(byte[] b) {
        return b != null && b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    private static boolean causedByTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ response

    /** Maps a 2xx body; throws {@link TriageUnavailableException} when it is not a JSON object with {@code answers}. */
    static DecisionsResponse parse(String body, List<TriageQuestion> questions, double low, double high,
            JsonMapper json) {
        JsonNode root;
        try {
            root = body == null ? null : json.readTree(body);
        }
        catch (JacksonException e) {
            throw new TriageUnavailableException("Decisions API body is not JSON", false);
        }
        if (root == null || !root.isObject() || !root.path("answers").isObject()) {
            throw new TriageUnavailableException("Decisions API body has no answers", false);
        }
        JsonNode answers = root.path("answers");
        Map<String, TriageAnswer> mapped = new LinkedHashMap<>();
        for (TriageQuestion q : questions) {
            mapped.put(q.key(), toAnswer(q, answers.get(q.key()), low, high));
        }
        JsonNode usage = root.path("usage");
        Double cost = usage.path("cost").isNumber() ? usage.path("cost").asDouble() : null;
        String model = root.path("model").isString() ? root.path("model").asString() : null;
        return new DecisionsResponse(model, mapped, usage.path("input_tokens").asLong(0),
                usage.path("output_tokens").asLong(0), cost);
    }

    static TriageAnswer toAnswer(TriageQuestion q, JsonNode wire, double low, double high) {
        if (wire == null || !wire.isObject() || !q.type().name().toLowerCase(Locale.ROOT)
            .equals(wire.path("type").asString(""))) {
            return refused(q);
        }
        return switch (q.type()) {
            case NOUL -> noul(q, wire, low, high);
            case CHOICE -> choice(q, wire, low, high);
            case SCORE -> score(q, wire, low, high);
        };
    }

    private static TriageAnswer noul(TriageQuestion q, JsonNode wire, double low, double high) {
        JsonNode p = wire.get("noul");
        if (p == null || !p.isNumber() || p.asDouble() < 0 || p.asDouble() > 1) {
            return refused(q);
        }
        double pTrue = p.asDouble();
        double confidence = Math.max(pTrue, 1 - pTrue);
        Map<String, Double> probabilities = Map.of("true", pTrue, "false", 1 - pTrue);
        return new TriageAnswer(q.key(), Type.NOUL, pTrue >= 0.5, pTrue, null, probabilities, confidence, true,
                band(confidence, low, high), false);
    }

    private static TriageAnswer choice(TriageQuestion q, JsonNode wire, double low, double high) {
        JsonNode choice = wire.get("choice");
        Double confidence = probability(wire.get("confidence"));
        if (choice == null || !choice.isString() || confidence == null) {
            return refused(q);
        }
        Map<String, Double> probabilities = probabilities(wire.get("probabilities"), null);
        return new TriageAnswer(q.key(), Type.CHOICE, choice.asString(), null, null, probabilities, confidence, false,
                band(confidence, low, high), false);
    }

    private static TriageAnswer score(TriageQuestion q, JsonNode wire, double low, double high) {
        JsonNode score = wire.get("score");
        JsonNode legend = wire.get("legend");
        Double confidence = probability(wire.get("confidence"));
        Map<String, Double> probabilities = probabilities(wire.get("probabilities"), legend);
        if (score == null || !score.isNumber() || legend == null || !legend.isObject() || confidence == null
                || probabilities == null || probabilities.isEmpty()) {
            return refused(q);
        }
        String top = probabilities.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        return new TriageAnswer(q.key(), Type.SCORE, top, null, score.asDouble(), probabilities, confidence, false,
                band(confidence, low, high), false);
    }

    /** Probabilities as returned; with a {@code legend}, the {@code "0".."N-1"} keys become the legend labels. */
    private static Map<String, Double> probabilities(JsonNode node, JsonNode legend) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Map<String, Double> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : node.properties()) {
            String key = e.getKey();
            if (legend != null) {
                JsonNode label = legend.get(key);
                if (label == null || !label.isString()) {
                    return null;
                }
                key = label.asString();
            }
            if (!e.getValue().isNumber()) {
                return null;
            }
            map.put(key, e.getValue().asDouble());
        }
        return map;
    }

    private static Double probability(JsonNode node) {
        if (node == null || !node.isNumber() || node.asDouble() < 0 || node.asDouble() > 1) {
            return null;
        }
        return node.asDouble();
    }

    private static TriageAnswer refused(TriageQuestion q) {
        return new TriageAnswer(q.key(), q.type(), null, null, null, null, 0.0, false, Band.LOW, true);
    }

    /** HIGH at or above {@code high}, LOW below {@code low}, otherwise MEDIUM (both bounds inclusive upwards). */
    public static Band band(double confidence, double low, double high) {
        if (confidence >= high) {
            return Band.HIGH;
        }
        return confidence < low ? Band.LOW : Band.MEDIUM;
    }
}
