package com.meow.domain;

import static com.meow.api.Json.object;

import com.meow.repository.Database;
import java.util.Map;

/** User entity projection. Password hashes are never included in its public representation. */
public record User(
    String id,
    String name,
    String email,
    String role,
    String status,
    String passwordHash,
    String verifiedAt,
    String contact,
    String preferences) {
  public static User from(Map<String, Object> row) {
    return new User(
        Database.str(row, "id"),
        Database.str(row, "name"),
        Database.str(row, "email"),
        Database.str(row, "role"),
        Database.str(row, "status"),
        Database.str(row, "password_hash"),
        Database.str(row, "verified_at"),
        Database.str(row, "contact"),
        Database.str(row, "preferences"));
  }

  public boolean canLogin() {
    return status.equals("active") || status.equals("pending");
  }

  public boolean verifiedDesigner() {
    return role.equals("designer") && status.equals("active") && !verifiedAt.isEmpty();
  }

  public Map<String, Object> publicView() {
    return object(
        "id",
        id,
        "name",
        name,
        "email",
        email,
        "role",
        role,
        "status",
        status,
        "contact",
        contact,
        "preferences",
        preferences);
  }
}
