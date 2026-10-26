package com.learning.clickstream.server.doris;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

class StreamLoadRedirectTest {

  @Test
  void containerAddressIsRewrittenOntoThePublishedBackend() {
    URI location = URI.create("http://172.17.0.2:8040/api/clickstream/events/_stream_load?redirected=true");
    URI backend = URI.create("http://127.0.0.1:8040");

    URI rewritten = StreamLoadRedirect.toBackend(location, backend);

    assertEquals("127.0.0.1", rewritten.getHost());
    assertEquals(8040, rewritten.getPort());
    assertEquals("/api/clickstream/events/_stream_load", rewritten.getPath());
    assertEquals("redirected=true", rewritten.getQuery());
  }
}
