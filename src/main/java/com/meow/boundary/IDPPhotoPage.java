package com.meow.boundary;

import com.meow.api.*;
import com.meow.workflow.*;
import java.io.File;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class IDPPhotoPage {
  protected final WorkflowContext context;
  private final com.meow.control.UploadPhotosController upload;

  public IDPPhotoPage(WorkflowContext context, com.meow.control.UploadPhotosController upload) {
    this.context = context;
    this.upload = upload;
  }

  public void submitPhotos(File[] photos, int coverIndex) {
    context.call("IDPPhotoPage.submitPhotos");  // step 1
    if (upload.uploadPhotos(context.state().selectedId, photos, coverIndex))  // step 2
      displayMessage("Photos uploaded successfully");  // step 5a
    else throw new ApiException("Photos could not be saved.");
  }

  public void displayMessage(String message) {
    context.call("IDPPhotoPage.displayMessage");
    context.message("success", message);
  }
}
