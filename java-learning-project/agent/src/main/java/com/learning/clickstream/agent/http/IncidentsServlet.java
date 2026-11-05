package com.learning.clickstream.agent.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.learning.clickstream.agent.IncidentService;
import com.learning.clickstream.agent.IncidentSnapshot;
import com.learning.clickstream.agent.StoredReport;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** POST /api/incidents. This is the only way a report is created. */
public final class IncidentsServlet extends HttpServlet {
  private final IncidentService service;

  public IncidentsServlet(IncidentService service) {
    this.service = service;
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    IncidentSnapshot snapshot;
    try {
      snapshot = Json.read(request, IncidentSnapshot.class);
    } catch (JsonProcessingException ex) {
      Json.error(response, 400, "bad_snapshot");
      return;
    }
    StoredReport report = service.open(snapshot);
    Json.write(response, 201, report);
  }
}
