package com.learning.clickstream.edge.http;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Writes the small JSON bodies. Event parsing lives in EventParser. */
public final class HttpResponses {
  private HttpResponses() {}

  public static void json(HttpServletResponse response, int status, String body) throws IOException {
    response.setStatus(status);
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json");
    // Tokens and errors must not be stored by a shared cache.
    response.setHeader("Cache-Control", "no-store");
    response.getWriter().write(body);
  }

  public static void error(HttpServletResponse response, int status, String code) throws IOException {
    json(response, status, "{\"error\":" + quote(code) + "}");
  }

  public static String quote(String value) {
    String escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
    return "\"" + escaped + "\"";
  }
}
