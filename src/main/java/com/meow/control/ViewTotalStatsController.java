package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ViewTotalStatsController {
  protected final WorkflowContext context;
  private final com.meow.entity.IDP idp;

  public ViewTotalStatsController(WorkflowContext context, com.meow.entity.IDP idp) {
    this.context = context;
    this.idp = idp;
  }

  public int[] getTotalStats(String designerId) {
    context.call("ViewTotalStatsController.getTotalStats");
    if (idp.countIDPs(designerId) == 0) return null;  // step 3
    int views = idp.getTotalViews(designerId);  // step 4
    int shortlists = idp.getTotalShortlists(designerId);  // step 5
    return new int[] {views, shortlists};
  }
}
