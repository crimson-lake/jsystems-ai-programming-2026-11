package com.hsdc.copilot.eval;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.hsdc.copilot.cases.CaseType;
import com.hsdc.copilot.cases.Category;
import com.hsdc.copilot.decision.CaseFacts;
import com.hsdc.copilot.decision.SafetyLexicon;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Case loading, request building and SSE parsing of the T43 eval suite (no Spring, no network). */
final class EvalSupport {

	static final JsonMapper MAPPER = JsonMapper.builder().build();
	static final Path CASES_DIR = Path.of("../evals/cases");
	static final Path IMAGES = Path.of("../../assets/example-images");
	static final Set<String> PHOTOS = Set.of("laptop-1.png", "laptop-2.webp", "phone-1.jpg", "phone-2.jpeg",
			"phone-3.jpeg");
	static final Set<String> EXPECTATIONS = Set.of("windowRejectOrSafety", "mentionsManufacturerWarranty",
			"safetyEscalation", "injection", "polishReplyWithDate");
	private static final Set<String> TOP_KEYS = Set.of("id", "description", "caseType", "category", "model",
			"purchaseDaysAgo", "purchaseYearsAgo", "reason", "photo", "chatFollowUp", "expect");

	private EvalSupport() {
	}

	/** One synthetic eval case; {@code purchaseDate} is computed relative to today in the application zone. */
	record EvalCase(String id, String description, CaseType caseType, Category category, String model,
			LocalDate purchaseDate, String reason, String photo, String chatFollowUp, Set<String> expect) {

		boolean expects(String key) {
			return expect.contains(key);
		}

		String formJson() {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("caseType", caseType.name());
			map.put("category", category.name());
			map.put("model", model);
			map.put("purchaseDate", purchaseDate.toString());
			map.put("reason", reason);
			return MAPPER.writeValueAsString(map);
		}
	}

	static List<EvalCase> loadCases(Path dir, Clock clock) throws IOException {
		LocalDate today = LocalDate.now(clock);
		List<Path> files;
		try (Stream<Path> s = Files.list(dir)) {
			files = s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
		}
		List<EvalCase> cases = new ArrayList<>();
		Set<String> ids = new LinkedHashSet<>();
		for (Path file : files) {
			EvalCase c = parse(file, today, clock);
			if (!ids.add(c.id())) {
				throw new IllegalStateException("duplicate case id " + c.id());
			}
			cases.add(c);
		}
		if (cases.isEmpty()) {
			throw new IllegalStateException("no case files in " + dir.toAbsolutePath());
		}
		// every example photo needs at least one record-only (no expectations) case
		for (String photo : PHOTOS) {
			if (cases.stream().noneMatch(c -> c.photo().equals(photo) && c.expect().isEmpty())) {
				throw new IllegalStateException("no record-only case for photo " + photo);
			}
		}
		return cases;
	}

	private static EvalCase parse(Path file, LocalDate today, Clock clock) throws IOException {
		String where = file.getFileName().toString();
		JsonNode n = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
		for (String key : n.propertyNames()) {
			require(TOP_KEYS.contains(key), where, "unknown key " + key);
		}
		String id = text(n, "id", where);
		require(where.equals(id + ".json"), where, "file name must be <id>.json");
		text(n, "description", where);
		CaseType type = CaseType.valueOf(text(n, "caseType", where));
		Category category = Category.valueOf(text(n, "category", where));
		String model = text(n, "model", where);
		String reason = text(n, "reason", where);
		String photo = text(n, "photo", where);
		require(PHOTOS.contains(photo), where, "photo must be one of the example photos: " + photo);
		require(Files.exists(IMAGES.resolve(photo)), where, "missing example photo " + photo);
		require(n.has("purchaseDaysAgo") && n.path("purchaseDaysAgo").asInt(-1) >= 0, where, "purchaseDaysAgo >= 0");
		LocalDate date = today.minusYears(n.path("purchaseYearsAgo").asInt(0)).minusDays(n.path("purchaseDaysAgo").asInt());
		String follow = n.has("chatFollowUp") ? text(n, "chatFollowUp", where) : null;
		Set<String> expect = new LinkedHashSet<>();
		JsonNode e = n.path("expect");
		for (String key : e.propertyNames()) {
			require(EXPECTATIONS.contains(key), where, "unknown expectation " + key);
			if (e.path(key).asBoolean(false)) {
				expect.add(key);
			}
		}
		if (type == CaseType.COMPLAINT) {
			require(reason.strip().length() >= 20, where, "complaint reason needs >= 20 characters");
		}
		CaseFacts facts = CaseFacts.of(date, clock);
		boolean exceeded = type == CaseType.RETURN ? facts.returnWindowExceeded() : facts.complaintPeriodExceeded();
		if (expect.contains("windowRejectOrSafety")) {
			require(exceeded, where, "windowRejectOrSafety needs a purchase date outside the window");
		}
		if (expect.contains("mentionsManufacturerWarranty")) {
			require(type == CaseType.COMPLAINT && expect.contains("windowRejectOrSafety"), where,
					"warranty mention applies to out-of-period complaints");
		}
		require(expect.contains("safetyEscalation") == SafetyLexicon.matches(reason), where,
				"the reason matches the safety lexicon exactly when safetyEscalation is expected");
		if (expect.contains("polishReplyWithDate")) {
			require(follow != null, where, "polishReplyWithDate needs chatFollowUp");
		}
		require(!date.isAfter(today), where, "purchase date in the future");
		return new EvalCase(id, n.path("description").asString(), type, category, model, date, reason, photo, follow,
				expect);
	}

