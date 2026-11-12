package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class ReportPage {
  protected final WorkflowContext context;
  private final com.meow.control.SelectPeriodController select;
  private final com.meow.control.GenerateReportController generate;
  private final com.meow.control.ViewReportTableController table;

  public ReportPage(
      WorkflowContext context,
      com.meow.control.SelectPeriodController select,
      com.meow.control.GenerateReportController generate,
      com.meow.control.ViewReportTableController table) {
    this.context = context;
    this.select = select;
    this.generate = generate;
    this.table = table;
  }

  public void navigateToReports() {
    context.call("ReportPage.navigateToReports");  // step 1
    displayPeriodOptions();  // step 2
  }

  public void displayPeriodOptions() {
    context.call("ReportPage.displayPeriodOptions");
    context.state().output.put("periodOptions", List.of("daily", "weekly", "monthly"));
  }

  public void selectPeriod(String periodType, Date date) {
    context.call("ReportPage.selectPeriod");  // step 3
    if (date == null) {
      displayError("Please select a date");
      return;
    }
    Date[] range = select.getPeriod(periodType, date);  // step 4
    if (range == null) displayError("Date cannot be in the future");  // step 6b
    else displayPeriod(range[0], range[1]);  // step 7a
  }

  public void displayPeriod(Date start, Date end) {
    context.call("ReportPage.displayPeriod");
    context.state().output.put("from", com.meow.entity.ReportPeriod.day(start).toString());
    context.state().output.put("to", com.meow.entity.ReportPeriod.day(end).toString());
  }

  public void clickGenerate(String periodType, Date date) {
    context.call("ReportPage.clickGenerate");  // step 1
    int[] totals = generate.generateTotals(periodType, date);  // step 2
    if (Arrays.stream(totals).allMatch(value -> value == 0))
      displayMessage("No activity in this period");  // step 9b
    displayTotals(totals[0], totals[1], totals[2], totals[3]);  // step 9a, 10b
  }

  public void displayTotals(int users, int idps, int views, int shortlists) {
    context.call("ReportPage.displayTotals");
    context
        .state()
        .output
        .put(
            "totals",
            Json.object(
                "registration", users, "project", idps, "view", views, "shortlist", shortlists));
  }

  public void clickViewAsTable() {
    context.call("ReportPage.clickViewAsTable");  // step 1
    var rows =
        table.getReportRows(  // step 2
            context.value("period"), com.meow.entity.ReportPeriod.parse(context.value("date")));
    if (rows.isEmpty()) {
      context.state().output.put("rows", rows);
      displayMessage("No activity in this period");  // step 6b
    } else displayReportTable(rows);  // step 7a
  }

  public void displayReportTable(List<Map<String, Object>> rows) {
    context.call("ReportPage.displayReportTable");
    context.state().output.put("rows", rows);
  }

  public void displayError(String message) {
    context.call("ReportPage.displayError");
    context.message("error", message);
  }

  public void displayMessage(String message) {
    context.call("ReportPage.displayMessage");
    context.message("notice", message);
  }
}
