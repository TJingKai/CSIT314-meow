package com.meow.entity;

import com.meow.repository.Database;
import com.meow.service.Identity;
import com.meow.workflow.*;
import org.springframework.stereotype.Repository;

@Repository
public class IDPShortlist extends DiagramEntity {
  public IDPShortlist(Database db, Identity identity, WorkflowContext context) {
    super(db, identity, context);
  }

  public boolean isShortlisted(String idpId, String userId) {
    mark("IDPShortlist.isShortlisted");
    return db.count(
            "SELECT COUNT(*) FROM events WHERE project_id=? AND user_id=? AND type='shortlist'",
            idpId,
            userId)
        > 0;
  }

  public boolean saveShortlist(String idpId, String userId) {
    mark("IDPShortlist.saveShortlist");
    if (db.count("SELECT COUNT(*) FROM favourites WHERE project_id=? AND user_id=?", idpId, userId)
        == 0)
      throw new com.meow.api.ApiException(409, "conflict", "Save the IDP to favourites first.");
    try {
      event("shortlist", userId, idpId);
      return true;
    } catch (org.springframework.dao.DataAccessException failure) {
      org.springframework.transaction.interceptor.TransactionAspectSupport
          .currentTransactionStatus()
          .setRollbackOnly();
      return false;
    }
  }

  public int getShortlistCount(String idpId) {
    mark("IDPShortlist.getShortlistCount");
    return (int)
        db.count("SELECT COUNT(*) FROM events WHERE project_id=? AND type='shortlist'", idpId);
  }
}
