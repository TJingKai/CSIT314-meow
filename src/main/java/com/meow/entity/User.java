package com.meow.entity;

import com.meow.api.*;
import com.meow.repository.Database;
import com.meow.service.Identity;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class User extends DiagramEntity {
  public User(Database db, Identity identity, WorkflowContext context) {
    super(db, identity, context);
  }

  public boolean isEmailRegistered(String email) {
    mark("User.isEmailRegistered");
    return db.count("SELECT COUNT(*) FROM users WHERE email=?", email) > 0;
  }

  public void saveUser(String email, String pwdHash) {
    mark("User.saveUser");
    insert(email, pwdHash, "customer");
  }

  public void saveUserWithRole(String email, String pwdHash, String role) {
    mark("User.saveUserWithRole");
    if (!Set.of("customer", "designer", "admin", "manager").contains(role))
      throw new ApiException("Invalid role.");
    insert(email, pwdHash, role);
  }

  private void insert(String email, String hash, String role) {
    String id = Database.id();
    String name = Inputs.text(context.state().input, "name", false, 100);
    if (name.isEmpty()) name = email;
    db.update(
        "INSERT INTO users(id,name,email,password_hash,role,status,created_at) VALUES (?,?,?,?,?,?,?)",
        id,
        name,
        email,
        hash,
        role,
        role.equals("designer") ? "pending" : "active",
        Database.now());
    context.state().createdId = id;
    event("registration", id, null);
  }

  public List<Map<String, Object>> findByKeyword(String keyword) {
    mark("User.findByKeyword");
    return db
        .rows(
            "SELECT * FROM users WHERE LOCATE(LOWER(?),LOWER(name||' '||email||' '||role))>0 ORDER BY created_at",
            keyword)
        .stream()
        .map(row -> com.meow.domain.User.from(row).publicView())
        .toList();
  }

  public boolean setStatus(String userId, String targetStatus) {
    mark("User.setStatus");
    if (userId.equals(identity.user().id()))
      throw new ApiException(409, "conflict", "You cannot change your own status.");
    if (!Set.of("active", "suspended").contains(targetStatus.toLowerCase(Locale.ROOT)))
      throw new ApiException("Choose active or suspended.");
    Map<String, Object> row = db.required("SELECT * FROM users WHERE id=?", userId);
    if (targetStatus.equalsIgnoreCase("active")
        && row.get("role").equals("designer")
        && row.get("verified_at") == null)
      throw new ApiException(409, "conflict", "Designer approval is required.");
    boolean changed =
        db.update(
                "UPDATE users SET status=? WHERE id=?",
                targetStatus.toLowerCase(Locale.ROOT),
                userId)
            == 1;
    db.update("DELETE FROM sessions WHERE user_id=?", userId);
    return changed;
  }

  public boolean removeAccount(String userId) {
    mark("User.removeAccount");
    if (userId.equals(identity.user().id()))
      throw new ApiException(409, "conflict", "You cannot delete your own account.");
    return db.update("DELETE FROM users WHERE id=?", userId) == 1;
  }
}
