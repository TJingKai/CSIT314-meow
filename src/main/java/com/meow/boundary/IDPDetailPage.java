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
    context.call("IDPDetailPage.selectIDP");  // step 1
    var idp = details.getIDPDetails(idpId);  // step 2
    var photos = details.getGallery(idpId);  // step 4a
    displayIDPDetails(idp);  // step 6a
    displayGallery(photos);  // step 7a
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
    context.call("IDPDetailPage.saveToFavourites");  // step 1
    int result = favourite.saveFavourite(idpId, customerId);  // step 2
    context.state().output.put("result", result);
    if (result == 0) displayMessage("Already in your favourites");  // step 4b
    else if (result == 1) displayMessage("Saved to favourites");  // step 5a
    else throw new ApiException("Favourite could not be saved.");
  }

  public void openIDP(String idpId) {
    context.call("IDPDetailPage.openIDP");  // step 1
    int result = views.recordView(idpId);  // step 2
    context.state().output.put("result", result);
    if (result == -1) logError("Failed to record view");
    else if (result > 0) displayViewCount(result);  // step 6a
  }

  public void displayViewCount(int count) {
    context.call("IDPDetailPage.displayViewCount");
    context.state().output.put("views", count);
  }

  public void saveIDP(String idpId, String userId) {
    context.call("IDPDetailPage.saveIDP");  // step 1
    int result = shortlists.recordShortlist(idpId, userId);  // step 2
    context.state().output.put("result", result);
    if (result == 0) logError("Duplicate shortlist not recorded");  // step 4b
    else if (result == -1) logError("Failed to record shortlist");  // step 4b
    else displayShortlistCount(result);  // step 6a
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
