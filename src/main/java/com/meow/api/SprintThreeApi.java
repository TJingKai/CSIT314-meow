package com.meow.api;

import com.meow.boundary.*;
import com.meow.config.RequireRole;
import com.meow.entity.ReportPeriod;
import com.meow.repository.Database;
import com.meow.service.Identity;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequireRole
public class SprintThreeApi extends ApiTransport {
  private final IDPDetailPage details;
  private final DesignerDashboardPage dashboard;
  private final CategoryManagementPage categories;
  private final ReportPage reports;

  public SprintThreeApi(
      WorkflowContext context,
      Database db,
      Identity identity,
      IDPDetailPage details,
      DesignerDashboardPage dashboard,
      CategoryManagementPage categories,
      ReportPage reports) {
    super(context, db, identity);
    this.details = details;
    this.dashboard = dashboard;
    this.categories = categories;
    this.reports = reports;
  }

  @PostMapping("/projects/{id}/views")
  public ResponseEntity<?> view(@PathVariable String id) {
    details.openIDP(id);
    return response();
  }

  @PostMapping("/projects/{id}/shortlists")
  @RequireRole("customer")
  public ResponseEntity<?> shortlist(@PathVariable String id) {
    details.saveIDP(id, identity.user().id());
    return response();
  }

  @GetMapping("/me/analytics/totals")
  @RequireRole("designer")
  public ResponseEntity<?> totals() {
    identity.verified();
    input(Map.of());
    dashboard.navigateToDashboard();
    return response();
  }

  @GetMapping("/me/analytics/projects")
  @RequireRole("designer")
  public ResponseEntity<?> perProject() {
    identity.verified();
    input(Map.of());
    dashboard.clickViewByIDP();
    return response();
  }

  @GetMapping("/categories/form")
  @RequireRole("manager")
  public ResponseEntity<?> categoryForm() {
    categories.clickCreateCategory();
    return response();
  }

  @GetMapping("/categories")
  @RequireRole("manager")
  public ResponseEntity<?> categories(@RequestParam(defaultValue = "edit") String flow) {
    input(Map.of("flow", flow));
    categories.navigateToCategories();
    return response();
  }

  @PostMapping("/categories")
  @RequireRole("manager")
  public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
    input(body);
    categories.submitCategory(
        Inputs.text(body, "name", false, 80), Inputs.text(body, "description", false, 5000));
    return response(201);
  }

  @GetMapping("/categories/{id}/edit-form")
  @RequireRole("manager")
  public ResponseEntity<?> editForm(@PathVariable String id) {
    context
        .state()
        .output
        .put(
            "category",
            db.required("SELECT id,name,description,active FROM categories WHERE id=?", id));
    categories.clickEdit(id);
    return response();
  }

  @PatchMapping("/categories/{id}")
  @RequireRole("manager")
  public ResponseEntity<?> edit(@PathVariable String id, @RequestBody Map<String, Object> body) {
    categories.saveCategory(
        id, Inputs.text(body, "name", false, 80), Inputs.text(body, "description", false, 5000));
    return response();
  }

  @PutMapping("/categories/{id}/availability")
  @RequireRole("manager")
  public ResponseEntity<?> available(
      @PathVariable String id, @RequestBody Map<String, Object> body) {
    categories.toggleAvailable(id, Inputs.bool(body, "available"));
    return response();
  }

  @GetMapping("/reports/options")
  @RequireRole("manager")
  public ResponseEntity<?> options() {
    reports.navigateToReports();
    return response();
  }

  @PostMapping("/reports/period")
  @RequireRole("manager")
  public ResponseEntity<?> period(@RequestBody Map<String, Object> body) {
    input(body);
    reports.selectPeriod(text(body, "period"), ReportPeriod.parse(context.value("date")));
    return response();
  }

  @GetMapping("/reports/activity")
  @RequireRole("manager")
  public ResponseEntity<?> totals(@RequestParam String period, @RequestParam String date) {
    input(Map.of("period", period, "date", date));
    if (date.isBlank()) {
      context.message("error", "Please select a date");
      return response();
    }
    reports.clickGenerate(period, ReportPeriod.parse(date));
    return response();
  }

  @GetMapping("/reports/table")
  @RequireRole("manager")
  public ResponseEntity<?> table(@RequestParam String period, @RequestParam String date) {
    input(Map.of("period", period, "date", date));
    if (date.isBlank()) {
      context.message("error", "Please select a date");
      return response();
    }
    reports.clickViewAsTable();
    return response();
  }
}
