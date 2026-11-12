package com.meow.entity;

import com.meow.repository.Database;
import com.meow.service.Identity;
import com.meow.workflow.*;
import org.springframework.stereotype.Repository;

@Repository
public class Company extends DiagramEntity {
  public Company(Database db, Identity identity, WorkflowContext context) {
    super(db, identity, context);
  }

  public com.meow.domain.Company getCompanyByIDP(String idpId) {
    mark("Company.getCompanyByIDP");
    var idp = project(idpId);
    if (!visible(idp))
      throw new com.meow.api.ApiException(404, "not_found", "Project unavailable.");
    var row = db.required("SELECT * FROM companies WHERE owner_id=?", idp.get("owner_id"));
    return com.meow.domain.Company.from(row);
  }
}
