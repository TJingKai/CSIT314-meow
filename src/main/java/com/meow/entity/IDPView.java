package com.meow.entity;

import com.meow.repository.Database;
import com.meow.service.Identity;
import com.meow.workflow.*;
import org.springframework.stereotype.Repository;

@Repository
public class IDPView extends DiagramEntity {
  public IDPView(Database db, Identity identity, WorkflowContext context) {
    super(db, identity, context);
  }

  public boolean saveView(String idpId) {
    mark("IDPView.saveView");
    try {
      event("view", identity.user().id(), idpId);
      return true;
    } catch (org.springframework.dao.DataAccessException failure) {
      org.springframework.transaction.interceptor.TransactionAspectSupport
          .currentTransactionStatus()
          .setRollbackOnly();
      return false;
    }
  }

  public int getViewCount(String idpId) {
    mark("IDPView.getViewCount");
    return (int) db.count("SELECT COUNT(*) FROM events WHERE project_id=? AND type='view'", idpId);
  }
}
