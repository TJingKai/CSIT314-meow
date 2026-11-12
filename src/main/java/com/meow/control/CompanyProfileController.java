package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CompanyProfileController {
  protected final WorkflowContext context;
  private final com.meow.entity.CompanyProfile company;

  public CompanyProfileController(WorkflowContext context, com.meow.entity.CompanyProfile company) {
    this.context = context;
    this.company = company;
  }

  public boolean saveProfile(String designerId, String name, String desc, String uen, int year) {
    context.call("CompanyProfileController.saveProfile");
    if (company.isUenExists(uen)) return false;  // step 5
    return company.updateDetails(designerId, name, desc, uen, year);  // step 6a
  }
}
