package com.meow.entity;

import com.meow.api.ApiException;
import com.meow.repository.Database;
import com.meow.service.Identity;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class IDPCategory extends DiagramEntity {
  public IDPCategory(Database db, Identity identity, WorkflowContext context) {
    super(db, identity, context);
  }

  public boolean saveCategories(String idpId, List<String> categoryIds) {
    mark("IDPCategory.saveCategories");
    owner(idpId);
    if (categoryIds.isEmpty()) return false;
    for (String category : new LinkedHashSet<>(categoryIds))
      if (db.count("SELECT COUNT(*) FROM categories WHERE id=? AND active=1", category) == 0)
        throw new ApiException("Choose available categories only.");
    db.update("DELETE FROM project_categories WHERE project_id=?", idpId);
    for (String category : new LinkedHashSet<>(categoryIds))
      db.update("INSERT INTO project_categories VALUES (?,?)", idpId, category);
    return true;
  }
}
