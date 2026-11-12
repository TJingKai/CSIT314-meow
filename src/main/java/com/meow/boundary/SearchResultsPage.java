package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class SearchResultsPage {
  protected final WorkflowContext context;
  private final com.meow.control.ViewSearchResultsController results;

  public SearchResultsPage(
      WorkflowContext context, com.meow.control.ViewSearchResultsController results) {
    this.context = context;
    this.results = results;
  }

  public void submitSearch(String keyword) {
    context.call("SearchResultsPage.submitSearch");  // step 1
    var idps = results.getResults(keyword);  // step 2
    if (idps.isEmpty()) {
      context.state().output.put("idps", idps);
      displayMessage("No projects match your search");  // step 4b
    } else displayResults(idps);  // step 4a
  }

  public void displayResults(List<Map<String, Object>> idps) {
    context.call("SearchResultsPage.displayResults");
    context.state().output.put("idps", idps);
  }

  public void displayMessage(String message) {
    context.call("SearchResultsPage.displayMessage");
    context.message("notice", message);
  }
}
