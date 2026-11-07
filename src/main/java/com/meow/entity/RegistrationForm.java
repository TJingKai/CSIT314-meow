package com.meow.entity;

import com.meow.workflow.WorkflowContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/** #220 form entity. Stores the entered values only for the lifetime of this request. */
@Component
@RequestScope
public class RegistrationForm {
  private final WorkflowContext context;
  private String email, pwd, confirmPwd;

  public RegistrationForm(WorkflowContext context) {
    this.context = context;
  }

  public void setDetails(String email, String pwd, String confirmPwd) {
    context.call("RegistrationForm.setDetails");
    this.email = email;
    this.pwd = pwd;
    this.confirmPwd = confirmPwd;
  }

  public boolean isValid() {
    context.call("RegistrationForm.isValid");
    return email != null
        && email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        && pwd != null
        && pwd.equals(confirmPwd);
  }
}
