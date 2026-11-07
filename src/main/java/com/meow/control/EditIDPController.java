package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EditIDPController {
  protected final WorkflowContext context;
  private final com.meow.entity.IDP idp;

  public EditIDPController(WorkflowContext context, com.meow.entity.IDP idp) {
    this.context = context;
    this.idp = idp;
  }

  public boolean editIDP(
      String idpId, String title, String description, String projectDetails, String status) {
    context.call("EditIDPController.editIDP");
    return idp.updateIDP(idpId, title, description, projectDetails, status);
  }
}
