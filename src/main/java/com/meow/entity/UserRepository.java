package com.meow.entity;

import com.meow.repository.Database;
import com.meow.service.*;
import com.meow.workflow.*;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository extends DiagramEntity {
  private final SessionService sessions;

  public UserRepository(
      Database db, Identity identity, WorkflowContext context, SessionService sessions) {
    super(db, identity, context);
    this.sessions = sessions;
  }

  public boolean validateUser(String email, String pwd) {
    mark("UserRepository.validateUser");
    var user =
        sessions.authenticate(
            Map.of("email", email, "password", pwd), context.value("remoteAddress"));
    context.state().output.put("user", user.publicView());
    context.state().selectedId = user.id();
    return true;
  }

  public void updateStatus() {
    mark("UserRepository.updateStatus");
    db.update(
        "UPDATE users SET last_login_at=? WHERE id=?", Database.now(), context.state().selectedId);
  }
}
