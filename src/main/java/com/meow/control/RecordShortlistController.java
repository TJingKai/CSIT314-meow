package com.meow.control;

import com.meow.api.*;
import com.meow.repository.Database;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RecordShortlistController {
  protected final WorkflowContext context;
  private final com.meow.entity.IDPShortlist shortlists;
  private final Database db;

  public RecordShortlistController(
      WorkflowContext context, com.meow.entity.IDPShortlist shortlists, Database db) {
    this.context = context;
    this.shortlists = shortlists;
    this.db = db;
  }

  public int recordShortlist(String idpId, String userId) {
    context.call("RecordShortlistController.recordShortlist");
    db.required("SELECT id FROM projects WHERE id=? FOR UPDATE", idpId);
    if (shortlists.isShortlisted(idpId, userId)) return 0;  // step 3
    if (!shortlists.saveShortlist(idpId, userId)) return -1;  // step 4a
    return shortlists.getShortlistCount(idpId);  // step 5a
  }
}
