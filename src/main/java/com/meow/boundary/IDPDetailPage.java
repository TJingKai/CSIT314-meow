package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class IDPDetailPage {
  protected final WorkflowContext context;
  private final com.meow.control.ViewIDPDetailsController details;
  private final com.meow.control.SaveFavouriteController favourite;
  private final com.meow.control.RecordViewController views;
  private final com.meow.control.RecordShortlistController shortlists;

  public IDPDetailPage(
      WorkflowContext context,
      com.meow.control.ViewIDPDetailsController details,
      com.meow.control.SaveFavouriteController favourite,
      com.meow.control.RecordViewController views,
      com.meow.control.RecordShortlistController shortlists) {
    this.context = context;
    this.details = details;
    this.favourite = favourite;
    this.views = views;
    this.shortlists = shortlists;
  }

  public void selectIDP(String idpId) {
    context.call("IDPDetailPage.selectIDP");
    var idp = details.getIDPDetails(idpId);
    var photos = details.getGallery(idpId);
    displayIDPDetails(idp);
    displayGallery(photos);
  }

  public void displayIDPDetails(com.meow.domain.IDP idp) {
    context.call("IDPDetailPage.displayIDPDetails");
    context.state().output.put("idp", idp.details());
  }

  public void displayGallery(List<Map<String, Object>> photos) {
    context.call("IDPDetailPage.displayGallery");
    context.state().output.put("gallery", photos);
  }

  public void saveToFavourites(String idpId, String customerId) {
    context.call("IDPDetailPage.saveToFavourites");
    int result = favourite.saveFavourite(idpId, customerId);
    context.state().output.put("result", result);
    if (result == 0) displayMessage("Already in your favourites");
    else if (result == 1) displayMessage("Saved to favourites");
    else throw new ApiException("Favourite could not be saved.");
  }

  public void openIDP(String idpId) {
    context.call("IDPDetailPage.openIDP");
    int result = views.recordView(idpId);
    context.state().output.put("result", result);
    if (result == -1) logError("Failed to record view");
    else if (result > 0) displayViewCount(result);
  }

  public void displayViewCount(int count) {
    context.call("IDPDetailPage.displayViewCount");
    context.state().output.put("views", count);
  }

  public void saveIDP(String idpId, String userId) {
    context.call("IDPDetailPage.saveIDP");
    int result = shortlists.recordShortlist(idpId, userId);
    context.state().output.put("result", result);
    if (result == 0) logError("Duplicate shortlist not recorded");
    else if (result == -1) logError("Failed to record shortlist");
    else displayShortlistCount(result);
  }

  public void displayShortlistCount(int count) {
    context.call("IDPDetailPage.displayShortlistCount");
    context.state().output.put("shortlists", count);
  }

  public void displayMessage(String message) {
    context.call("IDPDetailPage.displayMessage");
    context.message("notice", message);
  }

  public void logError(String message) {
    context.call("IDPDetailPage.logError");
    context.message("log", message);
  }
}
