package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class VerificationPage {
  protected final WorkflowContext context;
  private final com.meow.control.VerificationController verification;

  public VerificationPage(
      WorkflowContext context, com.meow.control.VerificationController verification) {
    this.context = context;
    this.verification = verification;
  }

  public void submitDocuments(String designerId) {
    context.call("VerificationPage.submitDocuments");  // step 1
    if (verification.processPending(designerId))  // step 2
      displayNotice("Account is pending verification. Publishing restricted.");  // step 5
    else displayError("Status update failed. Please retry submission.");
  }

  public void displayError(String message) {
    context.call("VerificationPage.displayError");
    context.message("error", message);
  }

  public void displayNotice(String message) {
    context.call("VerificationPage.displayNotice");
    context.message("notice", message);
  }
}
