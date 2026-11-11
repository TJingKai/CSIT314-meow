package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AssignCategoriesController {
  protected final WorkflowContext context;
  private final com.meow.entity.IDPCategory categories;

  public AssignCategoriesController(
      WorkflowContext context, com.meow.entity.IDPCategory categories) {
    this.context = context;
    this.categories = categories;
  }

  public int assignCategories(String idpId, List<String> categoryIds) {
    context.call("AssignCategoriesController.assignCategories");
    if (categoryIds.isEmpty()) return 0;
    return categories.saveCategories(idpId, categoryIds) ? 1 : -1;  // step 3a, 3c
  }
}
