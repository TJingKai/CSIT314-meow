package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class RegistrationPage {
  protected final WorkflowContext context;
  private final com.meow.control.FormValidator validator;
  private final com.meow.control.RegistrationController registration;

  public RegistrationPage(
      WorkflowContext context,
      com.meow.control.FormValidator validator,
      com.meow.control.RegistrationController registration) {
    this.context = context;
    this.validator = validator;
    this.registration = registration;
  }

  public void navigateToRegister() {
    context.call("RegistrationPage.navigateToRegister");  // step 1
    displayForm();  // step 2
  }

  public void displayForm() {
    context.call("RegistrationPage.displayForm");
    context.state().output.put("form", "customerRegistration");
  }

  public void enterDetails(String email, String pwd, String confirmPwd) {
    context.call("RegistrationPage.enterDetails");  // step 3
    boolean valid = validator.validate(email, pwd, confirmPwd);  // step 4
    if (valid) enableSubmit();  // step 7a
    else if (email == null || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      displayError("Invalid email format");  // step 7b
    else displayError("Passwords do not match");  // step 7c
  }

  public void enableSubmit() {
    context.call("RegistrationPage.enableSubmit");
    context.state().output.put("submitEnabled", true);
  }

  public void clickSubmit() {
    context.call("RegistrationPage.clickSubmit");  // step 1
    boolean saved = registration.createAccount(context.value("email"), context.value("password"));  // step 2
    if (saved) displaySuccessMsg("Account created successfully");  // step 6a
    else {
      displayError("Email is already registered");  // step 4b
      context.state().status = 409;
    }
  }

  public void displayError(String message) {
    context.call("RegistrationPage.displayError");
    context.message("error", message);
  }

  public void displaySuccessMsg(String message) {
    context.call("RegistrationPage.displaySuccessMsg");
    context.message("success", message);
  }
}
