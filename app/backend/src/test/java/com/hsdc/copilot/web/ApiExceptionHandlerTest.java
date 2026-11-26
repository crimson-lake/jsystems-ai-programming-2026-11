package com.hsdc.copilot.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.hsdc.copilot.cases.CaseBusyException;
import com.hsdc.copilot.cases.CaseNotFoundException;
import com.hsdc.copilot.cases.PhotoLimitException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** T24: ADR-002 section 3.7 mapping table; bodies carry codes and Polish texts, never internals. */
class ApiExceptionHandlerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SECRET = "secret-internal-detail com.hsdc.copilot.Something";

    record Dto(@NotBlank(message = "Podaj nazwę") String name, @Size(max = 3, message = "Za długie") String note) {
    }

    @RestController
    static class DummyController {

        @PostMapping("/dto")
        void dto(@RequestBody @Valid Dto dto) {
        }

        @GetMapping("/param")
        void param(@RequestParam @Size(min = 3, message = "Za krótkie") String q) {
        }

        @GetMapping("/constraint")
        void constraint() {
            try (var factory = new LocalValidatorFactoryBean()) {
                factory.afterPropertiesSet();
                throw new ConstraintViolationException(factory.validate(new Dto("", null)));
            }
        }

        @GetMapping("/bind")
        void bind() throws BindException {
            BindException e = new BindException(new Dto("x", null), "form");
            e.rejectValue("name", "bad", "Zła nazwa");
            throw e;
        }

        @GetMapping("/fields")
        void fields() {
            throw new FieldValidationException(List.of(new ApiError.FieldError("photo", "Dodaj zdjęcie urządzenia.")));
        }

        @GetMapping("/too-large")
        void tooLarge() {
            throw new MaxUploadSizeExceededException(10L * 1024 * 1024);
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new CaseNotFoundException(SECRET);
        }

        @GetMapping("/busy")
        void busy() {
            throw new CaseBusyException(SECRET);
        }

        @GetMapping("/photo-limit")
        void photoLimit() {
            throw new PhotoLimitException(SECRET);
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException(SECRET);
        }

        @PostMapping("/post-only")
        void postOnly() {
        }
    }

    private static MockMvcTester mvc() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcTester.of(List.of(new DummyController()),
                b -> b.setControllerAdvice(new ApiExceptionHandler()).setValidator(validator).build());
    }

    private static JsonNode body(org.springframework.test.web.servlet.assertj.MvcTestResult result) throws Exception {
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        return JSON.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void assertNoInternals(JsonNode body) {
        String text = body.toString();
        assertThat(text).doesNotContain("secret", "Exception", "com.hsdc", "java.", "at org.", "trace");
    }

    @Test
    void invalidRequestBodyIs400WithPolishFieldErrors() throws Exception {
        var result = mvc().post().uri("/dto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"note\":\"abcdef\"}").exchange();

        assertThat(result).hasStatus(400);
        JsonNode body = body(result);
        assertThat(body.path("code").asString()).isEqualTo("VALIDATION");
        List<String> pairs = body.path("fieldErrors").valueStream()
                .map(f -> f.path("field").asString() + "=" + f.path("message").asString()).sorted().toList();
        assertThat(pairs).containsExactly("name=Podaj nazwę", "note=Za długie");
        assertNoInternals(body);
    }

    @Test
    void constraintViolationIs400() throws Exception {
        var result = mvc().get().uri("/constraint").exchange();

        assertThat(result).hasStatus(400);
        JsonNode body = body(result);
        assertThat(body.path("code").asString()).isEqualTo("VALIDATION");
        assertThat(body.path("fieldErrors").path(0).path("field").asString()).isEqualTo("name");
        assertThat(body.path("fieldErrors").path(0).path("message").asString()).isEqualTo("Podaj nazwę");
    }

    @Test
    void methodParameterValidationIs400() throws Exception {
        var result = mvc().get().uri("/param?q=ab").exchange();

        assertThat(result).hasStatus(400);
        JsonNode body = body(result);
        assertThat(body.path("code").asString()).isEqualTo("VALIDATION");
        assertThat(body.path("fieldErrors").path(0).path("message").asString()).isEqualTo("Za krótkie");
        assertNoInternals(body);
    }

    @Test
    void bindExceptionIs400() throws Exception {
        var result = mvc().get().uri("/bind").exchange();

        assertThat(result).hasStatus(400);
        JsonNode body = body(result);
        assertThat(body.path("fieldErrors").path(0).path("field").asString()).isEqualTo("name");
        assertThat(body.path("fieldErrors").path(0).path("message").asString()).isEqualTo("Zła nazwa");
    }

    @Test
    void fieldValidationExceptionIs400() throws Exception {
        var result = mvc().get().uri("/fields").exchange();

        assertThat(result).hasStatus(400);
        JsonNode body = body(result);
        assertThat(body.path("code").asString()).isEqualTo("VALIDATION");
        assertThat(body.path("fieldErrors").path(0).path("field").asString()).isEqualTo("photo");
        assertThat(body.path("fieldErrors").path(0).path("message").asString()).isEqualTo("Dodaj zdjęcie urządzenia.");
    }

    @Test
    void uploadTooLargeIs413WithTheSizeMessage() throws Exception {
        var result = mvc().get().uri("/too-large").exchange();

        assertThat(result).hasStatus(413);
        assertThat(body(result).toString()).isEqualTo("{\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"Zdjęcie przekracza limit 10 MB\"}");
    }

    @Test
    void caseNotFoundIs404WithOnlyTheCode() throws Exception {
        var result = mvc().get().uri("/not-found").exchange();

        assertThat(result).hasStatus(404);
        assertThat(body(result).toString()).isEqualTo("{\"code\":\"CASE_NOT_FOUND\"}");
    }

    @Test
    void caseBusyIs409WithOnlyTheCode() throws Exception {
        var result = mvc().get().uri("/busy").exchange();

        assertThat(result).hasStatus(409);
        assertThat(body(result).toString()).isEqualTo("{\"code\":\"CASE_BUSY\"}");
    }

    @Test
    void photoLimitIs422WithOnlyTheCode() throws Exception {
        var result = mvc().get().uri("/photo-limit").exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result).toString()).isEqualTo("{\"code\":\"PHOTO_LIMIT\"}");
    }

    @Test
    void anythingElseIs500WithAGenericMessage() throws Exception {
        var result = mvc().get().uri("/boom").exchange();

        assertThat(result).hasStatus(500);
        JsonNode body = body(result);
        assertThat(body.toString()).isEqualTo("{\"code\":\"INTERNAL\",\"message\":\"Wystąpił błąd. Spróbuj ponownie.\"}");
        assertNoInternals(body);
    }

    @Test
    void errorBodyIsJsonEvenWhenTheClientAcceptsOnlyEventStreamFirst() throws Exception {
        var result = mvc().get().uri("/busy").accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON).exchange();

        assertThat(result).hasStatus(409);
        assertThat(body(result).path("code").asString()).isEqualTo("CASE_BUSY");
    }

    @Test
    void frameworkHttpErrorsKeepTheirStatusWithoutInternals() throws Exception {
        var result = mvc().get().uri("/post-only").exchange();

        assertThat(result).hasStatus(405);
        JsonNode body = body(result);
        assertThat(body.has("fieldErrors")).isFalse();
        assertNoInternals(body);
    }
}
