package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateAccountController {
  protected final WorkflowContext context;
  private final com.meow.entity.User user;
  private final org.springframework.security.crypto.password.PasswordEncoder passwords;

  public CreateAccountController(
      WorkflowContext context,
      com.meow.entity.User user,
      org.springframework.security.crypto.password.PasswordEncoder passwords) {
    this.context = context;
    this.user = user;
    this.passwords = passwords;
  }

  public boolean createAccount(String email, String pwd, String confirmPwd, String role) {
    context.call("CreateAccountController.createAccount");
    if (!pwd.equals(confirmPwd)) {
      context.state().output.put("failure", "passwordMismatch");
      return false;
    }
    if (user.isEmailRegistered(email)) {  // step 6a, 6c
      context.state().output.put("failure", "duplicateEmail");
      return false;
    }
    String hash = hashPassword(pwd);  // step 7a
    user.saveUserWithRole(email, hash, role);  // step 8a
    return true;
  }

  public String hashPassword(String pwd) {
    context.call("CreateAccountController.hashPassword");
    return passwords.encode(pwd);
  }
}
