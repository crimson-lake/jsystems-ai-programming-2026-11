package com.hsdc.copilot.triage;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.hsdc.copilot.cases.CaseType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The questions per case type from {@code classpath:triage/questions-return.json} and
 * {@code questions-complaint.json} (ADR-004 section 3.3). Loaded once at startup; a broken file fails the start.
 */
@Component
public class TriageQuestionSet {

    private final Map<CaseType, List<TriageQuestion>> sets;

    public TriageQuestionSet(JsonMapper json) {
        this.sets = Map.of(CaseType.RETURN, load(json, "triage/questions-return.json"), CaseType.COMPLAINT,
                load(json, "triage/questions-complaint.json"));
    }

    /** The questions for the case; reason-dependent questions only when {@code reason} is non-blank. */
    public List<TriageQuestion> forCase(CaseType type, String reason) {
        boolean hasReason = reason != null && !reason.isBlank();
        return sets.get(type).stream().filter(q -> hasReason || !q.requiresReason()).toList();
    }

    private static List<TriageQuestion> load(JsonMapper json, String location) {
        JsonNode root;
        try (InputStream in = new ClassPathResource(location).getInputStream()) {
            root = json.readTree(in);
        }
        catch (IOException e) {
            throw new IllegalStateException("Cannot read " + location, e);
        }
        List<TriageQuestion> questions = new ArrayList<>();
        for (JsonNode q : root.path("questions")) {
            TriageAnswer.Type type = TriageAnswer.Type.valueOf(q.path("type").asString().toUpperCase(Locale.ROOT));
            JsonNode criteria = q.path("criteria");
            boolean valid = switch (type) {
                case NOUL -> criteria.has("true") && criteria.has("false");
                case CHOICE -> criteria.isObject() && !criteria.isEmpty();
                case SCORE -> criteria.isArray() && criteria.size() >= 2;
            };
            if (!valid || q.path("key").asString("").isBlank() || q.path("instructions").asString("").isBlank()) {
                throw new IllegalStateException("Invalid triage question in " + location + ": " + q.path("key"));
            }
            questions.add(new TriageQuestion(q.path("key").asString(), type, q.path("instructions").asString(), criteria,
                    q.path("requiresReason").asBoolean(false)));
        }
        if (questions.isEmpty()) {
            throw new IllegalStateException("No triage questions in " + location);
        }
        return List.copyOf(questions);
    }
}
