package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateCategoryController {
  protected final WorkflowContext context;
  private final com.meow.entity.Category category;

  public CreateCategoryController(WorkflowContext context, com.meow.entity.Category category) {
    this.context = context;
    this.category = category;
  }

  public boolean createCategory(String name, String description) {
    context.call("CreateCategoryController.createCategory");
    if (category.nameExists(name)) return false;  // step 5a, 5c
    return category.saveCategory(name, description);  // step 6a
  }
}
