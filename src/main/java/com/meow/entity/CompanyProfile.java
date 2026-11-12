package com.meow.entity;

import com.meow.repository.Database;
import com.meow.service.Identity;
import com.meow.workflow.*;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class CompanyProfile extends DiagramEntity {
  public CompanyProfile(Database db, Identity identity, WorkflowContext context) {
    super(db, identity, context);
  }

  public boolean isUenExists(String uen) {
    mark("CompanyProfile.isUenExists");
    return db.count(
            "SELECT COUNT(*) FROM companies WHERE LOWER(uen)=LOWER(?) AND owner_id<>?",
            uen,
            identity.user().id())
        > 0;
  }

  public boolean updateDetails(String designerId, String name, String desc, String uen, int year) {
    mark("CompanyProfile.updateDetails");
    if (!designerId.equals(identity.user().id()))
      throw new com.meow.api.ApiException(403, "forbidden", "You may only edit your own company.");
    Map<String, Object> row = db.find("SELECT id FROM companies WHERE owner_id=?", designerId);
    if (row == null)
      db.update(
          "INSERT INTO companies(id,owner_id,name,description,uen,established_year) VALUES (?,?,?,?,?,?)",
          Database.id(),
          designerId,
          name,
          desc,
          uen,
          year);
    else
      db.update(
          "UPDATE companies SET name=?,description=?,uen=?,established_year=? WHERE owner_id=?",
          name,
          desc,
          uen,
          year,
          designerId);
    return true;
  }

  public Map<String, Object> getDetails(String designerId) {
    mark("CompanyProfile.getDetails");
    Map<String, Object> row =
        db.find(
            "SELECT c.* FROM companies c JOIN users u ON u.id=c.owner_id WHERE c.owner_id=? AND u.status='active' AND u.verified_at IS NOT NULL",
            designerId);
    return row == null ? null : com.meow.domain.Company.from(row).view(false);
  }
}
