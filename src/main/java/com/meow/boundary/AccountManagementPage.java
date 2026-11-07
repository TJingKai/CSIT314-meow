package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class AccountManagementPage {
  protected final WorkflowContext context;
  private final com.meow.control.AccountSearchController search;

  public AccountManagementPage(
      WorkflowContext context, com.meow.control.AccountSearchController search) {
    this.context = context;
    this.search = search;
  }

  public void navigateToAccountManagement() {
    context.call("AccountManagementPage.navigateToAccountManagement");
    context.state().output.put("view", "accountManagement");
  }

  public void enterSearchKeyword(String keyword) {
    context.call("AccountManagementPage.enterSearchKeyword");
    context.state().input.put("keyword", keyword);
  }

  public void clickSearch() {
    context.call("AccountManagementPage.clickSearch");
    var accounts = search.searchAccounts(context.value("keyword"));
    if (accounts.isEmpty()) {
      context.state().output.put("accounts", accounts);
      displayError("No accounts found");
      context.state().status = 200;
    } else displayAccountList(accounts);
  }

  public void displayAccountList(List<Map<String, Object>> accounts) {
    context.call("AccountManagementPage.displayAccountList");
    context.state().output.put("accounts", accounts);
  }

  public void displayError(String message) {
    context.call("AccountManagementPage.displayError");
    context.message("error", message);
  }
}
