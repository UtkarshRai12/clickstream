package com.learning.clickstream.agent;

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
 * Posts spans to a self-hosted Langfuse as OTLP JSON. The keys are HTTP Basic
 * auth for that one endpoint. If any of the three values is blank, nothing is
 * sent and the report still works. A failed POST is logged. The row stays.
 */
public final class LangfuseExporter implements TraceExporter {
  static final String PATH = "/api/public/otel/v1/traces";
  private static final Duration TIMEOUT = Duration.ofSeconds(2);
  private static final Logger log = LoggerFactory.getLogger(LangfuseExporter.class);

  private final URI endpoint;
  private final String authorization;
  private final HttpClient client;
  private final ObjectMapper json = new ObjectMapper();

  private LangfuseExporter(String host, String publicKey, String secretKey) {
    this.endpoint = URI.create(trimSlash(host) + PATH);
    String pair = publicKey + ":" + secretKey;
    this.authorization = "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
    this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  }

  /** Reads LANGFUSE_HOST, LANGFUSE_PUBLIC_KEY, and LANGFUSE_SECRET_KEY. */
  public static TraceExporter fromEnv(Map<String, String> env) {
    return create(env.get("LANGFUSE_HOST"), env.get("LANGFUSE_PUBLIC_KEY"), env.get("LANGFUSE_SECRET_KEY"));
  }

  /** A blank value gives an exporter that drops every batch without opening a socket. */
  public static TraceExporter create(String host, String publicKey, String secretKey) {
    if (blank(host) || blank(publicKey) || blank(secretKey)) {
      log.info("langfuse keys are blank, traces are not exported");
      return spans -> { };
    }
    return new LangfuseExporter(host, publicKey, secretKey);
  }

  @Override
  public void export(List<Observation> spans) {
    try {
      HttpRequest request = HttpRequest.newBuilder(endpoint)
          .timeout(TIMEOUT)
          .header("Content-Type", "application/json")
          .header("Authorization", authorization)
          // Without this header Langfuse treats the batch as the older ingestion format.
          .header("x-langfuse-ingestion-version", "4")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(OtlpJson.body(spans))))
          .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.warn("langfuse rejected the trace status={} body={}", response.statusCode(), response.body());
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      log.warn("langfuse export interrupted");
    } catch (IOException ex) {
      log.warn("langfuse export failed: {}", ex.toString());
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String trimSlash(String host) {
    String trimmed = host.trim();
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }
}
