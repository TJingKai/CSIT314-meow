package com.meow.entity;

import com.meow.repository.Database;
import com.meow.service.Identity;
import com.meow.workflow.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class ActivityReport extends DiagramEntity {
  public ActivityReport(Database db, Identity identity, WorkflowContext context) {
    super(db, identity, context);
  }

  private String start(Date date) {
    return Database.timestamp(ReportPeriod.day(date).atStartOfDay(ReportPeriod.ZONE).toInstant());
  }

  private String end(Date date) {
    return Database.timestamp(
        ReportPeriod.day(date).plusDays(1).atStartOfDay(ReportPeriod.ZONE).toInstant());
  }

  public int countNewUsers(Date start, Date end) {
    mark("ActivityReport.countNewUsers");
    return (int)
        db.count(
            "SELECT COUNT(*) FROM users WHERE created_at>=? AND created_at<?",
            start(start),
            end(end));
  }

  public int countNewIDPs(Date start, Date end) {
    mark("ActivityReport.countNewIDPs");
    return (int)
        db.count(
            "SELECT COUNT(*) FROM projects WHERE published_at>=? AND published_at<?",
            start(start),
            end(end));
  }

  public int countViews(Date start, Date end) {
    mark("ActivityReport.countViews");
    return (int)
        db.count(
            "SELECT COUNT(*) FROM events WHERE type='view' AND created_at>=? AND created_at<?",
            start(start),
            end(end));
  }

  public int countShortlists(Date start, Date end) {
    mark("ActivityReport.countShortlists");
    return (int)
        db.count(
            "SELECT COUNT(*) FROM events WHERE type='shortlist' AND created_at>=? AND created_at<?",
            start(start),
            end(end));
  }

  public List<Map<String, Object>> getDailyRows(Date start, Date end) {
    mark("ActivityReport.getDailyRows");
    List<Map<String, Object>> rows = new ArrayList<>();
    boolean any = false;
    for (LocalDate day = ReportPeriod.day(start);
        !day.isAfter(ReportPeriod.day(end));
        day = day.plusDays(1)) {
      String from = Database.timestamp(day.atStartOfDay(ReportPeriod.ZONE).toInstant()),
          to = Database.timestamp(day.plusDays(1).atStartOfDay(ReportPeriod.ZONE).toInstant());
      long
          users =
              db.count("SELECT COUNT(*) FROM users WHERE created_at>=? AND created_at<?", from, to),
          idps =
              db.count(
                  "SELECT COUNT(*) FROM projects WHERE published_at>=? AND published_at<?",
                  from,
                  to),
          views =
              db.count(
                  "SELECT COUNT(*) FROM events WHERE type='view' AND created_at>=? AND created_at<?",
                  from,
                  to),
          shortlists =
              db.count(
                  "SELECT COUNT(*) FROM events WHERE type='shortlist' AND created_at>=? AND created_at<?",
                  from,
                  to);
      any |= users + idps + views + shortlists > 0;
      rows.add(
          com.meow.api.Json.object(
              "date",
              day.toString(),
              "newUsers",
              users,
              "newIDPs",
              idps,
              "views",
              views,
              "shortlists",
              shortlists));
    }
    return any ? rows : List.of();
  }

  public List<Map<String, Object>> calculateTotalRow(List<Map<String, Object>> rows) {
    mark("ActivityReport.calculateTotalRow");
    return ReportMath.withTotal(rows);
  }
}
