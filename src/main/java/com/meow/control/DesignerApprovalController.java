package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DesignerApprovalController {
  protected final WorkflowContext context;
  private final com.meow.entity.InteriorDesigner designer;

  public DesignerApprovalController(
      WorkflowContext context, com.meow.entity.InteriorDesigner designer) {
    this.context = context;
    this.designer = designer;
  }

  public boolean approveDesigner(String designerId) {
    context.call("DesignerApprovalController.approveDesigner");
    if (!designer.setStatus(designerId, "ACTIVE")) return false;
    designer.enablePublish(designerId);
    return true;
  }

  public boolean rejectDesigner(String designerId) {
    context.call("DesignerApprovalController.rejectDesigner");
    return designer.setStatus(designerId, "REJECTED");
  }
}
