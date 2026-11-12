package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ViewFavouritesController {
  protected final WorkflowContext context;
  private final com.meow.entity.Favourite favourite;

  public ViewFavouritesController(WorkflowContext context, com.meow.entity.Favourite favourite) {
    this.context = context;
    this.favourite = favourite;
  }

  public List<Map<String, Object>> getFavourites(String customerId) {
    context.call("ViewFavouritesController.getFavourites");
    return favourite.getFavouriteIDPs(customerId);  // step 3
  }
}
