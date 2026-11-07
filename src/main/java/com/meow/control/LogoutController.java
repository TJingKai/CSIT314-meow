package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LogoutController {
  protected final WorkflowContext context;
  private final com.meow.entity.Session session;

  public LogoutController(WorkflowContext context, com.meow.entity.Session session) {
    this.context = context;
    this.session = session;
  }

  public boolean logout(String userId) {
    context.call("LogoutController.logout");
    session.clearSession(userId);
    return true;
  }
}
