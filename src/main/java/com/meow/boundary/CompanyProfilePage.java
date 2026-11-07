package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class CompanyProfilePage {
  protected final WorkflowContext context;
  private final com.meow.control.CompanyProfileController profile;
  private final com.meow.control.ViewCompanyProfileController view;

  public CompanyProfilePage(
      WorkflowContext context,
      com.meow.control.CompanyProfileController profile,
      com.meow.control.ViewCompanyProfileController view) {
    this.context = context;
    this.profile = profile;
    this.view = view;
  }

  public void viewCompanyProfile(String designerId) {
    context.call("CompanyProfilePage.viewCompanyProfile");
    displayForm(context.state().output);
  }

  public void displayForm(Map<String, Object> details) {
    context.call("CompanyProfilePage.displayForm");
    context.state().output.put("formDetails", new LinkedHashMap<>(details));
  }

  public void submitDetails(String designerId, String name, String desc, String uen, int year) {
    context.call("CompanyProfilePage.submitDetails");
    if (profile.saveProfile(designerId, name, desc, uen, year)) {
      displaySuccessMsg("Company details saved successfully");
      refreshPage();
    } else {
      displayError("UEN is already registered");
      context.state().status = 409;
    }
  }

  public void refreshPage() {
    context.call("CompanyProfilePage.refreshPage");
    context.state().output.put("refresh", true);
  }

  public void clickCompanyName(String idpId) {
    context.call("CompanyProfilePage.clickCompanyName");
    var company = view.getCompanyProfile(idpId);
    displayCompanyProfile(company);
  }

  public void displayCompanyProfile(com.meow.domain.Company company) {
    context.call("CompanyProfilePage.displayCompanyProfile");
    context.state().output.put("company", company.view(false));
  }

  public void displayError(String message) {
    context.call("CompanyProfilePage.displayError");
    context.message("error", message);
  }

  public void displaySuccessMsg(String message) {
    context.call("CompanyProfilePage.displaySuccessMsg");
    context.message("success", message);
  }
}
