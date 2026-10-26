package com.learning.clickstream.edge.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.learning.clickstream.edge.config.ApplicationConfig;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EventParserTest {
  private final EventParser parser = new EventParser(config(500));

  @Test
  void oneBadItemRejectsTheBatchAtThatIndex() {
    String body = """
        {"events":[
          {"event_id":"e1","user_id":"ada","event_type":"click","occurred_at":"2026-10-24T12:00:00Z"},
          {"event_id":"","user_id":"ada","event_type":"click","occurred_at":"2026-10-24T12:00:00Z"}
        ]}
        """;

    IngestRejection rejection = assertThrows(IngestRejection.class, () -> parser.parseBatch(body));

    assertEquals(400, rejection.getStatus());
    assertEquals(1, rejection.getIndex());
  }

  @Test
  void overTheCapIs413BeforeTheItemsAreAccepted() {
    StringBuilder body = new StringBuilder("{\"events\":[");
    for (int i = 0; i < 3; i++) {
      if (i > 0) {
        body.append(',');
      }
      body.append("{\"event_id\":\"e").append(i)
          .append("\",\"user_id\":\"ada\",\"event_type\":\"click\",\"occurred_at\":\"2026-10-24T12:00:00Z\"}");
    }
    body.append("]}");

    IngestRejection rejection = assertThrows(IngestRejection.class,
        () -> new EventParser(config(2)).parseBatch(body.toString()));

    assertEquals(413, rejection.getStatus());
    assertEquals("batch_too_large", rejection.getCode());
    assertTrue(rejection.getReason().contains("2"));
  }

  private static ApplicationConfig config(int batchCap) {
    return ApplicationConfig.builder()
        .httpPort(0)
        .jwtHmacSecret("test-hmac-secret-must-be-32-bytes")
        .ingestTtlSeconds(900)
        .reportTtlSeconds(3600)
        .apiKeyFile(Path.of("unused"))
        .ingestBatchCap(batchCap)
        .build();
  }
}
