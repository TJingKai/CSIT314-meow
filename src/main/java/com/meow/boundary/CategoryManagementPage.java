package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class CategoryManagementPage {
  protected final WorkflowContext context;
  private final com.meow.control.CreateCategoryController create;
  private final com.meow.control.EditCategoryController edit;
  private final com.meow.control.CategoryAvailabilityController availability;

  public CategoryManagementPage(
      WorkflowContext context,
      com.meow.control.CreateCategoryController create,
      com.meow.control.EditCategoryController edit,
      com.meow.control.CategoryAvailabilityController availability) {
    this.context = context;
    this.create = create;
    this.edit = edit;
    this.availability = availability;
  }

  public void clickCreateCategory() {
    context.call("CategoryManagementPage.clickCreateCategory");  // step 1
    displayCreateForm();  // step 2
  }

  public void displayCreateForm() {
    context.call("CategoryManagementPage.displayCreateForm");
    context.state().output.put("form", "createCategory");
  }

  public void submitCategory(String name, String description) {
    context.call("CategoryManagementPage.submitCategory");  // step 3
    if (name.isBlank()) {
      displayError("Category name is required");  // step 4b
      return;
    }
    if (create.createCategory(name, description))  // step 4a, 4c
      displaySuccessMsg("Category created successfully");  // step 7a
    else {
      displayError("Category already exists");  // step 6c
      context.state().status = 409;
    }
  }

  public void navigateToCategories() {
    context.call("CategoryManagementPage.navigateToCategories");  // #247 step 1, #248 step 1, #276 step 1
    var categories =
        context.value("flow").equals("availability")
            ? availability.getCategories()  // #247 step 2, #248 step 2, #276 step 2
            : edit.getCategories();  // #247 step 2, #248 step 2, #276 step 2
    displayCategoryList(categories);  // #247 step 4, #248 step 4, #276 step 4
  }

  public void displayCategoryList(List<Map<String, Object>> categories) {
    context.call("CategoryManagementPage.displayCategoryList");
    context.state().output.put("categories", categories);
  }

  public void clickEdit(String categoryId) {
    context.call("CategoryManagementPage.clickEdit");  // step 5
    displayEditForm(categoryId);  // step 6
  }

  public void displayEditForm(String categoryId) {
    context.call("CategoryManagementPage.displayEditForm");
    context.state().output.put("editCategoryId", categoryId);
  }

  public void saveCategory(String categoryId, String name, String description) {
    context.call("CategoryManagementPage.saveCategory");  // step 7
    if (name.isBlank()) {
      displayError("Category name is required");  // step 8b
      return;
    }
    if (edit.updateCategory(categoryId, name, description))  // step 8a, 8c
      displaySuccessMsg("Category updated successfully");  // step 11a
    else {
      displayError("Category already exists");  // step 10c
      context.state().status = 409;
    }
  }

  public void toggleAvailable(String categoryId, boolean available) {
    context.call("CategoryManagementPage.toggleAvailable");  // #248 step 5, #276 step 5
    if (availability.setAvailable(categoryId, available))  // #248 step 6, #276 step 6
      displaySuccessMsg(  // #248 step 8a/8b, #276 step 8
          available
              ? "Category is now available for IDP creation"
              : "Category is unavailable for IDP creation");
    else displayError("Failed to update category. Please try again.");
  }

  public void displayError(String message) {
    context.call("CategoryManagementPage.displayError");
    context.message("error", message);
  }

  public void displaySuccessMsg(String message) {
    context.call("CategoryManagementPage.displaySuccessMsg");
    context.message("success", message);
  }
}
