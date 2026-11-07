package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class SearchPage {
  protected final WorkflowContext context;
  private final com.meow.control.SearchIDPController search;

  public SearchPage(WorkflowContext context, com.meow.control.SearchIDPController search) {
    this.context = context;
    this.search = search;
  }

  public void searchIDP(String keyword) {
    context.call("SearchPage.searchIDP");
    displayResults(search.searchIDPs(keyword));
  }

  public void displayResults(List<Map<String, Object>> idps) {
    context.call("SearchPage.displayResults");
    context.state().output.put("idps", idps);
  }
}
