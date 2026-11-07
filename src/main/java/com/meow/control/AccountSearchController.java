package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountSearchController {
  protected final WorkflowContext context;
  private final com.meow.entity.User user;

  public AccountSearchController(WorkflowContext context, com.meow.entity.User user) {
    this.context = context;
    this.user = user;
  }

  public List<Map<String, Object>> searchAccounts(String keyword) {
    context.call("AccountSearchController.searchAccounts");
    return user.findByKeyword(keyword);
  }
}
