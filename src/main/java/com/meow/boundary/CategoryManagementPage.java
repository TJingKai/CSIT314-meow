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
    context.call("CategoryManagementPage.clickCreateCategory");
    displayCreateForm();
  }

  public void displayCreateForm() {
    context.call("CategoryManagementPage.displayCreateForm");
    context.state().output.put("form", "createCategory");
  }

  public void submitCategory(String name, String description) {
    context.call("CategoryManagementPage.submitCategory");
    if (name.isBlank()) {
      displayError("Category name is required");
      return;
    }
    if (create.createCategory(name, description))
      displaySuccessMsg("Category created successfully");
    else {
      displayError("Category already exists");
      context.state().status = 409;
    }
  }

  public void navigateToCategories() {
    context.call("CategoryManagementPage.navigateToCategories");
    var categories =
        context.value("flow").equals("availability")
            ? availability.getCategories()
            : edit.getCategories();
    displayCategoryList(categories);
  }

  public void displayCategoryList(List<Map<String, Object>> categories) {
    context.call("CategoryManagementPage.displayCategoryList");
    context.state().output.put("categories", categories);
  }

  public void clickEdit(String categoryId) {
    context.call("CategoryManagementPage.clickEdit");
    displayEditForm(categoryId);
  }

  public void displayEditForm(String categoryId) {
    context.call("CategoryManagementPage.displayEditForm");
    context.state().output.put("editCategoryId", categoryId);
  }

  public void saveCategory(String categoryId, String name, String description) {
    context.call("CategoryManagementPage.saveCategory");
    if (name.isBlank()) {
      displayError("Category name is required");
      return;
    }
    if (edit.updateCategory(categoryId, name, description))
      displaySuccessMsg("Category updated successfully");
    else {
      displayError("Category already exists");
      context.state().status = 409;
    }
  }

  public void toggleAvailable(String categoryId, boolean available) {
    context.call("CategoryManagementPage.toggleAvailable");
    if (availability.setAvailable(categoryId, available))
      displaySuccessMsg(
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
