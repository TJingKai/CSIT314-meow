package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class MyIDPPage {
  protected final WorkflowContext context;
  private final com.meow.control.PublishIDPController publish;

  public MyIDPPage(WorkflowContext context, com.meow.control.PublishIDPController publish) {
    this.context = context;
    this.publish = publish;
  }

  public void changePublishStatus(String idpId, String action) {
    context.call("MyIDPPage.changePublishStatus");
    if (publish.setPublishStatus(idpId, action))
      displayMessage(action.equals("Publish") ? "IDP published successfully" : "IDP unpublished");
    else throw new ApiException("Publication could not be updated.");
  }

  public void displayMessage(String message) {
    context.call("MyIDPPage.displayMessage");
    context.message("success", message);
  }
}
