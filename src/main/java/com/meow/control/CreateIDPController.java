package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateIDPController {
  protected final WorkflowContext context;
  private final com.meow.entity.IDP idp;

  public CreateIDPController(WorkflowContext context, com.meow.entity.IDP idp) {
    this.context = context;
    this.idp = idp;
  }

  public boolean createIDP(
      String designerId, String title, String description, String projectDetails, String status) {
    context.call("CreateIDPController.createIDP");
    return idp.saveIDP(designerId, title, description, projectDetails, status);  // step 3
  }
}
