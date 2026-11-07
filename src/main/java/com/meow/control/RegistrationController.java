package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RegistrationController {
  protected final WorkflowContext context;
  private final com.meow.entity.User user;
  private final org.springframework.security.crypto.password.PasswordEncoder passwords;

  public RegistrationController(
      WorkflowContext context,
      com.meow.entity.User user,
      org.springframework.security.crypto.password.PasswordEncoder passwords) {
    this.context = context;
    this.user = user;
    this.passwords = passwords;
  }

  public boolean createAccount(String email, String pwd) {
    context.call("RegistrationController.createAccount");
    if (user.isEmailRegistered(email)) return false;
    String hash = hashPassword(pwd);
    user.saveUser(email, hash);
    return true;
  }

  public String hashPassword(String pwd) {
    context.call("RegistrationController.hashPassword");
    return passwords.encode(pwd);
  }
}
