package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ViewIDPDetailsController {
  protected final WorkflowContext context;
  private final com.meow.entity.IDP idp;
  private final com.meow.entity.IDPPhoto photos;

  public ViewIDPDetailsController(
      WorkflowContext context, com.meow.entity.IDP idp, com.meow.entity.IDPPhoto photos) {
    this.context = context;
    this.idp = idp;
    this.photos = photos;
  }

  public com.meow.domain.IDP getIDPDetails(String idpId) {
    context.call("ViewIDPDetailsController.getIDPDetails");
    return idp.getIDP(idpId);  // step 3
  }

  public List<Map<String, Object>> getGallery(String idpId) {
    context.call("ViewIDPDetailsController.getGallery");
    return photos.getPhotos(idpId);  // step 5a
  }
}
