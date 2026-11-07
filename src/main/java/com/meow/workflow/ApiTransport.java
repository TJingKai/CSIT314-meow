package com.meow.workflow;

import com.meow.api.*;
import com.meow.repository.Database;
import com.meow.service.Identity;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

/** HTTP/JSON/file adaptation is separate from the numbered BCE business-call sequences. */
public abstract class ApiTransport {
  protected final WorkflowContext context;
  protected final Database db;
  protected final Identity identity;

  @Value("${meow.sequence-trace:false}")
  private boolean exposeTrace;

  protected ApiTransport(WorkflowContext context, Database db, Identity identity) {
    this.context = context;
    this.db = db;
    this.identity = identity;
  }

  protected void input(Map<String, ?> input) {
    context.state().input.putAll(input);
    if (identity.optional() != null) {
      context.state().input.put("designerId", identity.user().id());
      context.state().input.put("customerId", identity.user().id());
    }
  }

  protected ResponseEntity<Map<String, Object>> response() {
    return response(200);
  }

  protected ResponseEntity<Map<String, Object>> response(int success) {
    if (context.state().createdId != null)
      context.state().output.put("id", context.state().createdId);
    Map<String, Object> envelope =
        Json.object("data", context.state().output, "messages", context.state().messages);
    if (exposeTrace) {
      envelope.put("sequence", List.copyOf(context.state().calls));
      envelope.put("infrastructure", List.copyOf(context.state().infrastructureCalls));
    }
    return ResponseEntity.status(context.state().status == 200 ? success : context.state().status)
        .body(envelope);
  }

  protected File attach(MultipartFile upload) throws IOException {
    if (upload == null || upload.isEmpty()) throw new ApiException("Select a file.");
    if (upload.getSize() > 2 * 1024 * 1024)
      throw new ApiException(413, "file_too_large", "Files must be at most 2MB.");
    File file = Files.createTempFile("meow-upload-", ".bin").toFile();
    try {
      upload.transferTo(file.toPath());
      context.state().files.put(file, upload);
      return file;
    } catch (Exception error) {
      file.delete();
      throw error;
    }
  }

  protected void cleanFiles() {
    context
        .state()
        .files
        .keySet()
        .forEach(
            file -> {
              try {
                Files.deleteIfExists(file.toPath());
              } catch (IOException ignored) {
              }
            });
    context.state().files.clear();
  }

  protected String text(Map<String, ?> input, String field) {
    return Inputs.text(
        input,
        field,
        true,
        field.equals("description") || field.equals("projectDetails") ? 5000 : 150);
  }
}
