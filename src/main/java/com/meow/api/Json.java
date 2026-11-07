package com.meow.api;

import java.util.LinkedHashMap;
import java.util.Map;

/** Consistent, ordered JSON envelopes; map values may deliberately be null. */
public final class Json {
  private Json() {}

  public static Map<String, Object> object(Object... pairs) {
    Map<String, Object> value = new LinkedHashMap<>();
    for (int index = 0; index < pairs.length; index += 2) {
      value.put((String) pairs[index], pairs[index + 1]);
    }
    return value;
  }

  public static Map<String, Object> data(Object value) {
    return object("data", value);
  }
}
