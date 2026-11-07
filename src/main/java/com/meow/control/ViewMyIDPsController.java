package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ViewMyIDPsController {
  protected final WorkflowContext context;
  private final com.meow.entity.IDP idp;

  public ViewMyIDPsController(WorkflowContext context, com.meow.entity.IDP idp) {
    this.context = context;
    this.idp = idp;
  }

  public List<Map<String, Object>> getMyIDPs(String designerId) {
    context.call("ViewMyIDPsController.getMyIDPs");
    return idp.getIDPsByDesigner(designerId);
  }
}
