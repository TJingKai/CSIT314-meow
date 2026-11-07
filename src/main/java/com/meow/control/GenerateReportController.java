package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GenerateReportController {
  protected final WorkflowContext context;
  private final com.meow.entity.ReportPeriod period;
  private final com.meow.entity.ActivityReport report;

  public GenerateReportController(
      WorkflowContext context,
      com.meow.entity.ReportPeriod period,
      com.meow.entity.ActivityReport report) {
    this.context = context;
    this.period = period;
    this.report = report;
  }

  public int[] generateTotals(String periodType, Date date) {
    context.call("GenerateReportController.generateTotals");
    if (!period.setPeriod(periodType, date)) throw new ApiException("Date cannot be in the future");
    Date[] range = period.calculateRange();
    int users = report.countNewUsers(range[0], range[1]);
    int idps = report.countNewIDPs(range[0], range[1]);
    int views = report.countViews(range[0], range[1]);
    int shortlists = report.countShortlists(range[0], range[1]);
    return new int[] {users, idps, views, shortlists};
  }
}
