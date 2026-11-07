package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class ReviewDesignerPage {
  protected final WorkflowContext context;
  private final com.meow.control.DesignerApprovalController approval;

  public ReviewDesignerPage(
      WorkflowContext context, com.meow.control.DesignerApprovalController approval) {
    this.context = context;
    this.approval = approval;
  }

  public void viewPendingApplication(String designerId) {
    context.call("ReviewDesignerPage.viewPendingApplication");
    context.state().selectedId = designerId;
    displayApplication(context.state().output);
  }

  public void displayApplication(Map<String, Object> details) {
    context.call("ReviewDesignerPage.displayApplication");
    context.state().output.put("application", new LinkedHashMap<>(details));
  }

  public void clickApprove(String designerId) {
    context.call("ReviewDesignerPage.clickApprove");
    if (approval.approveDesigner(designerId)) {
      displaySuccessMsg("Designer approved successfully");
      refreshQueue();
    } else displayError("Failed to update designer status. Please try again.");
  }

  public void clickReject(String designerId) {
    context.call("ReviewDesignerPage.clickReject");
    if (approval.rejectDesigner(designerId)) {
      displaySuccessMsg("Designer registration rejected");
      refreshQueue();
    } else displayError("Failed to update designer status. Please try again.");
  }

  public void refreshQueue() {
    context.call("ReviewDesignerPage.refreshQueue");
    context.state().output.put("refreshQueue", true);
  }

  public void displayError(String message) {
    context.call("ReviewDesignerPage.displayError");
    context.message("error", message);
  }

  public void displaySuccessMsg(String message) {
    context.call("ReviewDesignerPage.displaySuccessMsg");
    context.message("success", message);
  }
}
