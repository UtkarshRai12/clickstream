package com.learning.clickstream.edge.http;

import com.learning.clickstream.edge.ingest.IngestRejection;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** JSON for ingest failures. Includes the batch index when one item is the problem. */
public final class IngestResponses {
  private IngestResponses() {}

  public static void write(HttpServletResponse response, IngestRejection rejection) throws IOException {
    StringBuilder json = new StringBuilder();
    json.append("{\"error\":").append(HttpResponses.quote(rejection.getCode()));
    if (rejection.getIndex() != null) {
      json.append(",\"index\":").append(rejection.getIndex());
    }
    if (rejection.getReason() != null) {
      json.append(",\"reason\":").append(HttpResponses.quote(rejection.getReason()));
    }
    json.append('}');
    HttpResponses.json(response, rejection.getStatus(), json.toString());
  }
}
