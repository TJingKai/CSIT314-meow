package com.meow.entity;

import com.meow.repository.Database;
import com.meow.service.Identity;
import com.meow.workflow.*;
import org.springframework.stereotype.Repository;

@Repository
public class Session extends DiagramEntity {
  public Session(Database db, Identity identity, WorkflowContext context) {
    super(db, identity, context);
  }

  public void clearSession(String userId) {
    mark("Session.clearSession");
    db.update(
        "DELETE FROM sessions WHERE user_id=? AND token_hash=?",
        userId,
        context.value("tokenHash"));
  }
}
