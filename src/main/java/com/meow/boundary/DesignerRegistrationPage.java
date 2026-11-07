package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.io.File;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class DesignerRegistrationPage {
  protected final WorkflowContext context;
  private final com.meow.control.DesignerRegistrationController registration;

  public DesignerRegistrationPage(
      WorkflowContext context, com.meow.control.DesignerRegistrationController registration) {
    this.context = context;
    this.registration = registration;
  }

  public void navigateToDesignerRegister() {
    context.call("DesignerRegistrationPage.navigateToDesignerRegister");
    displayForm();
  }

  public void displayForm() {
    context.call("DesignerRegistrationPage.displayForm");
    context.state().output.put("form", "designerRegistration");
  }

  public void enterDetailsAndUpload(
      String email, String pwd, String compName, String regNum, File doc) {
    context.call("DesignerRegistrationPage.enterDetailsAndUpload");
    this.email = email;
    this.pwd = pwd;
    this.compName = compName;
    this.regNum = regNum;
    this.doc = doc;
  }

  public void clickSubmitApplication() {
    context.call("DesignerRegistrationPage.clickSubmitApplication");
    if (registration.submitApplication(email, pwd, compName, regNum, doc))
      displayMsg("Application submitted successfully");
    else {
      displayError("Email is already registered");
      context.state().status = 409;
    }
  }

  public void displayError(String message) {
    context.call("DesignerRegistrationPage.displayError");
    context.message("error", message);
  }

  public void displayMsg(String message) {
    context.call("DesignerRegistrationPage.displayMsg");
    context.message("success", message);
  }

  private String email, pwd, compName, regNum;
  private File doc;
}
