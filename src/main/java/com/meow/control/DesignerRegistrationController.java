package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.io.File;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DesignerRegistrationController {
  protected final WorkflowContext context;
  private final com.meow.entity.InteriorDesigner designer;

  public DesignerRegistrationController(
      WorkflowContext context, com.meow.entity.InteriorDesigner designer) {
    this.context = context;
    this.designer = designer;
  }

  public boolean submitApplication(
      String email, String pwd, String compName, String regNum, File doc) {
    context.call("DesignerRegistrationController.submitApplication");
    if (designer.isEmailRegistered(email)) return false;
    designer.saveDesigner(email, pwd, compName, regNum, doc);
    return true;
  }
}
