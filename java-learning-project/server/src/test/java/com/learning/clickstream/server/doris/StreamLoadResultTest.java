package com.learning.clickstream.server.doris;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StreamLoadResultTest {

  @Test
  void http200IsStillAFailureWhenDorisSaysSo() {
    StreamLoadResult result = StreamLoadResult.parse(200, "{\"Status\":\"Fail\",\"Message\":\"bad row\"}");
    assertFalse(result.success());
    assertTrue(result.raw().contains("bad row"));
  }

  @Test
  void successStatusIsAccepted() {
    StreamLoadResult result = StreamLoadResult.parse(200, "{\"Status\":\"Success\",\"NumberLoadedRows\":1}");
    assertTrue(result.success());
  }
}
