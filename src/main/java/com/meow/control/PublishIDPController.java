package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PublishIDPController {
  protected final WorkflowContext context;
  private final com.meow.entity.IDP idp;

  public PublishIDPController(WorkflowContext context, com.meow.entity.IDP idp) {
    this.context = context;
    this.idp = idp;
  }

  public boolean setPublishStatus(String idpId, String action) {
    context.call("PublishIDPController.setPublishStatus");
    return idp.updateStatus(idpId, action);  // step 3
  }
}
