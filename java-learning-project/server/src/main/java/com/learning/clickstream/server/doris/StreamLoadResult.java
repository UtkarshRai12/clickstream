package com.learning.clickstream.server.doris;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Doris returns HTTP 200 for a load that failed validation. The Status field
 * in the JSON body is the real result. This reads that one field. It is not
 * a general JSON parser.
 */
public final class StreamLoadResult {
  private final String status;
  private final String raw;

  private StreamLoadResult(String status, String raw) {
    this.status = status;
    this.raw = raw;
  }

  public static StreamLoadResult parse(int httpStatus, String body) {
    String raw = body == null ? "" : body;
    // The finished load uses "Status". The Frontend's early rejection uses "status".
    String status = stringField(raw, "Status");
    if (status.isEmpty()) {
      status = stringField(raw, "status");
    }
    return new StreamLoadResult(status, "HTTP " + httpStatus + " " + raw);
  }

  public boolean success() {
    return "Success".equalsIgnoreCase(status);
  }

  public String raw() {
    return raw;
  }

  static String stringField(String json, String name) {
    Matcher matcher = Pattern.compile(
            "\"" + Pattern.quote(name) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        .matcher(json);
    if (!matcher.find()) {
      return "";
    }
    return matcher.group(1);
  }
}
