package com.meow.repository;

import com.meow.api.ApiException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Parameterised JDBC access. Services own business rules and transaction boundaries. */
@Repository
public class Database {
  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
  private final JdbcTemplate jdbc;

  public Database(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> rows(String sql, Object... args) {
    return jdbc.queryForList(sql, args);
  }

  public Map<String, Object> find(String sql, Object... args) {
    List<Map<String, Object>> result = rows(sql, args);
    return result.isEmpty() ? null : result.get(0);
  }

  public Map<String, Object> required(String sql, Object... args) {
    Map<String, Object> value = find(sql, args);
    if (value == null) throw new ApiException(404, "not_found", "Resource not found.");
    return value;
  }

  public int update(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  public long count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  public static String id() {
    return UUID.randomUUID().toString();
  }

  public static String now() {
    return timestamp(Instant.now());
  }

  public static String timestamp(Instant instant) {
    return TIMESTAMP.format(instant);
  }

  public static String str(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value == null ? "" : value.toString();
  }

  public static boolean flag(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).intValue() == 1;
  }
}
