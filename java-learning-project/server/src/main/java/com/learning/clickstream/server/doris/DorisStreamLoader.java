package com.learning.clickstream.server.doris;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * Writes JSON lines with Doris stream load.
 *
 * The request goes to the Frontend on 8030. One redirect hop is rewritten onto
 * the published Backend port. Java's HttpClient refuses to follow the redirect
 * itself and would drop the body, so we send the PUT a second time.
 *
 * Doris 4.1 rejects the load when Expect: 100-continue is missing. HttpClient
 * hides that header unless the JVM allow-list is set before the client starts.
 *
 * Phase 2 calls this from the probe. Phase 5 calls it from the ingest flusher.
 */
public final class DorisStreamLoader {
  static {
    // Read once, when java.net.http.HttpRequest initializes. Must run first.
    String allowed = System.getProperty("jdk.httpclient.allowRestrictedHeaders", "");
    if (!allowed.toLowerCase(Locale.ROOT).contains("expect")) {
      String value = allowed.isBlank() ? "expect" : allowed + ",expect";
      System.setProperty("jdk.httpclient.allowRestrictedHeaders", value);
    }
  }

  private final DorisConfig config;
  private final HttpClient http;

  public DorisStreamLoader(DorisConfig config) {
    this.config = config;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  public void loadLines(String ndjson, String label) {
    URI fe = URI.create(config.getFeHttp() + "/api/" + SchemaBootstrap.DATABASE + "/"
        + SchemaBootstrap.TABLE + "/_stream_load");
    HttpResponse<String> response = send(fe, ndjson, label);
    if (isRedirect(response.statusCode())) {
      String location = response.headers().firstValue("location")
          .orElseThrow(() -> new DorisAccessException("stream load redirect had no Location"));
      URI target = StreamLoadRedirect.toBackend(fe.resolve(location), URI.create(config.getBeHttp()));
      response = send(target, ndjson, label);
    }
    if (isRedirect(response.statusCode())) {
      throw new DorisAccessException("stream load redirected twice: " + response.headers());
    }
    StreamLoadResult result = StreamLoadResult.parse(response.statusCode(), response.body());
    if (!result.success()) {
      throw new DorisAccessException("stream load failed against " + config.getFeHttp() + ": " + result.raw());
    }
  }

  private HttpResponse<String> send(URI uri, String ndjson, String label) {
    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(30))
        .header("Authorization", basicAuth())
        .header("Expect", "100-continue")
        .header("Content-Type", "application/json")
        .header("format", "json")
        .header("read_json_by_line", "true")
        .header("strip_outer_array", "false")
        .header("max_filter_ratio", "0")
        .header("label", label)
        .PUT(HttpRequest.BodyPublishers.ofString(ndjson, StandardCharsets.UTF_8))
        .build();
    try {
      return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException ex) {
      throw new DorisAccessException("stream load request failed for " + uri, ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new DorisAccessException("stream load interrupted for " + uri, ex);
    }
  }

  private String basicAuth() {
    String raw = config.getUser() + ":" + config.getPassword();
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private static boolean isRedirect(int status) {
    return status == 301 || status == 302 || status == 307 || status == 308;
  }
}
