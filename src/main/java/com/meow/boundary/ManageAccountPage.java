package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class ManageAccountPage {
  protected final WorkflowContext context;
  private final com.meow.control.AccountStatusController status;
  private final com.meow.control.DeleteAccountController delete;

  public ManageAccountPage(
      WorkflowContext context,
      com.meow.control.AccountStatusController status,
      com.meow.control.DeleteAccountController delete) {
    this.context = context;
    this.status = status;
    this.delete = delete;
  }

  public void viewAccountDetails(String userId) {
    context.call("ManageAccountPage.viewAccountDetails");  // #229 step 1, #230 step 1
    context.state().selectedId = userId;
  }

  public void clickUpdateStatus(String userId, String targetStatus) {
    context.call("ManageAccountPage.clickUpdateStatus");  // step 2
    if (status.updateStatus(userId, targetStatus)) {  // step 3
      displaySuccessMsg("Account status updated successfully");  // step 5a
      refreshView();  // step 6a
    } else displayError("Failed to update account status");
  }

  public void clickDelete(String userId) {
    context.call("ManageAccountPage.clickDelete");  // step 2
    context.state().selectedId = userId;
    showConfirmationPrompt();  // step 3
  }

  public void showConfirmationPrompt() {
    context.call("ManageAccountPage.showConfirmationPrompt");
    context.state().output.put("confirmationRequired", true);
  }

  public void cancel() {
    context.call("ManageAccountPage.cancel");  // step 4a
    context.state().output.put("cancelled", true);
    refreshView();  // step 5a
  }

  public void confirmDelete() {
    context.call("ManageAccountPage.confirmDelete");  // step 4b, 4c
    if (delete.deleteUser(context.state().selectedId)) {  // step 5b, 5c
      displaySuccessMsg("Account deleted successfully");  // step 7c
      refreshView();  // step 8c
    } else displayError("Failed to delete account");
  }

  public void refreshView() {
    context.call("ManageAccountPage.refreshView");
    context.state().output.put("refresh", true);
  }

  public void displayError(String message) {
    context.call("ManageAccountPage.displayError");
    context.message("error", message);
  }

  public void displaySuccessMsg(String message) {
    context.call("ManageAccountPage.displaySuccessMsg");
    context.message("success", message);
  }
}
