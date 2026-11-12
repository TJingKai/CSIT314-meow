package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class IDPCategoryPage {
  protected final WorkflowContext context;
  private final com.meow.control.AssignCategoriesController assign;

  public IDPCategoryPage(
      WorkflowContext context, com.meow.control.AssignCategoriesController assign) {
    this.context = context;
    this.assign = assign;
  }

  public void saveCategories(String idpId, List<String> categoryIds) {
    context.call("IDPCategoryPage.saveCategories");  // step 1
    int result = assign.assignCategories(idpId, categoryIds);  // step 2
    context.state().output.put("result", result);
    if (result == 0) {
      displayMessage("Select at least one category");  // step 3b
      context.state().status = 400;
    } else if (result == 1) displayMessage("Categories saved successfully");  // step 4a
    else throw new ApiException("Categories could not be saved.");
  }

  public void displayMessage(String message) {
    context.call("IDPCategoryPage.displayMessage");
    context.message("notice", message);
  }
}
