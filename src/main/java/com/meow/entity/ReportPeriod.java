package com.meow.entity;

import com.meow.api.ApiException;
import com.meow.workflow.WorkflowContext;
import java.time.*;
import java.util.Date;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class ReportPeriod {
  public static final ZoneId ZONE = ZoneId.of("Asia/Singapore");
  private final WorkflowContext context;
  private String type;
  private LocalDate selected;

  public ReportPeriod(WorkflowContext context) {
    this.context = context;
  }

  public boolean setPeriod(String periodType, Date date) {
    context.call("ReportPeriod.setPeriod");
    if (date == null) return false;
    type = periodType.toLowerCase(java.util.Locale.ROOT);
    if (!java.util.Set.of("daily", "weekly", "monthly").contains(type))
      throw new ApiException("Choose daily, weekly or monthly.");
    selected = date.toInstant().atZone(ZONE).toLocalDate();
    return !selected.isAfter(LocalDate.now(ZONE));
  }

  public Date[] calculateRange() {
    context.call("ReportPeriod.calculateRange");
    if (selected == null) throw new ApiException("Please select a date");
    LocalDate start = selected, end = selected;
    switch (type) {
      case "weekly" -> {
        start = selected.minusDays(selected.getDayOfWeek().getValue() - 1);
        end = start.plusDays(6);
      }
      case "monthly" -> {
        start = selected.withDayOfMonth(1);
        end = start.plusMonths(1).minusDays(1);
      }
      default -> {}
    }
    return new Date[] {
      Date.from(start.atStartOfDay(ZONE).toInstant()), Date.from(end.atStartOfDay(ZONE).toInstant())
    };
  }

  public static Date parse(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Date.from(LocalDate.parse(value).atStartOfDay(ZONE).toInstant());
    } catch (java.time.format.DateTimeParseException exception) {
      throw new ApiException("Use an ISO date such as 2026-11-06.");
    }
  }

  public static LocalDate day(Date value) {
    return value.toInstant().atZone(ZONE).toLocalDate();
  }
}
