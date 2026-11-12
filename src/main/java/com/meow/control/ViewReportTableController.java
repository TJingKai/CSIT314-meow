package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ViewReportTableController {
  protected final WorkflowContext context;
  private final com.meow.entity.ReportPeriod period;
  private final com.meow.entity.ActivityReport report;

  public ViewReportTableController(
      WorkflowContext context,
      com.meow.entity.ReportPeriod period,
      com.meow.entity.ActivityReport report) {
    this.context = context;
    this.period = period;
    this.report = report;
  }

  public List<Map<String, Object>> getReportRows(String periodType, Date date) {
    context.call("ViewReportTableController.getReportRows");
    if (!period.setPeriod(periodType, date)) throw new ApiException("Date cannot be in the future");  // step 3
    Date[] range = period.calculateRange();  // step 4
    List<Map<String, Object>> rows = report.getDailyRows(range[0], range[1]);  // step 5
    if (rows.isEmpty()) return rows;
    return calculateTotalRow(rows);  // step 6a
  }

  public List<Map<String, Object>> calculateTotalRow(List<Map<String, Object>> rows) {
    context.call("ViewReportTableController.calculateTotalRow");
    return ReportMath.withTotal(rows);
  }
}
