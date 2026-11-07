package com.meow.control;

import com.meow.api.*;
import com.meow.repository.Database;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SaveFavouriteController {
  protected final WorkflowContext context;
  private final com.meow.entity.Favourite favourite;
  private final Database db;

  public SaveFavouriteController(
      WorkflowContext context, com.meow.entity.Favourite favourite, Database db) {
    this.context = context;
    this.favourite = favourite;
    this.db = db;
  }

  public int saveFavourite(String idpId, String customerId) {
    context.call("SaveFavouriteController.saveFavourite");
    db.required("SELECT id FROM projects WHERE id=? FOR UPDATE", idpId);
    if (favourite.isFavourited(idpId, customerId)) return 0;
    return favourite.saveFavourite(idpId, customerId) ? 1 : -1;
  }
}
