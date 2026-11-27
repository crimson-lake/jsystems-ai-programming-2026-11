package com.hsdc.copilot.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * ADR-002 section 3.8: handler order of the SPA fallback. Needs the packaged frontend in
 * {@code target/classes/static} (copied by {@code ./mvnw package}); the test JS file name is read from index.html.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpaForwardingIT {

    @Autowired
    MockMvcTester mvc;

    @org.junit.jupiter.api.BeforeEach
    void requiresPackagedFrontend() {
        // Under -Pno-frontend there is no static/index.html; the full ./mvnw verify/package runs these tests.
        org.junit.jupiter.api.Assumptions.assumeTrue(SpaForwardingConfig.INDEX.exists(),
                "static/index.html is only present in the full build (not -Pno-frontend)");
    }

    private MvcTestResult getHtml(String path) {
        return mvc.get().uri(path).accept(MediaType.TEXT_HTML).exchange();
    }

    @Test
    void rootAndClientRoutesServeIndexHtml() throws Exception {
        String index = SpaForwardingConfig.INDEX.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (String path : new String[] {"/", "/sprawa"}) {
            MvcTestResult result = getHtml(path);
            assertThat(result).hasStatus(HttpStatus.OK);
            assertThat(result).hasContentTypeCompatibleWith(MediaType.TEXT_HTML);
            assertThat(result.getResponse().getContentAsString()).isEqualTo(index);
        }
    }

    @Test
    void staticJavaScriptIsServedAsJavaScript() throws Exception {
        String index = SpaForwardingConfig.INDEX.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("src=\"(main-[^\"]+\\.js)\"").matcher(index);
        assertThat(m.find()).as("main-*.js referenced from index.html").isTrue();
        MvcTestResult result = mvc.get().uri("/" + m.group(1)).accept(MediaType.ALL).exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result.getResponse().getContentType()).contains("javascript");
    }

    @Test
    void unknownApiPathIsJson404NotIndexHtml() throws Exception {
        MvcTestResult result = mvc.get().uri("/api/x").accept(MediaType.TEXT_HTML, MediaType.APPLICATION_JSON).exchange();
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("<html");
    }

    @Test
    void actuatorHealthIsNotForwarded() throws Exception {
        MvcTestResult result = mvc.get().uri("/actuator/health").accept(MediaType.APPLICATION_JSON).exchange();
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result.getResponse().getContentAsString()).contains("UP");
    }
}
