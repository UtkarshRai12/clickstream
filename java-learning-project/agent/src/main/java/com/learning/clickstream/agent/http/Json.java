package com.learning.clickstream.agent.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/** JSON for the loopback API. Instants are ISO-8601, matching the stored snapshot. */
final class Json {
  static final ObjectMapper MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private Json() {}

  static void write(HttpServletResponse response, int status, Object body) throws IOException {
    response.setStatus(status);
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-store");
    MAPPER.writeValue(response.getWriter(), body);
  }

  static <T> T read(HttpServletRequest request, Class<T> type) throws IOException {
    return MAPPER.readValue(request.getInputStream(), type);
  }

  static void error(HttpServletResponse response, int status, String code) throws IOException {
    write(response, status, Map.of("error", code));
  }
}
