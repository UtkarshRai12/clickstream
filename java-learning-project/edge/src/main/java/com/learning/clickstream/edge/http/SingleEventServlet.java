package com.learning.clickstream.edge.http;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.learning.clickstream.edge.auth.Caller;
import com.learning.clickstream.edge.ingest.EventParser;
import com.learning.clickstream.edge.ingest.IngestAcceptance;
import com.learning.clickstream.edge.ingest.IngestRejection;
import com.learning.clickstream.edge.ingest.IncomingEvent;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** POST /v1/events. One JSON object. A batch body is 400. The JWT filter already ran. */
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SingleEventServlet extends HttpServlet {
  private final EventParser parser;
  private final IngestAcceptance acceptance;
  private final Provider<Caller> caller;

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    try {
      IncomingEvent event = parser.parseOne(Bodies.read(request));
      acceptance.accept(caller.get(), requestId(request), response, List.of(event));
    } catch (IngestRejection rejection) {
      IngestResponses.write(response, rejection);
    }
  }

  private static String requestId(HttpServletRequest request) {
    Object value = request.getAttribute(RequestIds.ATTRIBUTE);
    return value == null ? "" : value.toString();
  }
}
