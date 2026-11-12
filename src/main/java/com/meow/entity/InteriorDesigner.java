package com.meow.entity;

import com.meow.api.*;
import com.meow.repository.Database;
import com.meow.service.*;
import com.meow.workflow.*;
import java.io.File;
import java.time.Year;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

@Repository
public class InteriorDesigner extends DiagramEntity {
  private final UploadService uploads;
  private final PasswordEncoder passwords;

  public InteriorDesigner(
      Database db,
      Identity identity,
      WorkflowContext context,
      UploadService uploads,
      PasswordEncoder passwords) {
    super(db, identity, context);
    this.uploads = uploads;
    this.passwords = passwords;
  }

  public boolean isEmailRegistered(String email) {
    mark("InteriorDesigner.isEmailRegistered");
    return db.count("SELECT COUNT(*) FROM users WHERE email=?", email) > 0;
  }

  public void saveDesigner(String email, String pwd, String compName, String regNum, File doc) {
    mark("InteriorDesigner.saveDesigner");
    String id = Database.id();
    String name = Inputs.text(context.state().input, "name", false, 100);
    if (name.isEmpty()) name = email;
    Integer year =
        context.value("year").isBlank()
            ? null
            : (int) Inputs.number(context.state().input, "year", 1800, Year.now().getValue());
    db.update(
        "INSERT INTO users(id,name,email,password_hash,role,status,created_at) VALUES (?,?,?,?, 'designer','pending',?)",
        id,
        name,
        email,
        passwords.encode(pwd),
        Database.now());
    String proof = uploads.store(context.upload(doc), id, "proof");
    db.update(
        "INSERT INTO companies(id,owner_id,name,uen,established_year,proof_id) VALUES (?,?,?,?,?,?)",
        Database.id(),
        id,
        compName,
        regNum,
        year,
        proof);
    context.state().createdId = id;
    event("registration", id, null);
  }

  public boolean setStatus(String designerId, String status) {
    mark("InteriorDesigner.setStatus");
    String value =
        switch (status) {
          case "PENDING_VERIFICATION" -> "pending";
          case "ACTIVE" -> "active";
          case "REJECTED" -> "rejected";
          default -> throw new ApiException("Invalid designer status.");
        };
    if (value.equals("active")
        && db.count(
                "SELECT COUNT(*) FROM companies WHERE owner_id=? AND proof_id IS NOT NULL",
                designerId)
            == 0)
      throw new ApiException(409, "conflict", "Business proof is required before approval.");
    boolean updated =
        db.update(
                "UPDATE users SET status=?,verified_at=NULL WHERE id=? AND role='designer'",
                value,
                designerId)
            == 1;
    db.update("DELETE FROM sessions WHERE user_id=?", designerId);
    return updated;
  }

  public void disablePublish(String designerId) {
    mark("InteriorDesigner.disablePublish");
    db.update("UPDATE users SET verified_at=NULL WHERE id=?", designerId);
  }

  public void enablePublish(String designerId) {
    mark("InteriorDesigner.enablePublish");
    db.update(
        "UPDATE users SET verified_at=? WHERE id=? AND status='active'",
        Database.now(),
        designerId);
  }
}
