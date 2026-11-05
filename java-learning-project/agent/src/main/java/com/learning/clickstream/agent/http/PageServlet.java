package com.learning.clickstream.agent.http;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** GET /. One page, loaded from the classpath, with no session and no auth. */
public final class PageServlet extends HttpServlet {
  private final byte[] page;

  public PageServlet() {
    try (InputStream in = PageServlet.class.getResourceAsStream("/ui/index.html")) {
      if (in == null) {
        throw new IllegalStateException("missing ui/index.html");
      }
      page = in.readAllBytes();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setStatus(200);
    response.setCharacterEncoding("UTF-8");
    response.setContentType("text/html");
    response.setHeader("Cache-Control", "no-store");
    response.getOutputStream().write(page);
  }
}
