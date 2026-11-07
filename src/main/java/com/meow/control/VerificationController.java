package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VerificationController {
  protected final WorkflowContext context;
  private final com.meow.entity.InteriorDesigner designer;

  public VerificationController(
      WorkflowContext context, com.meow.entity.InteriorDesigner designer) {
    this.context = context;
    this.designer = designer;
  }

  public boolean processPending(String designerId) {
    context.call("VerificationController.processPending");
    if (!designer.setStatus(designerId, "PENDING_VERIFICATION")) return false;
    designer.disablePublish(designerId);
    return true;
  }
}
