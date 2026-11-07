package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccessController {
  protected final WorkflowContext context;
  private final com.meow.entity.UserRole role;

  public AccessController(WorkflowContext context, com.meow.entity.UserRole role) {
    this.context = context;
    this.role = role;
  }

  public List<String> getAuthorizedScreens(String roleName) {
    context.call("AccessController.getAuthorizedScreens");
    List<String> permissions = role.getPermissions(roleName);
    return permissions.isEmpty() ? null : permissions;
  }
}
