package com.meow.control;

import com.meow.api.*;
import com.meow.workflow.*;
import java.io.File;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UploadPhotosController {
  protected final WorkflowContext context;
  private final com.meow.entity.IDPPhoto photo;
  private final com.meow.entity.IDP idp;

  public UploadPhotosController(
      WorkflowContext context, com.meow.entity.IDPPhoto photo, com.meow.entity.IDP idp) {
    this.context = context;
    this.photo = photo;
    this.idp = idp;
  }

  public boolean uploadPhotos(String idpId, File[] photos, int coverIndex) {
    context.call("UploadPhotosController.uploadPhotos");
    boolean saved = photo.savePhotos(idpId, photos);  // step 3
    if (!saved) return false;
    return idp.setCoverImage(idpId, coverIndex);  // step 4a, 4b
  }
}
