package com.meow.entity;

import com.meow.api.ApiException;
import com.meow.repository.Database;
import com.meow.service.*;
import com.meow.workflow.*;
import java.io.File;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class IDPPhoto extends DiagramEntity {
  private final UploadService uploads;

  public IDPPhoto(Database db, Identity identity, WorkflowContext context, UploadService uploads) {
    super(db, identity, context);
    this.uploads = uploads;
  }

  public boolean savePhotos(String idpId, File[] photos) {
    mark("IDPPhoto.savePhotos");
    identity.verified();
    owner(idpId);
    int position = (int) db.count("SELECT COUNT(*) FROM project_photos WHERE project_id=?", idpId);
    if (position + photos.length > 20) throw new ApiException("At most 20 photos are allowed.");
    if (position == 0 && photos.length == 0) throw new ApiException("Select at least one photo.");
    for (File photo : photos) {
      String upload = uploads.store(context.upload(photo), identity.user().id(), "image");
      db.update(
          "INSERT INTO project_photos VALUES (?,?,?,NULL,?,0)",
          Database.id(),
          idpId,
          upload,
          position++);
    }
    return true;
  }

  public List<Map<String, Object>> getPhotos(String idpId) {
    mark("IDPPhoto.getPhotos");
    var row = project(idpId);
    if (!visible(row) && !row.get("owner_id").equals(identity.user().id()))
      throw new ApiException(404, "not_found", "Project unavailable.");
    return db
        .rows("SELECT * FROM project_photos WHERE project_id=? ORDER BY position", idpId)
        .stream()
        .map(
            p ->
                com.meow.api.Json.object(
                    "id",
                    p.get("id"),
                    "url",
                    p.get("url") == null
                        ? "/api/v1/uploads/" + p.get("upload_id") + "/content"
                        : p.get("url"),
                    "cover",
                    Database.flag(p, "is_cover")))
        .toList();
  }
}
