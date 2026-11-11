package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeleteAccountController {
  protected final WorkflowContext context;
  private final com.meow.entity.User user;

  public DeleteAccountController(WorkflowContext context, com.meow.entity.User user) {
    this.context = context;
    this.user = user;
  }

  public boolean deleteUser(String userId) {
    context.call("DeleteAccountController.deleteUser");
    return user.removeAccount(userId);  // step 6b, 6c
  }
}
