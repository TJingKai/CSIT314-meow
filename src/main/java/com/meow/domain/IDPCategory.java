package com.meow.domain;

import com.meow.api.Json;
import com.meow.repository.Database;
import java.util.Map;

public record IDPCategory(String id, String name, boolean active) {
  public static IDPCategory from(Map<String, Object> row) {
    return new IDPCategory(
        Database.str(row, "id"), Database.str(row, "name"), Database.flag(row, "active"));
  }

  public Map<String, Object> view() {
    return Json.object("id", id, "name", name, "active", active);
  }
}
