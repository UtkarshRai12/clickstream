package com.learning.clickstream.edge.http;

/** Request attribute set by RequestIdFilter and copied onto the queued batch. */
public final class RequestIds {
  public static final String ATTRIBUTE = "clickstream.request_id";

  private RequestIds() {}
}
