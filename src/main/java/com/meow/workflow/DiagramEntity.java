package com.meow.workflow;

import com.meow.api.ApiException;
import com.meow.repository.Database;
import com.meow.service.Identity;
import java.util.*;

/** Shared infrastructure only; the report's entity methods perform the business operations. */
public abstract class DiagramEntity {
  protected final Database db;
  protected final Identity identity;
  protected final WorkflowContext context;

  protected DiagramEntity(Database db, Identity identity, WorkflowContext context) {
    this.db = db;
    this.identity = identity;
    this.context = context;
  }

  protected void mark(String name) {
    context.call(name);
  }

  protected Map<String, Object> project(String id) {
    return db.required(
        "SELECT p.*,u.status AS owner_status FROM projects p JOIN users u ON u.id=p.owner_id WHERE p.id=?",
        id);
  }

  protected void owner(String id) {
    if (!project(id).get("owner_id").equals(identity.user().id()))
      throw new ApiException(403, "forbidden", "You do not own this IDP.");
  }

  protected boolean visible(Map<String, Object> row) {
    return com.meow.domain.IDP.from(row).visible();
  }

  protected Map<String, Object> idpView(Map<String, Object> row) {
    Map<String, Object> data = new LinkedHashMap<>(row);
    String id = Database.str(row, "id");
    data.put("owner", data.remove("owner_id"));
    data.remove("owner_status");
    data.put("published", Database.flag(row, "published"));
    data.put("archived", Database.flag(row, "archived"));
    data.put("created", data.remove("created_at"));
    data.put("updated", data.remove("updated_at"));
    data.put("takenDown", data.remove("takedown_reason"));
    data.put("projectDetails", data.remove("project_details"));
    List<Map<String, Object>> photos =
        db.rows("SELECT * FROM project_photos WHERE project_id=? ORDER BY position", id);
    data.put(
        "photos",
        photos.stream()
            .map(
                p ->
                    p.get("url") == null
                        ? "/api/v1/uploads/" + p.get("upload_id") + "/content"
                        : p.get("url"))
            .toList());
    data.put("photoIds", photos.stream().map(p -> p.get("id")).toList());
    int cover = 0;
    for (int i = 0; i < photos.size(); i++)
      if (Database.flag(photos.get(i), "is_cover")) {
        cover = i;
        break;
      }
    data.put("cover", cover);
    data.put(
        "categories",
        db.rows("SELECT category_id FROM project_categories WHERE project_id=?", id).stream()
            .map(c -> c.get("category_id"))
            .toList());
    data.put(
        "company",
        db.find("SELECT id,name,owner_id FROM companies WHERE owner_id=?", row.get("owner_id")));
    data.put(
        "views", db.count("SELECT COUNT(*) FROM events WHERE project_id=? AND type='view'", id));
    data.put(
        "shortlists",
        db.count("SELECT COUNT(*) FROM events WHERE project_id=? AND type='shortlist'", id));
    data.put(
        "saved",
        identity.optional() != null
            && db.count(
                    "SELECT COUNT(*) FROM favourites WHERE user_id=? AND project_id=?",
                    identity.user().id(),
                    id)
                > 0);
    return data;
  }

  protected void event(String type, String user, String idp) {
    db.update(
        "INSERT INTO events VALUES (?,?,?,?,?)", Database.id(), user, idp, type, Database.now());
  }
}
