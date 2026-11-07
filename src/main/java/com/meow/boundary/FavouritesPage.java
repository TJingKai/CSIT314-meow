package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class FavouritesPage {
  protected final WorkflowContext context;
  private final com.meow.control.ViewFavouritesController view;

  public FavouritesPage(WorkflowContext context, com.meow.control.ViewFavouritesController view) {
    this.context = context;
    this.view = view;
  }

  public void clickMyFavourites() {
    context.call("FavouritesPage.clickMyFavourites");
    displayFavourites(view.getFavourites(context.value("customerId")));
  }

  public void displayFavourites(List<Map<String, Object>> idps) {
    context.call("FavouritesPage.displayFavourites");
    context.state().output.put("idps", idps);
  }
}
