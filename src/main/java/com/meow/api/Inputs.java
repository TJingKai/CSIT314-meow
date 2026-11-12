package com.meow.api;

import java.util.List;
import java.util.Map;

public final class Inputs {
  private Inputs() {}

  public static String text(Map<String, ?> data, String key, boolean required, int maximum) {
    Object raw = data.get(key);
    if (raw == null && !required) return "";
    if (!(raw instanceof String)) throw new ApiException(key + " must be text.");
    String value = ((String) raw).trim();
    if ((required && value.isEmpty()) || value.length() > maximum) {
      throw new ApiException(key + " is required and must be at most " + maximum + " characters.");
    }
    return value;
  }

  public static String text(Map<String, ?> data, String key) {
    return text(data, key, true, 150);
  }

  public static double number(Map<String, ?> data, String key, double minimum, double maximum) {
    Object raw = data.get(key);
    double value;
    try {
      if (!(raw instanceof Number || raw instanceof String)) throw new NumberFormatException();
      value = Double.parseDouble(raw.toString());
    } catch (NumberFormatException error) {
      throw new ApiException(key + " must be a number.");
    }
    if (!Double.isFinite(value) || value < minimum || value > maximum) {
      throw new ApiException(key + " must be between " + minimum + " and " + maximum + ".");
    }
    return value;
  }

  public static boolean bool(Map<String, ?> data, String key) {
    if (!(data.get(key) instanceof Boolean value))
      throw new ApiException(key + " must be true or false.");
    return value;
  }

  public static String email(Map<String, ?> data) {
    String value = text(data, "email", true, 254).toLowerCase(java.util.Locale.ROOT);
    if (!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new ApiException("Enter a valid email address.");
    return value;
  }

  public static String password(Map<String, ?> data, String key) {
    if (!(data.get(key) instanceof String value) || value.length() < 10 || value.length() > 128) {
      throw new ApiException("Password must be 10-128 characters.");
    }
    return (String) data.get(key);
  }

  public static List<String> strings(Map<String, ?> data, String key) {
    Object raw = data.get(key);
    if (!(raw instanceof List<?> list)
        || list.stream().anyMatch(value -> !(value instanceof String))) {
      throw new ApiException(key + " must be a list of IDs.");
    }
    return ((List<?>) raw).stream().map(String.class::cast).distinct().toList();
  }
}
