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
    context.call("ManageAccountPage.viewAccountDetails");
    context.state().selectedId = userId;
  }

  public void clickUpdateStatus(String userId, String targetStatus) {
    context.call("ManageAccountPage.clickUpdateStatus");
    if (status.updateStatus(userId, targetStatus)) {
      displaySuccessMsg("Account status updated successfully");
      refreshView();
    } else displayError("Failed to update account status");
  }

  public void clickDelete(String userId) {
    context.call("ManageAccountPage.clickDelete");
    context.state().selectedId = userId;
    showConfirmationPrompt();
  }

  public void showConfirmationPrompt() {
    context.call("ManageAccountPage.showConfirmationPrompt");
    context.state().output.put("confirmationRequired", true);
  }

  public void cancel() {
    context.call("ManageAccountPage.cancel");
    context.state().output.put("cancelled", true);
    refreshView();
  }

  public void confirmDelete() {
    context.call("ManageAccountPage.confirmDelete");
    if (delete.deleteUser(context.state().selectedId)) {
      displaySuccessMsg("Account deleted successfully");
      refreshView();
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
