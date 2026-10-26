package com.learning.clickstream.edge.auth;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Guice calls get() when a servlet asks for the Caller inside an active request.
 * The filter has already stored the verified caller on the request.
 * A singleton servlet cannot take Caller in its constructor: construction
 * happens once, at boot, when no request exists. Provider delays the read.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class CallerFromRequest implements Provider<Caller> {
  private final Provider<HttpServletRequest> requests;

  @Override
  public Caller get() {
    Object value = requests.get().getAttribute(Caller.ATTRIBUTE);
    if (!(value instanceof Caller caller)) {
      throw new IllegalStateException("no caller on this request");
    }
    return caller;
  }
}
