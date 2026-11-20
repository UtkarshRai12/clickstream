package com.learning.clickstream.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The system rules, from Langfuse when it is up. The hardcoded text is the
 * fallback, and also the body used to seed the prompt the first time.
 * A failure is cached for the same minute as a success, so a down Langfuse
 * does not add a timeout to every report.
 */
public final class PromptSource {
  public static final String NAME = "ingress-report-rules";
  private static final String LABEL = "production";
  private static final Duration TIMEOUT = Duration.ofSeconds(2);
  private static final long CACHE_MS = 60_000;
  private static final Logger log = LoggerFactory.getLogger(PromptSource.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  private final URI promptUri;
  private final URI createUri;
  private final String authorization;
  private final HttpClient client;
  private final String fallbackText;
  private PromptText cached;
  private long expiresAt;

  private PromptSource(String host, String publicKey, String secretKey, String fallbackText) {
    this.fallbackText = fallbackText;
    if (host == null) {
      this.promptUri = null;
      this.createUri = null;
      this.authorization = null;
      this.client = null;
      return;
    }
    String base = trimSlash(host) + "/api/public/v2/prompts";
    // GET is the named prompt. POST creates on the collection. The name is in the body.
    this.promptUri = URI.create(base + "/" + NAME + "?label=" + LABEL);
    this.createUri = URI.create(base);
    String pair = publicKey + ":" + secretKey;
    this.authorization = "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    // Same HTTP/1.1 pin as LangfuseExporter. The default HTTP/2 upgrade gets
    // an empty close from the Langfuse web process.
    this.client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(TIMEOUT)
        .build();
  }

  /** No keys, or a unit test. The name and version stay empty, so the span is not tagged. */
  public static PromptSource hardcoded(String fallbackText) {
    return new PromptSource(null, null, null, fallbackText);
  }

  public static PromptSource fromEnv(Map<String, String> env, String fallbackText) {
    String host = env.get("LANGFUSE_HOST");
    String publicKey = env.get("LANGFUSE_PUBLIC_KEY");
    String secretKey = env.get("LANGFUSE_SECRET_KEY");
    if (blank(host) || blank(publicKey) || blank(secretKey)) {
      return hardcoded(fallbackText);
    }
    return new PromptSource(host, publicKey, secretKey, fallbackText);
  }

  public synchronized PromptText get() {
    long now = System.currentTimeMillis();
    if (cached != null && now < expiresAt) {
      return cached;
    }
    PromptText loaded = promptUri == null ? fallback() : load();
    cached = loaded;
    expiresAt = now + CACHE_MS;
    return loaded;
  }

  private PromptText load() {
    try {
      HttpResponse<String> got = send(promptUri, "GET", null);
      if (got.statusCode() == 200) {
        return parse(got.body());
      }
      // 404 is the only status that means "create it". A 401 is a bad key.
      if (got.statusCode() == 404) {
        HttpResponse<String> made = send(createUri, "POST", seedBody());
        if (made.statusCode() == 200 || made.statusCode() == 201) {
          return parse(made.body());
        }
        log.warn("langfuse did not seed the prompt status={}", made.statusCode());
      } else {
        log.warn("langfuse prompt status={}", got.statusCode());
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.warn("langfuse prompt load interrupted");
    } catch (IOException ex) {
      log.warn("langfuse prompt load failed: {}", ex.toString());
    }
    return fallback();
  }

  /** Text prompts return {@code prompt} as a string and {@code version} as a number. */
  private PromptText parse(String body) {
    try {
      JsonNode node = JSON.readTree(body);
      JsonNode prompt = node.get("prompt");
      if (prompt == null || !prompt.isTextual() || prompt.asText().isBlank()) {
        return fallback();
      }
      JsonNode version = node.get("version");
      Integer number = version != null && version.isNumber() ? version.intValue() : null;
      return new PromptText(prompt.asText(), NAME, number);
    } catch (IOException ex) {
      log.warn("langfuse prompt body was not json");
      return fallback();
    }
  }

  private String seedBody() throws IOException {
    // Shape from the public API: type "text", labels applied to this version.
    return JSON.writeValueAsString(Map.of(
        "name", NAME,
        "type", "text",
        "prompt", fallbackText,
        "labels", List.of(LABEL),
        "commitMessage", "Seeded by the ingress agent"));
  }

  private HttpResponse<String> send(URI uri, String method, String body) throws IOException, InterruptedException {
    HttpRequest.Builder request = HttpRequest.newBuilder(uri)
        .timeout(TIMEOUT)
        .header("Authorization", authorization);
    if (body == null) {
      request.GET();
    } else {
      request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private PromptText fallback() {
    return new PromptText(fallbackText, null, null);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String trimSlash(String host) {
    String trimmed = host.trim();
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }

  /** {@code name} is null when this is the hardcoded fallback. The span is then not linked. */
  public record PromptText(String text, String name, Integer version) {}
}
