package com.hsdc.copilot.capture;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal forwarding proxy on 127.0.0.1 that records every HTTP exchange verbatim (status, headers, body).
 * The SDK client is pointed at it, so the captured request is exactly what the SDK sends.
 * Responses are streamed through chunk by chunk, so SSE keeps its real timing.
 * Test-scope helper for the capture tests only; it never logs or stores the Authorization header.
 */
final class RecordingProxy implements AutoCloseable {

    /** One recorded exchange. Header names are lower-case; Authorization and cookies are not stored. */
    record Exchange(
            String method,
            String path,
            Map<String, String> requestHeaders,
            byte[] requestBody,
            int status,
            Map<String, String> responseHeaders,
            byte[] responseBody,
            long latencyMs) {
    }

    private static final Set<String> SKIP_REQUEST_HEADERS =
            Set.of("host", "connection", "content-length", "accept-encoding", "expect", "upgrade", "transfer-encoding");
    private static final Set<String> SKIP_RESPONSE_HEADERS = Set.of("transfer-encoding", "connection", "content-length");
    private static final Set<String> NEVER_STORE = Set.of("authorization", "cookie", "set-cookie");

    private final String upstream;
    private final HttpServer server;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final List<Exchange> exchanges = new CopyOnWriteArrayList<>();

    RecordingProxy(String upstream) throws IOException {
        this.upstream = upstream;
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    /** Base URL to give the client instead of the upstream origin, e.g. {@code proxy.url("/api/v1")}. */
    String url(String pathPrefix) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + pathPrefix;
    }

    List<Exchange> exchanges() {
        return List.copyOf(exchanges);
    }

    private void handle(HttpExchange in) throws IOException {
        long start = System.nanoTime();
        byte[] reqBody = in.getRequestBody().readAllBytes();
        String path = in.getRequestURI().getRawPath()
                + (in.getRequestURI().getRawQuery() == null ? "" : "?" + in.getRequestURI().getRawQuery());
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(upstream + path))
                .timeout(Duration.ofSeconds(180))
                .method(in.getRequestMethod(), HttpRequest.BodyPublishers.ofByteArray(reqBody));
        Map<String, String> reqHeaders = new TreeMap<>();
        in.getRequestHeaders().forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (SKIP_REQUEST_HEADERS.contains(lower)) {
                return;
            }
            String joined = String.join(", ", values);
            rb.header(name, joined);
            if (!NEVER_STORE.contains(lower)) {
                reqHeaders.put(lower, joined);
            }
        });
        try {
            HttpResponse<InputStream> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
            Map<String, String> respHeaders = new TreeMap<>();
            resp.headers().map().forEach((name, values) -> {
                String lower = name.toLowerCase(Locale.ROOT);
                if (!NEVER_STORE.contains(lower)) {
                    respHeaders.put(lower, String.join(", ", values));
                }
                if (!SKIP_RESPONSE_HEADERS.contains(lower) && !NEVER_STORE.contains(lower)) {
                    in.getResponseHeaders().add(name, String.join(", ", values));
                }
            });
            ByteArrayOutputStream recorded = new ByteArrayOutputStream();
            in.sendResponseHeaders(resp.statusCode(), 0);
            try (InputStream body = resp.body(); OutputStream out = in.getResponseBody()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = body.read(buf)) != -1) {
                    recorded.write(buf, 0, n);
                    out.write(buf, 0, n);
                    out.flush();
                }
            }
            exchanges.add(new Exchange(in.getRequestMethod(), path, reqHeaders, reqBody, resp.statusCode(),
                    respHeaders, recorded.toByteArray(), (System.nanoTime() - start) / 1_000_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            in.sendResponseHeaders(502, -1);
        } catch (IOException e) {
            // Upstream unreachable: tell the client, record nothing invented.
            try {
                in.sendResponseHeaders(502, -1);
            } catch (IOException ignored) {
                // client already gone
            }
        } finally {
            in.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

}
