package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class CreateAccountPage {
  protected final WorkflowContext context;
  private final com.meow.control.CreateAccountController create;

  public CreateAccountPage(
      WorkflowContext context, com.meow.control.CreateAccountController create) {
    this.context = context;
    this.create = create;
  }

  public void navigateToCreateAccount() {
    context.call("CreateAccountPage.navigateToCreateAccount");
    displayForm();
  }

  public void displayForm() {
    context.call("CreateAccountPage.displayForm");
    context.state().output.put("form", "createAccount");
  }

  public void enterAccountDetails(String email, String pwd, String confirmPwd, String role) {
    context.call("CreateAccountPage.enterAccountDetails");
    context
        .state()
        .input
        .putAll(
            Map.of("email", email, "password", pwd, "confirmPassword", confirmPwd, "role", role));
  }

  public void clickCreateAccount() {
    context.call("CreateAccountPage.clickCreateAccount");
    boolean saved =
        create.createAccount(
            context.value("email"),
            context.value("password"),
            context.value("confirmPassword"),
            context.value("role"));
    if (saved) displaySuccessMsg("Account created successfully");
    else if ("passwordMismatch".equals(context.state().output.get("failure")))
      displayError("Passwords do not match");
    else {
      displayError("Email is already in use");
      context.state().status = 409;
    }
  }

  public void displayError(String message) {
    context.call("CreateAccountPage.displayError");
    context.message("error", message);
  }

  public void displaySuccessMsg(String message) {
    context.call("CreateAccountPage.displaySuccessMsg");
    context.message("success", message);
  }
}
