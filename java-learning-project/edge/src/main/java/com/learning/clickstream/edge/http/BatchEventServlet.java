package com.learning.clickstream.edge.http;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.learning.clickstream.edge.auth.Caller;
import com.learning.clickstream.edge.ingest.EventParser;
import com.learning.clickstream.edge.ingest.IngestAcceptance;
import com.learning.clickstream.edge.ingest.IngestRejection;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;

/**
 * POST /v1/events:batch. Body is {"events":[ ...]}.
 * One bad item rejects the whole batch with that item's index. Nothing is queued.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class BatchEventServlet extends HttpServlet {
  private final EventParser parser;
  private final IngestAcceptance acceptance;
  private final Provider<Caller> caller;

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    try {
      acceptance.accept(caller.get(), requestId(request), response, parser.parseBatch(Bodies.read(request)));
    } catch (IngestRejection rejection) {
      IngestResponses.write(response, rejection);
    }
  }

  private static String requestId(HttpServletRequest request) {
    Object value = request.getAttribute(RequestIds.ATTRIBUTE);
    return value == null ? "" : value.toString();
  }
}
