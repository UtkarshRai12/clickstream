package com.learning.clickstream.edge;

import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;

/**
 * Jetty calls this when the context starts. The injector is built by EdgeServer
 * so the same graph can start the dispatcher after the port is open.
 */
public final class EdgeGuiceContext extends GuiceServletContextListener {
  private final Injector injector;

  public EdgeGuiceContext(Injector injector) {
    this.injector = injector;
  }

  @Override
  protected Injector getInjector() {
    return injector;
  }
}
