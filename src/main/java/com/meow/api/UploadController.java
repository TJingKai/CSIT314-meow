package com.meow.api;

import com.meow.config.RequireRole;
import com.meow.repository.Database;
import com.meow.service.UploadService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/uploads")
@RequireRole
public class UploadController {
  private final UploadService uploads;

  public UploadController(UploadService uploads) {
    this.uploads = uploads;
  }

  @GetMapping("/{id}/content")
  public ResponseEntity<Resource> content(@PathVariable String id) {
    Map<String, Object> row = uploads.authorised(id);
    ContentDisposition disposition =
        (row.get("kind").equals("proof")
                ? ContentDisposition.attachment()
                : ContentDisposition.inline())
            .filename(Database.str(row, "filename"), StandardCharsets.UTF_8)
            .build();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(Database.str(row, "mime_type")))
        .header("Content-Disposition", disposition.toString())
        .body(new FileSystemResource(uploads.path(Database.str(row, "storage_name"))));
  }
}
