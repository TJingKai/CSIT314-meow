package com.meow.entity;

import com.meow.workflow.WorkflowContext;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class UserRole {
  private final WorkflowContext context;

  public UserRole(WorkflowContext context) {
    this.context = context;
  }

  public List<String> getPermissions(String role) {
    context.call("UserRole.getPermissions");
    return switch (role) {
      case "customer" -> List.of("discover", "favourites");
      case "designer" -> List.of("portfolio", "analytics", "company");
      case "admin" -> List.of("accounts", "verification");
      case "manager" -> List.of("categories", "reports");
      default -> List.of();
    };
  }
}
