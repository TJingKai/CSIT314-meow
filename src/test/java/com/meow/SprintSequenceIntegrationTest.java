package com.meow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;
import com.meow.config.SeedData;
import com.meow.repository.Database;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Expected method lists were independently transcribed from all 36 supplied sequence diagrams. */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:sprinttest;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "meow.seed-demo=false",
      "meow.sequence-trace=true"
    })
@AutoConfigureMockMvc
class SprintSequenceIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired Database db;
  @Autowired SeedData seed;

  record Auth(Cookie cookie, String csrf) {}

  static final String API = "/api/v1", PASSWORD = "MEOWDemo!2026";
  static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0};

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add(
        "meow.upload-directory",
        () -> {
          try {
            return Files.createTempDirectory("meow-sprint-tests-").toString();
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
        });
  }

  @BeforeEach
  void reset() throws Exception {
    for (String table :
        List.of(
            "sessions",
            "favourites",
            "events",
            "project_photos",
            "project_categories",
            "projects",
            "companies",
            "uploads",
            "users",
            "categories",
            "login_attempts")) db.update("DELETE FROM " + table);
    seed.seed();
  }

  static Stream<Arguments> stories() throws Exception {
    JsonNode contracts =
        new ObjectMapper()
            .readTree(
                SprintSequenceIntegrationTest.class.getResourceAsStream("/sprint-contracts.json"));
    List<Arguments> arguments = new ArrayList<>();
    for (JsonNode c : contracts)
      arguments.add(
          Arguments.of(c.get("story").asInt(), c.get("title").asText(), c.get("expectedCalls")));
    return arguments.stream();
  }

  JsonNode tree(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  MvcResult request(String method, String path, Auth auth, Object body) throws Exception {
    MockHttpServletRequestBuilder b =
        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
            HttpMethod.valueOf(method), API + path);
    if (auth != null) b.cookie(auth.cookie()).header("X-CSRF-Token", auth.csrf());
    if (body != null)
      b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
    return mvc.perform(b).andReturn();
  }

  void status(int expected, MvcResult r) throws Exception {
    assertEquals(expected, r.getResponse().getStatus(), r.getResponse().getContentAsString());
  }

  List<String> calls(MvcResult r) throws Exception {
    List<String> calls = new ArrayList<>();
    tree(r).path("sequence").forEach(v -> calls.add(v.asText()));
    return calls;
  }

  void collect(List<String> calls, MvcResult r, int status) throws Exception {
    status(status, r);
    calls.addAll(calls(r));
  }

  Auth login(String role) throws Exception {
    return loginEmail(role + "@meow.demo", PASSWORD);
  }

  Auth loginEmail(String email, String pwd) throws Exception {
    MvcResult r = request("POST", "/auth/session", null, Map.of("email", email, "password", pwd));
    status(200, r);
    String cookie = r.getResponse().getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1];
    Cookie value = new Cookie("meow_session", cookie);
    JsonNode session = tree(mvc.perform(get(API + "/auth/session").cookie(value)).andReturn());
    return new Auth(value, session.at("/data/csrfToken").asText());
  }

  Map<String, Object> idp() {
    return Map.of(
        "title",
        "A Java Home",
        "description",
        "A warm space",
        "projectDetails",
        "Natural wood and soft light",
        "status",
        "Current");
  }

  MvcResult designerRegistration() throws Exception {
    return mvc.perform(
            multipart(API + "/registrations")
                .file(
                    new MockMultipartFile(
                        "proof",
                        "proof.pdf",
                        "application/pdf",
                        "%PDF-1.4 Demonstration proof".getBytes()))
                .param("email", "newdesigner@example.com")
                .param("password", "ExamplePassword1!")
                .param("confirmPassword", "ExamplePassword1!")
                .param("role", "designer")
                .param("company", "New Studio")
                .param("uen", "NEW123"))
        .andReturn();
  }

  String candidate() throws Exception {
    MvcResult r = designerRegistration();
    status(201, r);
    return tree(r).at("/data/id").asText();
  }

  void favouriteSetup() {
    db.update("INSERT INTO favourites VALUES (?,?,?)", "c1", "p0", Database.now());
  }

  @ParameterizedTest(name = "#{0}: {1}")
  @MethodSource("stories")
  void allThirtySixNormalSequences(int story, String title, JsonNode expected) throws Exception {
    List<String> actual = new ArrayList<>();
    MvcResult r;
    Auth auth;
    switch (story) {
      case 220 -> {
        collect(actual, request("GET", "/registration-form", null, null), 200);
        r =
            request(
                "POST",
                "/registration-validations",
                null,
                Map.of(
                    "email",
                    "new@example.com",
                    "password",
                    "ExamplePassword1!",
                    "confirmPassword",
                    "ExamplePassword1!"));
        collect(actual, r, 200);
        assertTrue(tree(r).at("/data/submitEnabled").asBoolean());
      }
      case 221 -> {
        r =
            request(
                "POST",
                "/registrations",
                null,
                Map.of(
                    "email",
                    "new@example.com",
                    "password",
                    "ExamplePassword1!",
                    "confirmPassword",
                    "ExamplePassword1!",
                    "role",
                    "customer"));
        collect(actual, r, 201);
        assertEquals(
            "active",
            db.required("SELECT status FROM users WHERE email=?", "new@example.com").get("status"));
        assertNotEquals(
            "ExamplePassword1!",
            db.required("SELECT password_hash FROM users WHERE email=?", "new@example.com")
                .get("password_hash"));
      }
      case 222 -> {
        collect(actual, request("GET", "/registration-form?role=designer", null, null), 200);
        r = designerRegistration();
        status(201, r);
        List<String> whole = calls(r);
        int split = whole.indexOf("VerificationPage.submitDocuments");
        assertTrue(split > 0);
        actual.addAll(whole.subList(0, split));
        assertEquals(
            "pending",
            db.required("SELECT status FROM users WHERE email=?", "newdesigner@example.com")
                .get("status"));
      }
      case 223 -> {
        r = designerRegistration();
        status(201, r);
        List<String> whole = calls(r);
        actual.addAll(
            whole.subList(whole.indexOf("VerificationPage.submitDocuments"), whole.size()));
        assertNull(
            db.required("SELECT verified_at FROM users WHERE email=?", "newdesigner@example.com")
                .get("verified_at"));
      }
      case 224 -> {
        collect(actual, request("GET", "/login-form", null, null), 200);
        r =
            request(
                "POST",
                "/auth/session",
                null,
                Map.of("email", "customer@meow.demo", "password", PASSWORD));
        collect(actual, r, 200);
        assertTrue(r.getResponse().getHeader("Set-Cookie").contains("HttpOnly"));
        assertEquals("LoginController.createSession", tree(r).at("/infrastructure/0").asText());
      }
      case 225 -> {
        auth = login("customer");
        r = request("DELETE", "/auth/session", auth, null);
        collect(actual, r, 200);
        assertEquals("login", tree(r).at("/data/redirect").asText());
        status(401, request("GET", "/navigation", auth, null));
      }
      case 226 -> {
        auth = login("customer");
        r = request("GET", "/navigation", auth, null);
        collect(actual, r, 200);
        assertEquals(2, tree(r).at("/data/screens").size());
        assertEquals("discover", tree(r).at("/data/screens/0").asText());
      }
      case 227 -> {
        r = request("GET", "/users?q=Alex", login("admin"), null);
        collect(actual, r, 200);
        assertEquals(1, tree(r).at("/data/accounts").size());
      }
      case 228 -> {
        auth = login("admin");
        collect(actual, request("GET", "/users/form", auth, null), 200);
        r =
            request(
                "POST",
                "/users",
                auth,
                Map.of(
                    "email",
                    "newmanager@example.com",
                    "password",
                    "ExamplePassword1!",
                    "confirmPassword",
                    "ExamplePassword1!",
                    "role",
                    "manager"));
        collect(actual, r, 201);
        assertEquals(
            "manager",
            db.required("SELECT role FROM users WHERE email=?", "newmanager@example.com")
                .get("role"));
      }
      case 229 -> {
        r = request("PATCH", "/users/c1", login("admin"), Map.of("status", "suspended"));
        collect(actual, r, 200);
        assertEquals(
            "suspended", db.required("SELECT status FROM users WHERE id='c1'").get("status"));
      }
      case 230 -> {
        auth = login("admin");
        collect(actual, request("GET", "/users/c1/deletion-confirmation", auth, null), 200);
        collect(actual, request("DELETE", "/users/c1", auth, null), 200);
        assertEquals(0, db.count("SELECT COUNT(*) FROM users WHERE id='c1'"));
      }
      case 256 -> {
        String id = candidate();
        auth = login("admin");
        collect(actual, request("GET", "/users/" + id + "/verification", auth, null), 200);
        collect(
            actual,
            request("PUT", "/users/" + id + "/verification", auth, Map.of("decision", "approve")),
            200);
        assertNotNull(
            db.required("SELECT verified_at FROM users WHERE id=?", id).get("verified_at"));
      }
      case 231 -> {
        auth = login("designer");
        collect(actual, request("GET", "/me/company", auth, null), 200);
        collect(
            actual,
            request(
                "PUT",
                "/me/company",
                auth,
                Map.of(
                    "name",
                    "Casa Updated",
                    "description",
                    "Updated details",
                    "uen",
                    "UPDATED123",
                    "year",
                    2020)),
            200);
        assertEquals(
            "Casa Updated",
            db.required("SELECT name FROM companies WHERE owner_id='d1'").get("name"));
      }
      case 232 -> {
        r = request("GET", "/companies/casa", null, null);
        collect(actual, r, 200);
        assertEquals("Casa Atelier", tree(r).at("/data/company/name").asText());
        assertFalse(tree(r).at("/data/company").has("proofUrl"));
      }
      case 233 -> {
        r = request("POST", "/projects", login("designer"), idp());
        collect(actual, r, 201);
        String id = tree(r).at("/data/id").asText();
        assertEquals(0, db.count("SELECT COUNT(*) FROM project_photos WHERE project_id=?", id));
        assertEquals(0, db.count("SELECT COUNT(*) FROM project_categories WHERE project_id=?", id));
        assertEquals(
            0, db.required("SELECT published FROM projects WHERE id=?", id).get("published"));
      }
      case 234 -> {
        auth = login("designer");
        r =
            mvc.perform(
                    multipart(API + "/projects/p0/photos")
                        .file(new MockMultipartFile("photos", "one.png", "image/png", PNG))
                        .file(new MockMultipartFile("photos", "two.png", "image/png", PNG))
                        .param("coverIndex", "3")
                        .cookie(auth.cookie())
                        .header("X-CSRF-Token", auth.csrf()))
                .andReturn();
        collect(actual, r, 200);
        assertEquals(4, db.count("SELECT COUNT(*) FROM project_photos WHERE project_id='p0'"));
        assertEquals(
            3,
            db.required("SELECT position FROM project_photos WHERE project_id='p0' AND is_cover=1")
                .get("position"));
      }
      case 235 -> {
        r = request("PATCH", "/projects/p0", login("designer"), idp());
        collect(actual, r, 200);
        assertEquals(
            "A Java Home", db.required("SELECT title FROM projects WHERE id='p0'").get("title"));
      }
      case 236 -> {
        r = request("GET", "/projects?mine=true", login("designer"), null);
        collect(actual, r, 200);
        assertEquals(6, tree(r).at("/data/idps").size());
      }
      case 237 -> {
        r = request("GET", "/projects?q=Slow", login("customer"), null);
        collect(actual, r, 200);
        assertEquals(1, tree(r).at("/data/idps").size());
      }
      case 238 -> {
        r = request("GET", "/search-results?q=Slow", login("customer"), null);
        collect(actual, r, 200);
        assertEquals(1, tree(r).at("/data/idps").size());
      }
      case 239 -> {
        r = request("GET", "/projects/p0", login("customer"), null);
        collect(actual, r, 200);
        assertEquals(2, tree(r).at("/data/gallery").size());
        assertEquals(0, db.count("SELECT COUNT(*) FROM events WHERE type='view'"));
      }
      case 240 -> {
        r = request("GET", "/projects/p0/company", login("customer"), null);
        collect(actual, r, 200);
        assertEquals("Casa Atelier", tree(r).at("/data/company/name").asText());
      }
      case 242 -> {
        r = request("PUT", "/me/favourites/p0", login("customer"), null);
        collect(actual, r, 201);
        assertEquals(1, db.count("SELECT COUNT(*) FROM favourites"));
        assertEquals(0, db.count("SELECT COUNT(*) FROM events WHERE type='shortlist'"));
      }
      case 263 -> {
        r =
            request(
                "PUT",
                "/projects/p0/publication",
                login("designer"),
                Map.of("action", "Unpublish"));
        collect(actual, r, 200);
        assertEquals(
            0, db.required("SELECT published FROM projects WHERE id='p0'").get("published"));
      }
      case 266 -> {
        r =
            request(
                "PUT",
                "/projects/p0/categories",
                login("designer"),
                Map.of("categories", List.of("kitchen")));
        collect(actual, r, 200);
        assertEquals(
            "kitchen",
            db.required("SELECT category_id FROM project_categories WHERE project_id='p0'")
                .get("category_id"));
      }
      case 273 -> {
        favouriteSetup();
        r = request("GET", "/me/favourites", login("customer"), null);
        collect(actual, r, 200);
        assertEquals(1, tree(r).at("/data/idps").size());
      }
      case 241 -> {
        r = request("POST", "/projects/p0/views", login("customer"), null);
        collect(actual, r, 200);
        assertEquals(1, tree(r).at("/data/views").asInt());
      }
      case 243 -> {
        favouriteSetup();
        r = request("POST", "/projects/p0/shortlists", login("customer"), null);
        collect(actual, r, 200);
        assertEquals(1, tree(r).at("/data/shortlists").asInt());
      }
      case 244 -> {
        r = request("GET", "/me/analytics/totals", login("designer"), null);
        collect(actual, r, 200);
        assertEquals(0, tree(r).at("/data/totalViews").asInt());
      }
      case 245 -> {
        r = request("GET", "/me/analytics/projects", login("designer"), null);
        collect(actual, r, 200);
        assertEquals(6, tree(r).at("/data/projects").size());
      }
      case 246 -> {
        auth = login("manager");
        collect(actual, request("GET", "/categories/form", auth, null), 200);
        r =
            request(
                "POST",
                "/categories",
                auth,
                Map.of("name", "Study", "description", "Study room projects"));
        collect(actual, r, 201);
        assertEquals(
            "Study room projects",
            db.required("SELECT description FROM categories WHERE name='Study'")
                .get("description"));
      }
      case 247 -> {
        auth = login("manager");
        collect(actual, request("GET", "/categories", auth, null), 200);
        collect(actual, request("GET", "/categories/living/edit-form", auth, null), 200);
        collect(
            actual,
            request(
                "PATCH",
                "/categories/living",
                auth,
                Map.of("name", "Living & Dining", "description", "Combined spaces")),
            200);
        assertEquals(
            "Combined spaces",
            db.required("SELECT description FROM categories WHERE id='living'").get("description"));
      }
      case 248 -> {
        db.update("UPDATE categories SET active=0 WHERE id='living'");
        auth = login("manager");
        collect(actual, request("GET", "/categories?flow=availability", auth, null), 200);
        collect(
            actual,
            request("PUT", "/categories/living/availability", auth, Map.of("available", true)),
            200);
        assertEquals(
            1, db.required("SELECT active FROM categories WHERE id='living'").get("active"));
      }
      case 249 -> {
        auth = login("manager");
        collect(actual, request("GET", "/reports/options", auth, null), 200);
        r =
            request(
                "POST", "/reports/period", auth, Map.of("period", "weekly", "date", "2000-01-05"));
        collect(actual, r, 200);
        assertEquals("2000-01-03", tree(r).at("/data/from").asText());
        assertEquals("2000-01-09", tree(r).at("/data/to").asText());
      }
      case 250 -> {
        String today = LocalDate.now(ZoneId.of("Asia/Singapore")).toString();
        r = request("GET", "/reports/activity?period=daily&date=" + today, login("manager"), null);
        collect(actual, r, 200);
        assertEquals(5, tree(r).at("/data/totals/registration").asInt());
      }
      case 251 -> {
        String today = LocalDate.now(ZoneId.of("Asia/Singapore")).toString();
        r = request("GET", "/reports/table?period=weekly&date=" + today, login("manager"), null);
        collect(actual, r, 200);
        assertEquals(8, tree(r).at("/data/rows").size());
        assertEquals("Total", tree(r).at("/data/rows/7/date").asText());
      }
      default -> fail("Unimplemented sprint contract " + story);
    }
    List<String> expectedCalls = new ArrayList<>();
    expected.forEach(v -> expectedCalls.add(v.asText()));
    assertEquals(
        expectedCalls,
        actual,
        "Numbered business calls must match the supplied sequence for #" + story);
  }

  @Test
  void duplicateFavouriteDoesNotWriteAgain() throws Exception {
    favouriteSetup();
    var r = request("PUT", "/me/favourites/p0", login("customer"), null);
    status(200, r);
    assertEquals(
        List.of(
            "IDPDetailPage.saveToFavourites",
            "SaveFavouriteController.saveFavourite",
            "Favourite.isFavourited",
            "IDPDetailPage.displayMessage"),
        calls(r));
    assertEquals(0, tree(r).at("/data/result").asInt());
    assertEquals(1, db.count("SELECT COUNT(*) FROM favourites"));
  }

  @Test
  void duplicateShortlistStopsBeforeSaveAndCount() throws Exception {
    favouriteSetup();
    var auth = login("customer");
    status(200, request("POST", "/projects/p0/shortlists", auth, null));
    var r = request("POST", "/projects/p0/shortlists", auth, null);
    status(200, r);
    assertEquals(
        List.of(
            "IDPDetailPage.saveIDP",
            "RecordShortlistController.recordShortlist",
            "IDPShortlist.isShortlisted",
            "IDPDetailPage.logError"),
        calls(r));
    assertEquals(1, db.count("SELECT COUNT(*) FROM events WHERE type='shortlist'"));
  }

  @Test
  void hiddenProjectDoesNotRecordAView() throws Exception {
    db.update("UPDATE projects SET published=0 WHERE id='p0'");
    var r = request("POST", "/projects/p0/views", login("customer"), null);
    status(200, r);
    assertEquals(
        List.of("IDPDetailPage.openIDP", "RecordViewController.recordView", "IDP.isPublished"),
        calls(r));
    assertEquals(0, db.count("SELECT COUNT(*) FROM events"));
  }

  @Test
  void emptyCategorySelectionStopsBeforePersistence() throws Exception {
    var r =
        request(
            "PUT", "/projects/p0/categories", login("designer"), Map.of("categories", List.of()));
    status(400, r);
    assertFalse(calls(r).contains("IDPCategory.saveCategories"));
    assertEquals(2, db.count("SELECT COUNT(*) FROM project_categories WHERE project_id='p0'"));
  }

  @Test
  void categoryDuplicateAndBlankHaveDifferentStoppingPoints() throws Exception {
    var auth = login("manager");
    var blank = request("POST", "/categories", auth, Map.of("name", "", "description", "Empty"));
    status(400, blank);
    assertFalse(calls(blank).contains("CreateCategoryController.createCategory"));
    var duplicate =
        request(
            "POST", "/categories", auth, Map.of("name", "Living Room", "description", "Duplicate"));
    status(409, duplicate);
    assertTrue(calls(duplicate).contains("Category.nameExists"));
    assertFalse(calls(duplicate).contains("Category.saveCategory"));
  }

  @Test
  void deleteCancellationKeepsAccount() throws Exception {
    var r = request("POST", "/users/c1/deletion-cancellations", login("admin"), null);
    status(200, r);
    assertEquals(List.of("ManageAccountPage.cancel", "ManageAccountPage.refreshView"), calls(r));
    assertEquals(1, db.count("SELECT COUNT(*) FROM users WHERE id='c1'"));
  }

  @Test
  void rejectionDoesNotEnableDesignerPublishing() throws Exception {
    String id = candidate();
    var r =
        request(
            "PUT", "/users/" + id + "/verification", login("admin"), Map.of("decision", "reject"));
    status(200, r);
    assertTrue(calls(r).contains("ReviewDesignerPage.clickReject"));
    assertFalse(calls(r).contains("InteriorDesigner.enablePublish"));
    assertEquals("rejected", db.required("SELECT status FROM users WHERE id=?", id).get("status"));
  }

  @Test
  void absentAndFutureReportDatesCannotGenerate() throws Exception {
    var auth = login("manager");
    var missing = request("POST", "/reports/period", auth, Map.of("period", "daily", "date", ""));
    status(400, missing);
    assertEquals(List.of("ReportPage.selectPeriod", "ReportPage.displayError"), calls(missing));
    String future = LocalDate.now(ZoneId.of("Asia/Singapore")).plusDays(1).toString();
    status(
        400, request("POST", "/reports/period", auth, Map.of("period", "daily", "date", future)));
    status(400, request("GET", "/reports/activity?period=daily&date=" + future, auth, null));
    status(400, request("GET", "/reports/table?period=daily&date=" + future, auth, null));
  }

  @Test
  void emptyReportsDisplayNoActivity() throws Exception {
    var auth = login("manager");
    var totals = request("GET", "/reports/activity?period=daily&date=2000-01-01", auth, null);
    status(200, totals);
    assertTrue(calls(totals).contains("ReportPage.displayMessage"));
    assertEquals(0, tree(totals).at("/data/totals/project").asInt());
    var table = request("GET", "/reports/table?period=daily&date=2000-01-01", auth, null);
    status(200, table);
    assertEquals(0, tree(table).at("/data/rows").size());
    assertFalse(calls(table).contains("ViewReportTableController.calculateTotalRow"));
  }

  @Test
  void reportCountsFirstPublicationRatherThanDraftCreation() throws Exception {
    db.update("UPDATE projects SET published_at=NULL");
    var designer = login("designer");
    var created = request("POST", "/projects", designer, idp());
    status(201, created);
    String id = tree(created).at("/data/id").asText();
    var manager = login("manager");
    String today = LocalDate.now(ZoneId.of("Asia/Singapore")).toString();
    String path = "/reports/activity?period=daily&date=" + today;
    var draft = request("GET", path, manager, null);
    status(200, draft);
    assertEquals(0, tree(draft).at("/data/totals/project").asInt());
    status(
        200,
        request("PUT", "/projects/" + id + "/publication", designer, Map.of("action", "Publish")));
    var published = request("GET", path, manager, null);
    assertEquals(1, tree(published).at("/data/totals/project").asInt());
  }

  @Test
  void emptySearchUsesDescriptionMessage() throws Exception {
    var r = request("GET", "/search-results?q=NoMatchingWord", login("customer"), null);
    status(200, r);
    assertTrue(calls(r).contains("SearchResultsPage.displayMessage"));
    assertEquals(0, tree(r).at("/data/idps").size());
  }

  @Test
  void passwordsMustMatchForAdminCreation() throws Exception {
    var r =
        request(
            "POST",
            "/users",
            login("admin"),
            Map.of(
                "email",
                "bad@example.com",
                "password",
                "ExamplePassword1!",
                "confirmPassword",
                "DifferentPassword!",
                "role",
                "manager"));
    status(400, r);
    assertEquals(0, db.count("SELECT COUNT(*) FROM users WHERE email='bad@example.com'"));
  }

  @Test
  void rolesAndCsrfProtectMutations() throws Exception {
    var customer = login("customer");
    status(403, request("GET", "/users", customer, null));
    status(403, request("POST", "/projects", customer, idp()));
    status(403, mvc.perform(put(API + "/me/favourites/p0").cookie(customer.cookie())).andReturn());
    status(401, request("GET", "/navigation", null, null));
  }
}
