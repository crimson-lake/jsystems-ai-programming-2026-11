package com.hsdc.copilot.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RequestPredicates.accept;

/**
 * SPA fallback (ADR-002 section 3.8): browser navigations to client routes such as {@code /sprawa} get
 * {@code index.html}. API, actuator and error paths, and any path whose last segment has a dot (static files),
 * are left to their own handlers.
 */
@Configuration(proxyBeanMethods = false)
public class SpaForwardingConfig {

    static final Resource INDEX = new ClassPathResource("static/index.html");

    @Bean
    RouterFunction<ServerResponse> spaFallback() {
        RequestPredicate clientRoute = request -> {
            String path = request.path();
            if (path.startsWith("/api/") || path.equals("/api") || path.startsWith("/actuator") || path.startsWith("/error")) {
                return false;
            }
            String last = path.substring(path.lastIndexOf('/') + 1);
            return !last.contains(".");
        };
        return RouterFunctions.route(GET("/**").and(accept(MediaType.TEXT_HTML)).and(clientRoute),
                request -> INDEX.exists()
                        ? ServerResponse.ok().contentType(MediaType.TEXT_HTML).body(INDEX)
                        : ServerResponse.notFound().build());
    }
}
