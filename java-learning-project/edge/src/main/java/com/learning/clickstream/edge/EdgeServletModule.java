package com.learning.clickstream.edge;

import com.google.inject.servlet.ServletModule;
import com.learning.clickstream.edge.auth.JwtAuthFilter;
import com.learning.clickstream.edge.http.BatchEventServlet;
import com.learning.clickstream.edge.http.MetricsFilter;
import com.learning.clickstream.edge.http.ReportServlet;
import com.learning.clickstream.edge.http.RequestIdFilter;
import com.learning.clickstream.edge.http.SingleEventServlet;
import com.learning.clickstream.edge.http.TokenServlet;

/**
 * Maps URLs to servlets and mounts filters in front of them.
 * MetricsFilter is first so it sees the status after every other filter returns.
 * RequestIdFilter is next so the id is on the request before auth and ingest.
 * The JWT filter is not mounted on /v1/token.
 */
public final class EdgeServletModule extends ServletModule {
  @Override
  protected void configureServlets() {
    filter("/*").through(MetricsFilter.class);
    filter("/*").through(RequestIdFilter.class);

    serve("/v1/token").with(TokenServlet.class);
    serve("/v1/reports/daily").with(ReportServlet.class);
    serve("/v1/events").with(SingleEventServlet.class);
    serve("/v1/events:batch").with(BatchEventServlet.class);

    filter("/v1/reports/*").through(JwtAuthFilter.class);
    filter("/v1/events").through(JwtAuthFilter.class);
    filter("/v1/events:batch").through(JwtAuthFilter.class);
  }
}
