package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryAvailabilityController {
  protected final WorkflowContext context;
  private final com.meow.entity.Category category;

  public CategoryAvailabilityController(
      WorkflowContext context, com.meow.entity.Category category) {
    this.context = context;
    this.category = category;
  }

  public List<Map<String, Object>> getCategories() {
    context.call("CategoryAvailabilityController.getCategories");
    return category.getAllCategories();
  }

  public boolean setAvailable(String categoryId, boolean available) {
    context.call("CategoryAvailabilityController.setAvailable");
    return category.updateAvailability(categoryId, available);
  }
}
