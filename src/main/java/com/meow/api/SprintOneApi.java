package com.meow.api;

import com.meow.boundary.*;
import com.meow.config.RequireRole;
import com.meow.repository.Database;
import com.meow.service.*;
import com.meow.workflow.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.time.Year;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class SprintOneApi extends ApiTransport {
  private final RegistrationPage registration;
  private final DesignerRegistrationPage designer;
  private final VerificationPage verification;
  private final LoginPage login;
  private final NavigationPage navigation;
  private final AppMenuView menu;
  private final AccountManagementPage accounts;
  private final CreateAccountPage create;
  private final ManageAccountPage manage;
  private final ReviewDesignerPage review;
  private final CompanyProfilePage company;
  private final PublicCompanyProfilePage publicCompany;
  private final SessionService sessions;

  public SprintOneApi(
      WorkflowContext context,
      Database db,
      Identity identity,
      RegistrationPage registration,
      DesignerRegistrationPage designer,
      VerificationPage verification,
      LoginPage login,
      NavigationPage navigation,
      AppMenuView menu,
      AccountManagementPage accounts,
      CreateAccountPage create,
      ManageAccountPage manage,
      ReviewDesignerPage review,
      CompanyProfilePage company,
      PublicCompanyProfilePage publicCompany,
      SessionService sessions) {
    super(context, db, identity);
    this.registration = registration;
    this.designer = designer;
    this.verification = verification;
    this.login = login;
    this.navigation = navigation;
    this.menu = menu;
    this.accounts = accounts;
    this.create = create;
    this.manage = manage;
    this.review = review;
    this.company = company;
    this.publicCompany = publicCompany;
    this.sessions = sessions;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Json.data(Map.of("status", "ok"));
  }

  @GetMapping("/registration-form")
  public ResponseEntity<?> registrationForm(@RequestParam(defaultValue = "customer") String role) {
    if (role.equals("designer")) designer.navigateToDesignerRegister();
    else registration.navigateToRegister();
    return response();
  }

  @PostMapping("/registration-validations")
  public ResponseEntity<?> validateRegistration(@RequestBody Map<String, Object> body) {
    input(body);
    registration.enterDetails(
        context.value("email"), context.value("password"), context.value("confirmPassword"));
    return response();
  }

  @PostMapping(value = "/registrations", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> registerCustomer(@RequestBody Map<String, Object> body) {
    input(body);
    if (!context.value("role").isEmpty() && !context.value("role").equals("customer"))
      throw new ApiException("Use the designer registration form with business proof.");
    Inputs.email(body);
    Inputs.password(body, "password");
    if (!Objects.equals(body.get("password"), body.get("confirmPassword")))
      throw new ApiException("Passwords do not match");
    registration.clickSubmit();
    return response(201);
  }

  @PostMapping(value = "/registrations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<?> registerDesigner(
      @RequestParam Map<String, String> body, @RequestPart("proof") MultipartFile proof)
      throws IOException {
    input(body);
    Inputs.email(body);
    Inputs.password(body, "password");
    if (!Objects.equals(body.get("password"), body.get("confirmPassword")))
      throw new ApiException("Passwords do not match");
    String name = Inputs.text(body, "company", true, 150), uen = Inputs.text(body, "uen", true, 50);
    try {
      File doc = attach(proof);
      designer.enterDetailsAndUpload(
          context.value("email"), context.value("password"), name, uen, doc);
      designer.clickSubmitApplication();
      if (context.state().createdId != null)
        verification.submitDocuments(context.state().createdId);
      return response(201);
    } finally {
      cleanFiles();
    }
  }

  @GetMapping("/login-form")
  public ResponseEntity<?> loginForm() {
    login.navigateToLoginPage();  // step 1
    return response();
  }

  @GetMapping("/auth/session")
  public Map<String, Object> session() {
    return Json.data(
        Json.object(
            "user",
            identity.optional() == null ? null : identity.user().publicView(),
            "csrfToken",
            identity.csrf()));
  }

  @PostMapping("/auth/session")
  public ResponseEntity<?> login(
      @RequestBody Map<String, Object> body,
      HttpServletRequest request,
      HttpServletResponse response) {
    input(body);
    context.state().request = request;
    context.state().response = response;
    context.state().input.put("remoteAddress", request.getRemoteAddr());
    String email = Inputs.email(body);
    String password = Inputs.text(body, "password", true, 128);
    login.enterCredentials(email, password);  // step 3
    login.clickLogin();  // step 4
    return response();
  }

  @DeleteMapping("/auth/session")
  @RequireRole
  public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
    context.state().selectedId = identity.user().id();
    context.state().input.put("tokenHash", SessionService.digest(SessionService.cookie(request)));
    navigation.clickLogout();  // step 1
    sessions.clearCookie(response);
    return response();
  }

  @GetMapping("/navigation")
  @RequireRole
  public ResponseEntity<?> navigation() {
    menu.openNavigationMenu(identity.user().role());  // step 1
    return response();
  }

  @GetMapping("/users")
  @RequireRole("admin")
  public ResponseEntity<?> users(@RequestParam(defaultValue = "") String q) {
    accounts.navigateToAccountManagement();
    accounts.enterSearchKeyword(q);
    accounts.clickSearch();
    return response();
  }

  @GetMapping("/users/form")
  @RequireRole("admin")
  public ResponseEntity<?> createForm() {
    create.navigateToCreateAccount();
    return response();
  }

  @PostMapping("/users")
  @RequireRole("admin")
  public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
    input(body);
    String email = Inputs.email(body), pwd = Inputs.password(body, "password");
    create.enterAccountDetails(
        email, pwd, Inputs.text(body, "confirmPassword", true, 128), text(body, "role"));
    create.clickCreateAccount();
    return response(201);
  }

  @PatchMapping("/users/{id}")
  @RequireRole("admin")
  public ResponseEntity<?> status(@PathVariable String id, @RequestBody Map<String, Object> body) {
    manage.viewAccountDetails(id);
    manage.clickUpdateStatus(id, text(body, "status"));
    return response();
  }

  @GetMapping("/users/{id}/deletion-confirmation")
  @RequireRole("admin")
  public ResponseEntity<?> confirmation(@PathVariable String id) {
    db.required("SELECT id FROM users WHERE id=?", id);
    manage.viewAccountDetails(id);
    manage.clickDelete(id);
    return response();
  }

  @PostMapping("/users/{id}/deletion-cancellations")
  @RequireRole("admin")
  public ResponseEntity<?> cancel(@PathVariable String id) {
    context.state().selectedId = id;
    manage.cancel();
    return response();
  }

  @DeleteMapping("/users/{id}")
  @RequireRole("admin")
  public ResponseEntity<?> delete(@PathVariable String id) {
    context.state().selectedId = id;
    manage.confirmDelete();
    return response();
  }

  @GetMapping("/users/{id}/verification")
  @RequireRole("admin")
  public ResponseEntity<?> application(@PathVariable String id) {
    var user = db.required("SELECT * FROM users WHERE id=? AND role='designer'", id);
    var details = db.find("SELECT * FROM companies WHERE owner_id=?", id);
    context.state().output.put("user", com.meow.domain.User.from(user).publicView());
    context
        .state()
        .output
        .put("company", details == null ? null : com.meow.domain.Company.from(details).view(true));
    review.viewPendingApplication(id);
    return response();
  }

  @PutMapping("/users/{id}/verification")
  @RequireRole("admin")
  public ResponseEntity<?> approve(@PathVariable String id, @RequestBody Map<String, Object> body) {
    var target = db.required("SELECT status FROM users WHERE id=? AND role='designer'", id);
    if (!Set.of("pending", "rejected").contains(target.get("status")))
      throw new ApiException(409, "conflict", "Choose a pending application.");
    String action = text(body, "decision");
    if (action.equals("approve")) review.clickApprove(id);
    else if (action.equals("reject")) review.clickReject(id);
    else throw new ApiException("Choose approve or reject.");
    return response();
  }

  @GetMapping("/me/company")
  @RequireRole("designer")
  public ResponseEntity<?> ownCompany() {
    var row = db.find("SELECT * FROM companies WHERE owner_id=?", identity.user().id());
    context
        .state()
        .output
        .put("company", row == null ? null : com.meow.domain.Company.from(row).view(true));
    company.viewCompanyProfile(identity.user().id());
    return response();
  }

  @PutMapping("/me/company")
  @RequireRole("designer")
  public ResponseEntity<?> saveCompany(@RequestBody Map<String, Object> body) {
    input(body);
    company.submitDetails(
        identity.user().id(),
        Inputs.text(body, "name", true, 150),
        Inputs.text(body, "description", false, 5000),
        Inputs.text(body, "uen", true, 50),
        (int) Inputs.number(body, "year", 1800, Year.now().getValue()));
    return response();
  }

  @GetMapping("/companies/{id}")
  public ResponseEntity<?> company(@PathVariable String id) {
    var row = db.find("SELECT owner_id FROM companies WHERE id=?", id);
    publicCompany.viewPublicProfile(row == null ? id : Database.str(row, "owner_id"));  // step 1
    return response();
  }

  @GetMapping("/designers/{id}/company")
  public ResponseEntity<?> designerCompany(@PathVariable String id) {
    publicCompany.viewPublicProfile(id);  // step 1
    return response();
  }
}
