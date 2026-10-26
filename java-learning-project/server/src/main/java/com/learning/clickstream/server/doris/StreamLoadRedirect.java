package com.learning.clickstream.server.doris;

import java.net.URI;

/**
 * The Frontend accepts a stream load and answers with a redirect to the Backend.
 * Inside the all-in-one container that Location host is often an address the
 * laptop cannot resolve. We keep the path and query, and send the body to the
 * Backend host port that Docker published (127.0.0.1:8040).
 */
public final class StreamLoadRedirect {
  private StreamLoadRedirect() {}

  public static URI toBackend(URI location, URI backendBase) {
    try {
      return new URI(
          backendBase.getScheme(),
          null,
          backendBase.getHost(),
          backendBase.getPort(),
          location.getPath(),
          location.getQuery(),
          null);
    } catch (Exception ex) {
      throw new DorisAccessException("could not rewrite stream load redirect " + location, ex);
    }
  }
}
