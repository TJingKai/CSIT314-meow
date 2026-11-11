package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ViewSearchResultsController {
  protected final WorkflowContext context;
  private final com.meow.entity.IDP idp;

  public ViewSearchResultsController(WorkflowContext context, com.meow.entity.IDP idp) {
    this.context = context;
    this.idp = idp;
  }

  public List<Map<String, Object>> getResults(String keyword) {
    context.call("ViewSearchResultsController.getResults");
    return idp.findPublishedByKeyword(keyword);  // step 3
  }
}
