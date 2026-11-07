package com.meow.workflow;

import java.util.*;

/** Pure arithmetic shared by the two placements of calculateTotalRow in the report. */
public final class ReportMath {
  private ReportMath() {}

  public static List<Map<String, Object>> withTotal(List<Map<String, Object>> rows) {
    List<Map<String, Object>> result = new ArrayList<>(rows);
    Map<String, Object> total = new LinkedHashMap<>();
    total.put("date", "Total");
    for (String key : List.of("newUsers", "newIDPs", "views", "shortlists"))
      total.put(key, rows.stream().mapToLong(row -> ((Number) row.get(key)).longValue()).sum());
    result.add(total);
    return result;
  }
}
