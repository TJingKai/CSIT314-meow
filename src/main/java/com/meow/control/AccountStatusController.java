package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountStatusController {
  protected final WorkflowContext context;
  private final com.meow.entity.User user;

  public AccountStatusController(WorkflowContext context, com.meow.entity.User user) {
    this.context = context;
    this.user = user;
  }

  public boolean updateStatus(String userId, String targetStatus) {
    context.call("AccountStatusController.updateStatus");
    return user.setStatus(userId, targetStatus);
  }
}
