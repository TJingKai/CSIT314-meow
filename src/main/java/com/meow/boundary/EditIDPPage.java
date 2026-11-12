package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class EditIDPPage {
  protected final WorkflowContext context;
  private final com.meow.control.EditIDPController edit;

  public EditIDPPage(WorkflowContext context, com.meow.control.EditIDPController edit) {
    this.context = context;
    this.edit = edit;
  }

  public void saveChanges(
      String idpId, String title, String description, String projectDetails, String status) {
    context.call("EditIDPPage.saveChanges");  // step 1
    if (edit.editIDP(idpId, title, description, projectDetails, status))  // step 2
      displayMessage("IDP updated successfully");  // step 4
    else throw new ApiException("IDP could not be updated.");
  }

  public void displayMessage(String message) {
    context.call("EditIDPPage.displayMessage");
    context.message("success", message);
  }
}
