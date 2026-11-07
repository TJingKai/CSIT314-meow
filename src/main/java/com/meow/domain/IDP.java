package com.meow.domain;

import com.meow.repository.Database;
import java.util.Map;

/** IDP entity rules keep Current/Past separate from publication, archive and moderation state. */
public record IDP(
    String id,
    String ownerId,
    boolean published,
    boolean archived,
    String takedownReason,
    String ownerStatus,
    Map<String, Object> details) {
  public static IDP from(Map<String, Object> row) {
    return new IDP(
        Database.str(row, "id"),
        Database.str(row, "owner_id"),
        Database.flag(row, "published"),
        Database.flag(row, "archived"),
        Database.str(row, "takedown_reason"),
        Database.str(row, "owner_status"),
        new java.util.LinkedHashMap<>(row));
  }

  public IDP withDetails(Map<String, Object> details) {
    return new IDP(id, ownerId, published, archived, takedownReason, ownerStatus, details);
  }

  public boolean visible() {
    return published && !archived && takedownReason.isEmpty() && ownerStatus.equals("active");
  }
}
