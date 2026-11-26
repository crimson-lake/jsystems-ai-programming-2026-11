package com.hsdc.copilot.capture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes capture files to {@code src/test/resources/openrouter-captures/}:
 * <ul>
 *   <li>{@code <name>.json} or {@code <name>.sse}: the response body, byte for byte;</li>
 *   <li>{@code <name>.request.json}: the request body (image base64 elided, no secrets);</li>
 *   <li>{@code <name>.meta.json}: date, endpoint, model, request summary, status, response headers.</li>
 * </ul>
 */
final class CaptureFiles {

    static final Path DIR = Path.of("src/test/resources/openrouter-captures");
    static final Path IMAGES = Path.of("../../assets/example-images");

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final Pattern DATA_URL = Pattern.compile("(data:image/[a-z+.-]+;base64,)[A-Za-z0-9+/=]{64,}");
    private static final Pattern DATA_BASE64 = Pattern.compile("(\"data_base64\"\\s*:\\s*\")[A-Za-z0-9+/=]{64,}(\")");

    private CaptureFiles() {
    }

    static byte[] image(String fileName) throws IOException {
        return Files.readAllBytes(IMAGES.resolve(fileName));
    }

    /**
     * The photo as the app uploads it: the browser re-encodes to JPEG with the longest edge at most 1568 px
     * (ADR-000 D-06, within Jev's 256-1568 px per side). Photos already within the limit are re-encoded only.
     */
    static byte[] reducedJpeg(String fileName) throws IOException {
        java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(IMAGES.resolve(fileName).toFile());
        double scale = Math.min(1.0, 1568.0 / Math.max(src.getWidth(), src.getHeight()));
        int w = (int) Math.round(src.getWidth() * scale);
        int h = (int) Math.round(src.getHeight() * scale);
        java.awt.image.BufferedImage dst = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = dst.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.85f);
        try (javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(dst, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** Data URL with the media type detected from the magic bytes (file names can lie, see the images README). */
    static String dataUrl(byte[] bytes) {
        return "data:" + mediaType(bytes) + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    static String mediaType(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (b.length > 8 && b[0] == (byte) 0x89 && b[1] == 'P') {
            return "image/png";
        }
        if (b.length > 12 && b[0] == 'R' && b[8] == 'W' && b[9] == 'E') {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    static void write(String name, String description, String model, String requestSummary,
            RecordingProxy.Exchange ex, String upstreamOrigin) throws IOException {
        Files.createDirectories(DIR);
        String contentType = ex.responseHeaders().getOrDefault("content-type", "");
        String ext = contentType.contains("text/event-stream") ? "sse" : "json";
        Files.write(DIR.resolve(name + "." + ext), ex.responseBody());
        Files.writeString(DIR.resolve(name + ".request.json"), elide(new String(ex.requestBody(), StandardCharsets.UTF_8)));

        ObjectNode meta = JSON.createObjectNode();
        meta.put("name", name);
        meta.put("description", description);
        meta.put("capturedAt", Instant.now().toString());
        meta.put("endpoint", ex.method() + " " + upstreamOrigin + ex.path());
        meta.put("model", model);
        meta.put("requestSummary", requestSummary);
        meta.put("httpStatus", ex.status());
        meta.put("latencyMs", ex.latencyMs());
        meta.put("bodyFile", name + "." + ext);
        meta.put("requestFile", name + ".request.json");
        meta.put("note", "Authorization, Cookie and Set-Cookie headers are not stored. Body file is verbatim.");
        ObjectNode reqH = meta.putObject("requestHeaders");
        ex.requestHeaders().forEach(reqH::put);
        ObjectNode respH = meta.putObject("responseHeaders");
        ex.responseHeaders().forEach(respH::put);
        Files.writeString(DIR.resolve(name + ".meta.json"),
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(meta) + "\n");
    }

    /** Same as {@link #write} for exchanges made without the proxy (plain HttpClient). */
    static void writeDirect(String name, String description, String model, String requestSummary,
            String method, String url, Map<String, String> requestHeaders, String requestBody,
            int status, Map<String, String> responseHeaders, byte[] responseBody, long latencyMs) throws IOException {
        java.net.URI uri = java.net.URI.create(url);
        String origin = uri.getScheme() + "://" + uri.getHost();
        String path = uri.getRawPath();
        write(name, description, model, requestSummary, new RecordingProxy.Exchange(method, path, requestHeaders,
                requestBody.getBytes(StandardCharsets.UTF_8), status, responseHeaders, responseBody, latencyMs), origin);
    }

    static String elide(String body) {
        Matcher m = DATA_URL.matcher(body);
        String out = m.replaceAll(r -> r.group(1) + "<base64 elided>");
        return DATA_BASE64.matcher(out).replaceAll(r -> r.group(1) + "<base64 elided>" + r.group(2));
    }
}
