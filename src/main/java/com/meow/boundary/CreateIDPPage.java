package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class CreateIDPPage {
  protected final WorkflowContext context;
  private final com.meow.control.CreateIDPController create;

  public CreateIDPPage(WorkflowContext context, com.meow.control.CreateIDPController create) {
    this.context = context;
    this.create = create;
  }

  public void submitIDP(String title, String description, String projectDetails, String status) {
    context.call("CreateIDPPage.submitIDP");
    if (create.createIDP(context.value("designerId"), title, description, projectDetails, status))
      displayMessage("IDP created successfully");
    else throw new ApiException("IDP could not be saved.");
  }

  public void displayMessage(String message) {
    context.call("CreateIDPPage.displayMessage");
    context.message("success", message);
  }
}
