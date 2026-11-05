package com.learning.clickstream.agent.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.learning.clickstream.agent.ChatMessage;
import com.learning.clickstream.agent.IncidentService;
import com.learning.clickstream.agent.StoredReport;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

/** The report list, one open report, and a follow-up question. */
public final class ReportsServlet extends HttpServlet {
  static final int QUESTION_CAP = 4_000;

  private final IncidentService service;

  public ReportsServlet(IncidentService service) {
    this.service = service;
  }

  @Override
  protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String info = request.getPathInfo();
    if (info == null || info.isEmpty() || "/".equals(info)) {
      if ("GET".equals(request.getMethod())) {
        Json.write(response, 200, service.list());
        return;
      }
      Json.error(response, 405, "method_not_allowed");
      return;
    }
    String[] parts = info.substring(1).split("/");
    if (parts.length == 1 && "GET".equals(request.getMethod())) {
      open(response, parts[0]);
      return;
    }
    if (parts.length == 2 && "messages".equals(parts[1]) && "POST".equals(request.getMethod())) {
      ask(request, response, parts[0]);
      return;
    }
    Json.error(response, 404, "not_found");
  }

  private void open(HttpServletResponse response, String id) throws IOException {
    Optional<StoredReport> report = service.find(id);
    if (report.isEmpty()) {
      Json.error(response, 404, "not_found");
      return;
    }
    Json.write(response, 200, report.get());
  }

  private void ask(HttpServletRequest request, HttpServletResponse response, String id) throws IOException {
    QuestionBody body;
    try {
      body = Json.read(request, QuestionBody.class);
    } catch (JsonProcessingException ex) {
      Json.error(response, 400, "bad_question");
      return;
    }
    String question = body.question();
    if (question == null || question.isBlank()) {
      Json.error(response, 400, "empty_question");
      return;
    }
    if (question.length() > QUESTION_CAP) {
      Json.error(response, 400, "question_too_long");
      return;
    }
    Optional<ChatMessage> answer = service.followUp(id, question);
    if (answer.isEmpty()) {
      Json.error(response, 404, "not_found");
      return;
    }
    Json.write(response, 200, answer.get());
  }
}
