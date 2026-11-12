package com.meow.entity;

import com.meow.repository.Database;
import com.meow.service.Identity;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class Category extends DiagramEntity {
  public Category(Database db, Identity identity, WorkflowContext context) {
    super(db, identity, context);
  }

  public boolean nameExists(String name) {
    mark("Category.nameExists");
    return db.count("SELECT COUNT(*) FROM categories WHERE name=?", name) > 0;
  }

  public boolean nameExists(String name, String categoryId) {
    mark("Category.nameExists");
    return db.count("SELECT COUNT(*) FROM categories WHERE name=? AND id<>?", name, categoryId) > 0;
  }

  public boolean saveCategory(String name, String description) {
    mark("Category.saveCategory");
    String id = Database.id();
    boolean saved =
        db.update(
                "INSERT INTO categories(id,name,description,active) VALUES (?,?,?,1)",
                id,
                name,
                description)
            == 1;
    context.state().createdId = id;
    return saved;
  }

  public List<Map<String, Object>> getAllCategories() {
    mark("Category.getAllCategories");
    return db.rows("SELECT id,name,description,active FROM categories ORDER BY name").stream()
        .map(
            row -> {
              row.put("active", Database.flag(row, "active"));
              return row;
            })
        .toList();
  }

  public boolean updateCategory(String categoryId, String name, String description) {
    mark("Category.updateCategory");
    return db.update(
            "UPDATE categories SET name=?,description=? WHERE id=?", name, description, categoryId)
        == 1;
  }

  public boolean updateAvailability(String categoryId, boolean available) {
    mark("Category.updateAvailability");
    return db.update("UPDATE categories SET active=? WHERE id=?", available ? 1 : 0, categoryId)
        == 1;
  }
}