	private static String text(JsonNode n, String key, String where) {
		String v = n.path(key).asString("");
		require(!v.isBlank(), where, "missing " + key);
		return v;
	}

	private static void require(boolean ok, String where, String message) {
		if (!ok) {
			throw new IllegalStateException("invalid eval case " + where + ": " + message);
		}
	}

	// ---------------------------------------------------------------- photo (same reduction as the browser upload)

	/**
	 * The photo as the app uploads it: JPEG, longest edge at most 1568 px (copy of the logic of
	 * {@code capture.CaptureFiles.reducedJpeg}, ADR-000 D-06); formats the JDK cannot decode (WebP) are sent unchanged.
	 */
	static byte[] reducedJpeg(String fileName) throws IOException {
		java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(IMAGES.resolve(fileName).toFile());
		if (src == null) {
			// The JDK has no WebP decoder (laptop-2.webp, 863x843, already within 1568 px): send the original bytes,
			// the app accepts WebP by magic bytes (PhotoValidator).
			return Files.readAllBytes(IMAGES.resolve(fileName));
		}
		double scale = Math.min(1.0, 1568.0 / Math.max(src.getWidth(), src.getHeight()));
		int w = (int) Math.round(src.getWidth() * scale);
		int h = (int) Math.round(src.getHeight() * scale);
		java.awt.image.BufferedImage dst = new java.awt.image.BufferedImage(w, h,
				java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = dst.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
				java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		javax.imageio.ImageWriter writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next();
		javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(0.85f);
		try (javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(out)) {
			writer.setOutput(ios);
			writer.write(null, new javax.imageio.IIOImage(dst, null, null), param);
		}
		finally {
			writer.dispose();
		}
		return out.toByteArray();
	}

	// ---------------------------------------------------------------- multipart

	record Part(String name, String filename, String contentType, byte[] bytes) {

		static Part text(String name, String value) {
			return new Part(name, null, null, value.getBytes(StandardCharsets.UTF_8));
		}
	}

	static byte[] multipart(String boundary, List<Part> parts) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (Part p : parts) {
			StringBuilder head = new StringBuilder("--").append(boundary).append("\r\n")
				.append("Content-Disposition: form-data; name=\"").append(p.name()).append('"');
			if (p.filename() != null) {
				head.append("; filename=\"").append(p.filename()).append('"');
			}
			head.append("\r\n");
			if (p.contentType() != null) {
				head.append("Content-Type: ").append(p.contentType()).append("\r\n");
			}
			head.append("\r\n");
			out.writeBytes(head.toString().getBytes(StandardCharsets.UTF_8));
			out.writeBytes(p.bytes());
			out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
		}
		out.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		return out.toByteArray();
	}

	static List<Part> caseParts(EvalCase c, byte[] photo) {
		return List.of(new Part("form", "blob", "application/json", c.formJson().getBytes(StandardCharsets.UTF_8)),
				new Part("photo", "photo", mediaType(photo), photo));
	}

	static String mediaType(byte[] b) {
		if (b.length > 12 && b[0] == 'R' && b[8] == 'W' && b[9] == 'E') {
			return "image/webp";
		}
		return b.length > 8 && b[0] == (byte) 0x89 && b[1] == 'P' ? "image/png" : "image/jpeg";
	}

	static List<Part> messageParts(java.util.UUID clientMessageId, String text) {
		return List.of(Part.text("clientMessageId", clientMessageId.toString()), Part.text("text", text));
	}

	// ---------------------------------------------------------------- SSE

	record SseEvent(String name, JsonNode data) {
	}

	static List<SseEvent> parseSse(String body) {
		List<SseEvent> events = new ArrayList<>();
		for (String block : body.replace("\r\n", "\n").split("\n\n")) {
			String name = null;
			StringBuilder data = new StringBuilder();
			for (String line : block.split("\n")) {
				if (line.startsWith("event:")) {
					name = line.substring(6).trim();
				}
				else if (line.startsWith("data:")) {
					data.append(line.substring(5).stripLeading());
				}
			}
			if (name != null) {
				events.add(new SseEvent(name, data.isEmpty() ? null : MAPPER.readTree(data.toString())));
			}
		}
		return events;
	}
}
