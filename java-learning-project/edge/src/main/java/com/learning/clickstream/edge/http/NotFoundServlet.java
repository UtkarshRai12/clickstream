package com.learning.clickstream.edge.http;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Jetty needs a servlet on /* or it never invokes GuiceFilter.
 * Guice serves the routes it knows and calls this servlet for everything else.
 * Jetty constructs it. Guice does not.
 */
public final class NotFoundServlet extends HttpServlet {
  @Override
  protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
    HttpResponses.error(response, 404, "not_found");
  }
}
