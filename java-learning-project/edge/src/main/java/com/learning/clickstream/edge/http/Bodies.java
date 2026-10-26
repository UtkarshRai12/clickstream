package com.learning.clickstream.edge.http;

import com.learning.clickstream.edge.ingest.IngestRejection;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Reader;

/** Reads the POST body with a hard cap so a huge payload never becomes a 202. */
public final class Bodies {
  public static final int MAX_BYTES = 1_000_000;

  private Bodies() {}

  public static String read(HttpServletRequest request) {
    if (request.getContentLength() > MAX_BYTES) {
      throw new IngestRejection(413, "body_too_large", null, "max " + MAX_BYTES + " bytes");
    }
    try {
      request.setCharacterEncoding("UTF-8");
      StringBuilder body = new StringBuilder();
      char[] buffer = new char[4096];
      int total = 0;
      try (Reader reader = request.getReader()) {
        int read;
        while ((read = reader.read(buffer)) >= 0) {
          total += read;
          if (total > MAX_BYTES) {
            throw new IngestRejection(413, "body_too_large", null, "max " + MAX_BYTES + " bytes");
          }
          body.append(buffer, 0, read);
        }
      }
      return body.toString();
    } catch (IOException ex) {
      throw new IngestRejection(400, "invalid_json", null, "could not read body");
    }
  }
}
