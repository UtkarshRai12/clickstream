package com.learning.clickstream.edge.ingest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.learning.clickstream.edge.config.ApplicationConfig;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Turns the HTTP JSON into events. A bad item fails the whole body.
 * The batch cap is applied here, before every item is validated, so a huge
 * array returns 413 without a per-item error.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class EventParser {
  private static final int MAX_ID_LENGTH = 64;
  private static final int MAX_PROPERTIES_LENGTH = 65_536;

  private final ApplicationConfig config;
  private final ObjectMapper mapper = new ObjectMapper();

  public IncomingEvent parseOne(String body) {
    JsonNode node = readObject(body);
    if (node.has("events")) {
      throw new IngestRejection(400, "invalid_event", 0, "single endpoint does not take a batch");
    }
    return one(node, 0);
  }

  public List<IncomingEvent> parseBatch(String body) {
    JsonNode node = readObject(body);
    JsonNode events = node.get("events");
    if (events == null || !events.isArray()) {
      throw new IngestRejection(400, "invalid_event", null, "events must be an array");
    }
    if (events.size() > config.getIngestBatchCap()) {
      throw new IngestRejection(413, "batch_too_large", null, "max " + config.getIngestBatchCap());
    }
    List<IncomingEvent> parsed = new ArrayList<>();
    int index = 0;
    for (JsonNode item : events) {
      parsed.add(one(item, index));
      index++;
    }
    return parsed;
  }

  private JsonNode readObject(String body) {
    if (body == null || body.isBlank()) {
      throw new IngestRejection(400, "invalid_json", null, "body is empty");
    }
    try {
      JsonNode node = mapper.readTree(body);
      if (node == null || !node.isObject()) {
        throw new IngestRejection(400, "invalid_json", null, "body must be a JSON object");
      }
      return node;
    } catch (JsonProcessingException ex) {
      throw new IngestRejection(400, "invalid_json", null, "body is not json");
    }
  }

  private IncomingEvent one(JsonNode node, int index) {
    if (node == null || !node.isObject()) {
      throw new IngestRejection(400, "invalid_event", index, "event must be an object");
    }
    String eventId = text(node, "event_id", index);
    String userId = text(node, "user_id", index);
    String eventType = text(node, "event_type", index);
    Instant occurredAt = time(node, index);
    String properties = properties(node, index);
    return new IncomingEvent(eventId, userId, eventType, occurredAt, properties);
  }

  private static String text(JsonNode node, String field, int index) {
    JsonNode value = node.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      throw new IngestRejection(400, "invalid_event", index, "missing " + field);
    }
    if (value.asText().length() > MAX_ID_LENGTH) {
      throw new IngestRejection(400, "invalid_event", index, field + " is too long");
    }
    return value.asText();
  }

  private static Instant time(JsonNode node, int index) {
    JsonNode value = node.get("occurred_at");
    if (value == null || !value.isTextual()) {
      throw new IngestRejection(400, "invalid_event", index, "missing occurred_at");
    }
    try {
      return Instant.parse(value.asText());
    } catch (DateTimeParseException ex) {
      throw new IngestRejection(400, "invalid_event", index, "occurred_at must be ISO-8601");
    }
  }

  private String properties(JsonNode node, int index) {
    JsonNode value = node.get("properties");
    if (value == null || value.isNull()) {
      return "{}";
    }
    if (!value.isObject()) {
      throw new IngestRejection(400, "invalid_event", index, "properties must be an object");
    }
    try {
      String json = mapper.writeValueAsString(value);
      if (json.length() > MAX_PROPERTIES_LENGTH) {
        throw new IngestRejection(400, "invalid_event", index, "properties is too long");
      }
      return json;
    } catch (JsonProcessingException ex) {
      throw new IngestRejection(400, "invalid_event", index, "properties must be an object");
    }
  }
}
