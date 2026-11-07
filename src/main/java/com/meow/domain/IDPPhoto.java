package com.meow.domain;

import com.meow.repository.Database;
import java.util.Map;

/** An ordered portfolio photo with an explicit cover selection. */
public record IDPPhoto(
    String id, String projectId, String uploadId, String externalUrl, int position, boolean cover) {
  public static IDPPhoto from(Map<String, Object> row) {
    return new IDPPhoto(
        Database.str(row, "id"),
        Database.str(row, "project_id"),
        Database.str(row, "upload_id"),
        Database.str(row, "url"),
        ((Number) row.get("position")).intValue(),
        Database.flag(row, "is_cover"));
  }

  public String url() {
    return externalUrl.isEmpty() ? "/api/v1/uploads/" + uploadId + "/content" : externalUrl;
  }
}
