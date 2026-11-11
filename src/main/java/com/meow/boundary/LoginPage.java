package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class LoginPage {
  protected final WorkflowContext context;
  private final com.meow.control.LoginController login;

  public LoginPage(WorkflowContext context, com.meow.control.LoginController login) {
    this.context = context;
    this.login = login;
  }

  public void navigateToLoginPage() {
    context.call("LoginPage.navigateToLoginPage");
    displayLoginForm();  // step 2
  }

  public void displayLoginForm() {
    context.call("LoginPage.displayLoginForm");
    context.state().output.put("form", "login");
  }

  public void enterCredentials(String email, String pwd) {
    context.call("LoginPage.enterCredentials");
    context.state().input.put("email", email);
    context.state().input.put("password", pwd);
  }

  public void clickLogin() {
    context.call("LoginPage.clickLogin");
    if (login.login(context.value("email"), context.value("password")))  // step 5
      displayMsg("Login successful");  // step 7b
  }

  public void displayMsg(String message) {
    context.call("LoginPage.displayMsg");
    context.message("success", message);
  }
}
