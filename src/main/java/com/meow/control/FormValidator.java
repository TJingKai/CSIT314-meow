package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FormValidator {
  protected final WorkflowContext context;
  private final com.meow.entity.RegistrationForm form;

  public FormValidator(WorkflowContext context, com.meow.entity.RegistrationForm form) {
    this.context = context;
    this.form = form;
  }

  public boolean validate(String email, String pwd, String confirmPwd) {
    context.call("FormValidator.validate");
    form.setDetails(email, pwd, confirmPwd);
    return form.isValid();
  }
}
