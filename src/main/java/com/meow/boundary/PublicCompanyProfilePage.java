package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class PublicCompanyProfilePage {
  protected final WorkflowContext context;
  private final com.meow.control.ViewCompanyProfileController view;

  public PublicCompanyProfilePage(
      WorkflowContext context, com.meow.control.ViewCompanyProfileController view) {
    this.context = context;
    this.view = view;
  }

  public void viewPublicProfile(String designerId) {
    context.call("PublicCompanyProfilePage.viewPublicProfile");
    var details = view.getPublicProfile(designerId);
    if (details == null) {
      displayError("Designer profile not found");
      redirectToHome();
      context.state().status = 404;
    } else renderProfile(details);
  }

  public void renderProfile(Map<String, Object> details) {
    context.call("PublicCompanyProfilePage.renderProfile");
    context.state().output.put("company", details);
  }

  public void redirectToHome() {
    context.call("PublicCompanyProfilePage.redirectToHome");
    context.state().output.put("redirect", "home");
  }

  public void displayError(String message) {
    context.call("PublicCompanyProfilePage.displayError");
    context.message("error", message);
  }
}
