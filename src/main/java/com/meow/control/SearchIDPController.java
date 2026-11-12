package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SearchIDPController {
  protected final WorkflowContext context;
  private final com.meow.entity.IDP idp;

  public SearchIDPController(WorkflowContext context, com.meow.entity.IDP idp) {
    this.context = context;
    this.idp = idp;
  }

  public List<Map<String, Object>> searchIDPs(String keyword) {
    context.call("SearchIDPController.searchIDPs");
    return idp.findPublishedByKeyword(keyword);  // #237 step 3, #268 step 3
  }
}
