package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RecordViewController {
  protected final WorkflowContext context;
  private final com.meow.entity.IDP idp;
  private final com.meow.entity.IDPView views;

  public RecordViewController(
      WorkflowContext context, com.meow.entity.IDP idp, com.meow.entity.IDPView views) {
    this.context = context;
    this.idp = idp;
    this.views = views;
  }

  public int recordView(String idpId) {
    context.call("RecordViewController.recordView");
    if (!idp.isPublished(idpId)) return 0;  // step 3
    if (!views.saveView(idpId)) return -1;  // step 4a
    return views.getViewCount(idpId);  // step 5a
  }
}
