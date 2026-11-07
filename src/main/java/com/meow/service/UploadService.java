package com.meow.service;

import com.meow.api.ApiException;
import com.meow.repository.Database;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** Owned file resources, bounded uploads and private business-proof delivery. */
@Service
public class UploadService {
  private final Database db;
  private final Identity identity;
  private final Path directory;

  public UploadService(
      Database db, Identity identity, @Value("${meow.upload-directory}") String directory)
      throws IOException {
    this.db = db;
    this.identity = identity;
    this.directory = Path.of(directory).toAbsolutePath().normalize();
    Files.createDirectories(this.directory);
  }

  public String store(MultipartFile file, String ownerId, String kind) {
    if (file == null || file.isEmpty()) throw new ApiException("Select a file.");
    if (file.getSize() > 2 * 1024 * 1024)
      throw new ApiException(413, "file_too_large", "Files must be at most 2MB.");
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException error) {
      throw new ApiException("Could not read upload.");
    }
    String extension, mime;
    if (starts(bytes, new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10})) {
      extension = ".png";
      mime = "image/png";
    } else if (starts(bytes, new byte[] {(byte) 255, (byte) 216, (byte) 255})) {
      extension = ".jpg";
      mime = "image/jpeg";
    } else if (bytes.length >= 12
        && starts(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII))
        && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
      extension = ".webp";
      mime = "image/webp";
    } else if (kind.equals("proof") && starts(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
      extension = ".pdf";
      mime = "application/pdf";
    } else throw new ApiException("Use PNG, JPEG or WebP images; business proof also accepts PDF.");
    String id = Database.id();
    String storageName = id + extension;
    Path path = directory.resolve(storageName);
    try {
      Files.write(path, bytes);
    } catch (IOException error) {
      throw new IllegalStateException("Upload storage failed", error);
    }
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status != STATUS_COMMITTED) removeFile(storageName);
            }
          });
    }
    String name =
        file.getOriginalFilename() == null
            ? storageName
            : file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
    if (name.length() > 150) name = name.substring(name.length() - 150);
    db.update(
        "INSERT INTO uploads VALUES (?,?,?,?,?,?,?)",
        id,
        ownerId,
        name,
        storageName,
        mime,
        kind,
        Database.now());
    return id;
  }

  @org.springframework.transaction.annotation.Transactional
  public Map<String, Object> create(MultipartFile file) {
    String id = store(file, identity.user().id(), "image");
    return com.meow.api.Json.object("id", id, "url", "/api/v1/uploads/" + id + "/content");
  }

  private boolean starts(byte[] bytes, byte[] prefix) {
    return bytes.length >= prefix.length
        && Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
  }

  public Map<String, Object> owned(Object id, String kind) {
    if (!(id instanceof String)) throw new ApiException("Upload ID must be a string.");
    Map<String, Object> row =
        db.find(
            "SELECT * FROM uploads WHERE id=? AND owner_id=? AND kind=?",
            id,
            identity.user().id(),
            kind);
    if (row == null) throw new ApiException("Upload does not exist or does not belong to you.");
    return row;
  }

  public Map<String, Object> authorised(String id) {
    Map<String, Object> row = db.required("SELECT * FROM uploads WHERE id=?", id);
    boolean allowed =
        row.get("owner_id").equals(identity.user().id())
            || (row.get("kind").equals("proof") && identity.user().role().equals("admin"));
    if (!allowed && row.get("kind").equals("image")) {
      allowed =
          db.count(
                      "SELECT COUNT(*) FROM project_photos ph JOIN projects p ON p.id=ph.project_id JOIN users u ON u.id=p.owner_id WHERE ph.upload_id=? AND p.published=1 AND p.archived=0 AND p.takedown_reason='' AND u.status='active'",
                      id)
                  > 0
              || db.count(
                      "SELECT COUNT(*) FROM companies c JOIN users u ON u.id=c.owner_id WHERE (c.logo_id=? OR c.banner_id=?) AND u.status='active'",
                      id,
                      id)
                  > 0;
    }
    if (!allowed) throw new ApiException(403, "forbidden", "You do not have access to this file.");
    if (!Files.isRegularFile(path(Database.str(row, "storage_name"))))
      throw new ApiException(404, "not_found", "File not found.");
    return row;
  }

  public Path path(String name) {
    return directory.resolve(name);
  }

  public void removeFile(String name) {
    try {
      Files.deleteIfExists(directory.resolve(name));
    } catch (IOException ignored) {
      /* A missing/unavailable file cannot restore a deleted account. */
    }
  }
}
