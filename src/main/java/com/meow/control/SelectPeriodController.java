package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SelectPeriodController {
  protected final WorkflowContext context;
  private final com.meow.entity.ReportPeriod period;

  public SelectPeriodController(WorkflowContext context, com.meow.entity.ReportPeriod period) {
    this.context = context;
    this.period = period;
  }

  public Date[] getPeriod(String periodType, Date date) {
    context.call("SelectPeriodController.getPeriod");
    if (!period.setPeriod(periodType, date)) return null;  // step 5
    return period.calculateRange();  // step 6a
  }
}
