package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ViewCompanyProfileController {
  protected final WorkflowContext context;
  private final com.meow.entity.CompanyProfile profile;
  private final com.meow.entity.Company company;

  public ViewCompanyProfileController(
      WorkflowContext context,
      com.meow.entity.CompanyProfile profile,
      com.meow.entity.Company company) {
    this.context = context;
    this.profile = profile;
    this.company = company;
  }

  public Map<String, Object> getPublicProfile(String designerId) {
    context.call("ViewCompanyProfileController.getPublicProfile");
    return profile.getDetails(designerId);  // step 3
  }

  public com.meow.domain.Company getCompanyProfile(String idpId) {
    context.call("ViewCompanyProfileController.getCompanyProfile");
    return company.getCompanyByIDP(idpId);  // step 3
  }
}
