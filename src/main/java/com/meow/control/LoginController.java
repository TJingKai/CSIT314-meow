package com.meow.control;

import com.meow.api.*;
import com.meow.repository.Database;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LoginController {
  private final com.meow.service.SessionService sessions;
  private final Database db;
  protected final WorkflowContext context;
  private final com.meow.entity.UserRepository users;

  public LoginController(
      WorkflowContext context,
      com.meow.entity.UserRepository users,
      com.meow.service.SessionService sessions,
      Database db) {
    this.context = context;
    this.users = users;
    this.sessions = sessions;
    this.db = db;
  }

  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  public boolean login(String email, String pwd) {
    context.call("LoginController.login");
    boolean valid = users.validateUser(email, pwd);
    if (valid) createSession();
    return valid;
  }

  public void createSession() {
    context.state().infrastructureCalls.add("LoginController.createSession");
    sessions.issue(
        com.meow.domain.User.from(
            db.required("SELECT * FROM users WHERE id=?", context.state().selectedId)),
        context.state().request,
        context.state().response);
  }
}
