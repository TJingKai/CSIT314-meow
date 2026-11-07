package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class DesignerDashboardPage {
  protected final WorkflowContext context;
  private final com.meow.control.ViewTotalStatsController totals;
  private final com.meow.control.ViewIDPStatsController perIdp;

  public DesignerDashboardPage(
      WorkflowContext context,
      com.meow.control.ViewTotalStatsController totals,
      com.meow.control.ViewIDPStatsController perIdp) {
    this.context = context;
    this.totals = totals;
    this.perIdp = perIdp;
  }

  public void navigateToDashboard() {
    context.call("DesignerDashboardPage.navigateToDashboard");
    int[] stats = totals.getTotalStats(context.value("designerId"));
    if (stats == null) {
      displayMessage("You have not created any IDPs yet");
      displayTotalStats(0, 0);
    } else displayTotalStats(stats[0], stats[1]);
  }

  public void clickViewByIDP() {
    context.call("DesignerDashboardPage.clickViewByIDP");
    var stats = perIdp.getStatsPerIDP(context.value("designerId"));
    if (stats.isEmpty()) {
      context.state().output.put("projects", stats);
      displayMessage("No IDPs found");
    } else displayIDPStatsList(stats);
  }

  public void displayTotalStats(int views, int shortlists) {
    context.call("DesignerDashboardPage.displayTotalStats");
    context.state().output.put("totalViews", views);
    context.state().output.put("totalShortlists", shortlists);
  }

  public void displayIDPStatsList(List<Map<String, Object>> stats) {
    context.call("DesignerDashboardPage.displayIDPStatsList");
    context.state().output.put("projects", stats);
  }

  public void displayMessage(String message) {
    context.call("DesignerDashboardPage.displayMessage");
    context.message("notice", message);
  }
}
