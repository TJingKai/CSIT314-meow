package com.meow.api;

import com.meow.boundary.*;
import com.meow.config.RequireRole;
import com.meow.repository.Database;
import com.meow.service.Identity;
import com.meow.workflow.*;
import java.io.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequireRole
public class SprintTwoApi extends ApiTransport {
  private final CreateIDPPage create;
  private final IDPPhotoPage photos;
  private final EditIDPPage edit;
  private final MyProjectsPage mine;
  private final SearchPage search;
  private final SearchResultsPage results;
  private final IDPDetailPage details;
  private final CompanyProfilePage company;
  private final MyIDPPage publication;
  private final IDPCategoryPage categories;
  private final FavouritesPage favourites;

  public SprintTwoApi(
      WorkflowContext context,
      Database db,
      Identity identity,
      CreateIDPPage create,
      IDPPhotoPage photos,
      EditIDPPage edit,
      MyProjectsPage mine,
      SearchPage search,
      SearchResultsPage results,
      IDPDetailPage details,
      CompanyProfilePage company,
      MyIDPPage publication,
      IDPCategoryPage categories,
      FavouritesPage favourites) {
    super(context, db, identity);
    this.create = create;
    this.photos = photos;
    this.edit = edit;
    this.mine = mine;
    this.search = search;
    this.results = results;
    this.details = details;
    this.company = company;
    this.publication = publication;
    this.categories = categories;
    this.favourites = favourites;
  }

  @GetMapping("/projects")
  public ResponseEntity<?> projects(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "false") boolean mine,
      @RequestParam(defaultValue = "false") boolean favourites) {
    input(Map.of());
    if (mine) {
      if (!identity.user().role().equals("designer"))
        throw new ApiException(403, "forbidden", "Designer access required.");
      this.mine.clickMyProjects();
    } else if (favourites) {
      if (!identity.user().role().equals("customer"))
        throw new ApiException(403, "forbidden", "Customer access required.");
      this.favourites.clickMyFavourites();
    } else search.searchIDP(q);
    return response();
  }

  @PostMapping("/projects")
  @RequireRole("designer")
  public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
    identity.verified();
    input(body);
    create.submitIDP(
        text(body, "title"),
        text(body, "description"),
        text(body, "projectDetails"),
        text(body, "status"));
    return response(201);
  }

  @PostMapping(value = "/projects/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequireRole("designer")
  public ResponseEntity<?> photos(
      @PathVariable String id,
      @RequestPart(value = "photos", required = false) List<MultipartFile> files,
      @RequestParam(defaultValue = "0") int coverIndex)
      throws IOException {
    identity.verified();
    context.state().selectedId = id;
    try {
      if (files != null && files.size() > 20)
        throw new ApiException("At most 20 photos are allowed.");
      List<File> attached = new ArrayList<>();
      if (files != null)
        for (MultipartFile file : files) if (!file.isEmpty()) attached.add(attach(file));
      photos.submitPhotos(attached.toArray(File[]::new), coverIndex);
      return response();
    } finally {
      cleanFiles();
    }
  }

  @PatchMapping("/projects/{id}")
  @RequireRole("designer")
  public ResponseEntity<?> edit(@PathVariable String id, @RequestBody Map<String, Object> body) {
    identity.verified();
    input(body);
    edit.saveChanges(
        id,
        text(body, "title"),
        text(body, "description"),
        text(body, "projectDetails"),
        text(body, "status"));
    return response();
  }

  @GetMapping("/search-results")
  public ResponseEntity<?> results(@RequestParam(defaultValue = "") String q) {
    results.submitSearch(q);
    return response();
  }

  @GetMapping("/projects/{id}")
  public ResponseEntity<?> details(@PathVariable String id) {
    details.selectIDP(id);
    return response();
  }

  @GetMapping("/projects/{id}/company")
  @RequireRole("customer")
  public ResponseEntity<?> company(@PathVariable String id) {
    company.clickCompanyName(id);
    return response();
  }

  @PutMapping("/me/favourites/{id}")
  @RequireRole("customer")
  public ResponseEntity<?> save(@PathVariable String id) {
    details.saveToFavourites(id, identity.user().id());
    return response(Integer.valueOf(1).equals(context.state().output.get("result")) ? 201 : 200);
  }

  @PutMapping("/projects/{id}/publication")
  @RequireRole("designer")
  public ResponseEntity<?> publication(
      @PathVariable String id, @RequestBody Map<String, Object> body) {
    identity.verified();
    String action =
        body.containsKey("action")
            ? text(body, "action")
            : Inputs.bool(body, "published") ? "Publish" : "Unpublish";
    publication.changePublishStatus(id, action);
    return response();
  }

  @PutMapping("/projects/{id}/categories")
  @RequireRole("designer")
  public ResponseEntity<?> categories(
      @PathVariable String id, @RequestBody Map<String, Object> body) {
    identity.verified();
    categories.saveCategories(id, Inputs.strings(body, "categories"));
    return response();
  }

  @GetMapping("/me/favourites")
  @RequireRole("customer")
  public ResponseEntity<?> favourites() {
    input(Map.of());
    favourites.clickMyFavourites();
    return response();
  }

  @GetMapping("/categories/available")
  @RequireRole("designer")
  public Map<String, Object> available() {
    return Json.data(
        db.rows("SELECT id,name,description FROM categories WHERE active=1 ORDER BY name"));
  }
}
