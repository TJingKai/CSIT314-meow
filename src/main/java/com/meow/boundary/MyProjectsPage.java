package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class MyProjectsPage {
  protected final WorkflowContext context;
  private final com.meow.control.ViewMyIDPsController view;

  public MyProjectsPage(WorkflowContext context, com.meow.control.ViewMyIDPsController view) {
    this.context = context;
    this.view = view;
  }

  public void clickMyProjects() {
    context.call("MyProjectsPage.clickMyProjects");  // step 1
    displayIDPList(view.getMyIDPs(context.value("designerId")));  // step 2, 4
  }

  public void displayIDPList(List<Map<String, Object>> idps) {
    context.call("MyProjectsPage.displayIDPList");
    context.state().output.put("idps", idps);
  }
}
