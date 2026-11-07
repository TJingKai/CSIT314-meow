package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class AppMenuView {
  protected final WorkflowContext context;
  private final com.meow.control.AccessController access;

  public AppMenuView(WorkflowContext context, com.meow.control.AccessController access) {
    this.context = context;
    this.access = access;
  }

  public void openNavigationMenu(String role) {
    context.call("AppMenuView.openNavigationMenu");
    var screens = access.getAuthorizedScreens(role);
    if (screens == null) {
      displayError("Access Denied: Unauthorized role");
      redirectToHome();
    } else renderMenu(screens);
  }

  public void renderMenu(List<String> screens) {
    context.call("AppMenuView.renderMenu");
    context.state().output.put("screens", screens);
  }

  public void redirectToHome() {
    context.call("AppMenuView.redirectToHome");
    context.state().output.put("redirect", "home");
  }

  public void displayError(String message) {
    context.call("AppMenuView.displayError");
    context.message("error", message);
  }
}
