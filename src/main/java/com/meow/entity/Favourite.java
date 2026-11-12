package com.meow.entity;

import com.meow.api.ApiException;
import com.meow.repository.Database;
import com.meow.service.Identity;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class Favourite extends DiagramEntity {
  public Favourite(Database db, Identity identity, WorkflowContext context) {
    super(db, identity, context);
  }

  public boolean isFavourited(String idpId, String customerId) {
    mark("Favourite.isFavourited");
    return db.count(
            "SELECT COUNT(*) FROM favourites WHERE project_id=? AND user_id=?", idpId, customerId)
        > 0;
  }

  public boolean saveFavourite(String idpId, String customerId) {
    mark("Favourite.saveFavourite");
    if (!customerId.equals(identity.user().id()))
      throw new ApiException(403, "forbidden", "You may only save your own favourites.");
    if (!visible(project(idpId))) throw new ApiException(404, "not_found", "Project unavailable.");
    return db.update("INSERT INTO favourites VALUES (?,?,?)", customerId, idpId, Database.now())
        == 1;
  }

  public List<Map<String, Object>> getFavouriteIDPs(String customerId) {
    mark("Favourite.getFavouriteIDPs");
    if (!customerId.equals(identity.user().id()))
      throw new ApiException(403, "forbidden", "You may only view your own favourites.");
    return db
        .rows(
            "SELECT p.*,u.status AS owner_status FROM favourites f JOIN projects p ON p.id=f.project_id JOIN users u ON u.id=p.owner_id WHERE f.user_id=? AND p.published=1 AND p.archived=0 AND p.takedown_reason='' AND u.status='active' ORDER BY f.created_at",
            customerId)
        .stream()
        .map(this::idpView)
        .toList();
  }
}
