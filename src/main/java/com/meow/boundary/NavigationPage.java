package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class NavigationPage {
  protected final WorkflowContext context;
  private final com.meow.control.LogoutController logout;

  public NavigationPage(WorkflowContext context, com.meow.control.LogoutController logout) {
    this.context = context;
    this.logout = logout;
  }

  public void clickLogout() {
    context.call("NavigationPage.clickLogout");
    if (logout.logout(context.state().selectedId)) {  // step 2
      displayMsg("Logged out successfully");  // step 4
      redirectToLogin();  // step 5
    }
  }

  public void redirectToLogin() {
    context.call("NavigationPage.redirectToLogin");
    context.state().output.put("redirect", "login");
  }

  public void displayMsg(String message) {
    context.call("NavigationPage.displayMsg");
    context.message("success", message);
  }
}
