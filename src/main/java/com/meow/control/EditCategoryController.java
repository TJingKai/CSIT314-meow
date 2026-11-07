package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EditCategoryController {
  protected final WorkflowContext context;
  private final com.meow.entity.Category category;

  public EditCategoryController(WorkflowContext context, com.meow.entity.Category category) {
    this.context = context;
    this.category = category;
  }

  public List<Map<String, Object>> getCategories() {
    context.call("EditCategoryController.getCategories");
    return category.getAllCategories();
  }

  public boolean updateCategory(String categoryId, String name, String description) {
    context.call("EditCategoryController.updateCategory");
    if (category.nameExists(name, categoryId)) return false;
    return category.updateCategory(categoryId, name, description);
  }
}
