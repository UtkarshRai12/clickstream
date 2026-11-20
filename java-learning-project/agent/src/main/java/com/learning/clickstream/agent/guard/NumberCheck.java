package com.learning.clickstream.agent.guard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.learning.clickstream.agent.IncidentSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every number in the model text has to exist in the snapshot. Integers must
 * match exactly. A decimal may be the snapshot value rounded to the number of
 * places the model wrote. 202 is always allowed, because the rules mention it.
 */
public final class NumberCheck {
  private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
  private static final BigDecimal ACCEPTED = new BigDecimal("202");
  private static final ObjectMapper JSON = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private NumberCheck() {}

  public static boolean passes(String text, IncidentSnapshot snapshot) {
    if (text == null || text.isBlank()) {
      return false;
    }
    List<BigDecimal> known = numbersIn(json(snapshot));
    known.add(ACCEPTED);
    for (BigDecimal found : numbersIn(text)) {
      if (!matches(found, known)) {
        return false;
      }
    }
    return true;
  }

  /** A decimal is checked against each known value rounded to the same scale. */
  private static boolean matches(BigDecimal found, List<BigDecimal> known) {
    for (BigDecimal value : known) {
      if (found.compareTo(value) == 0) {
        return true;
      }
      if (found.scale() > 0 && value.setScale(found.scale(), RoundingMode.HALF_UP).compareTo(found) == 0) {
        return true;
      }
    }
    return false;
  }

  private static List<BigDecimal> numbersIn(String text) {
    List<BigDecimal> out = new ArrayList<>();
    Matcher matcher = NUMBER.matcher(text);
    while (matcher.find()) {
      out.add(new BigDecimal(matcher.group()));
    }
    return out;
  }

  private static String json(IncidentSnapshot snapshot) {
    try {
      return JSON.writeValueAsString(snapshot);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
