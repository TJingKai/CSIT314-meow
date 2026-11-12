package com.meow.entity;

import com.meow.api.*;
import com.meow.repository.Database;
import com.meow.service.Identity;
import com.meow.workflow.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class IDP extends DiagramEntity {
  public IDP(Database db, Identity identity, WorkflowContext context) {
    super(db, identity, context);
  }

  public boolean saveIDP(
      String designerId, String title, String description, String projectDetails, String status) {
    mark("IDP.saveIDP");
    identity.verified();
    if (!designerId.equals(identity.user().id()))
      throw new ApiException(403, "forbidden", "You may only create your own IDP.");
    checkStatus(status);
    String id = Database.id();
    db.update(
        "INSERT INTO projects(id,owner_id,title,description,project_details,stage,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
        id,
        designerId,
        title,
        description,
        projectDetails,
        status,
        Database.now(),
        Database.now());
    context.state().createdId = id;
    event("project", designerId, id);
    return true;
  }

  public boolean updateIDP(
      String idpId, String title, String description, String projectDetails, String status) {
    mark("IDP.updateIDP");
    identity.verified();
    owner(idpId);
    checkStatus(status);
    return db.update(
            "UPDATE projects SET title=?,description=?,project_details=?,stage=?,updated_at=? WHERE id=?",
            title,
            description,
            projectDetails,
            status,
            Database.now(),
            idpId)
        == 1;
  }

  private void checkStatus(String status) {
    if (!Set.of("Current", "Past").contains(status))
      throw new ApiException("Project status must be Current or Past.");
  }

  public boolean setCoverImage(String idpId, int coverIndex) {
    mark("IDP.setCoverImage");
    owner(idpId);
    var photos =
        db.rows("SELECT id FROM project_photos WHERE project_id=? ORDER BY position", idpId);
    if (coverIndex < 0 || coverIndex >= photos.size())
      throw new ApiException("Choose a valid cover image.");
    db.update(
        "UPDATE project_photos SET is_cover=CASE WHEN id=? THEN 1 ELSE 0 END WHERE project_id=?",
        photos.get(coverIndex).get("id"),
        idpId);
    return true;
  }

  public List<Map<String, Object>> getIDPsByDesigner(String designerId) {
    mark("IDP.getIDPsByDesigner");
    if (!designerId.equals(identity.user().id()))
      throw new ApiException(403, "forbidden", "You may only view your own portfolio.");
    return db
        .rows(
            "SELECT p.*,u.status AS owner_status FROM projects p JOIN users u ON u.id=p.owner_id WHERE p.owner_id=? ORDER BY created_at DESC",
            designerId)
        .stream()
        .map(this::idpView)
        .toList();
  }

  public List<Map<String, Object>> findPublishedByKeyword(String keyword) {
    mark("IDP.findPublishedByKeyword");
    return db
        .rows(
            "SELECT p.*,u.status AS owner_status FROM projects p JOIN users u ON u.id=p.owner_id WHERE p.published=1 AND p.archived=0 AND p.takedown_reason='' AND u.status='active' AND LOCATE(LOWER(?),LOWER(p.title||' '||p.description||' '||p.project_details))>0 ORDER BY p.created_at DESC",
            keyword)
        .stream()
        .map(this::idpView)
        .toList();
  }

  public com.meow.domain.IDP getIDP(String idpId) {
    mark("IDP.getIDP");
    var row = project(idpId);
    if (!visible(row) && !row.get("owner_id").equals(identity.user().id()))
      throw new ApiException(404, "not_found", "Project unavailable.");
    var model = com.meow.domain.IDP.from(row);
    return model.withDetails(idpView(row));
  }

  public boolean updateStatus(String idpId, String action) {
    mark("IDP.updateStatus");
    identity.verified();
    owner(idpId);
    if (!Set.of("Publish", "Unpublish").contains(action))
      throw new ApiException("Choose Publish or Unpublish.");
    return db.update(
            "UPDATE projects SET published=?,published_at=CASE WHEN ?=1 AND published_at IS NULL THEN ? ELSE published_at END,updated_at=? WHERE id=?",
            action.equals("Publish") ? 1 : 0,
            action.equals("Publish") ? 1 : 0,
            Database.now(),
            Database.now(),
            idpId)
        == 1;
  }

  public boolean isPublished(String idpId) {
    mark("IDP.isPublished");
    return visible(project(idpId));
  }

  public int countIDPs(String designerId) {
    mark("IDP.countIDPs");
    return (int) db.count("SELECT COUNT(*) FROM projects WHERE owner_id=?", designerId);
  }

  public int getTotalViews(String designerId) {
    mark("IDP.getTotalViews");
    return (int)
        db.count(
            "SELECT COUNT(*) FROM events e JOIN projects p ON p.id=e.project_id WHERE p.owner_id=? AND e.type='view'",
            designerId);
  }

  public int getTotalShortlists(String designerId) {
    mark("IDP.getTotalShortlists");
    return (int)
        db.count(
            "SELECT COUNT(*) FROM events e JOIN projects p ON p.id=e.project_id WHERE p.owner_id=? AND e.type='shortlist'",
            designerId);
  }

  public List<Map<String, Object>> getStatsByDesigner(String designerId) {
    mark("IDP.getStatsByDesigner");
    return db.rows(
        "SELECT p.id,p.title,(SELECT COUNT(*) FROM events e WHERE e.project_id=p.id AND e.type='view') AS views,(SELECT COUNT(*) FROM events e WHERE e.project_id=p.id AND e.type='shortlist') AS shortlists FROM projects p WHERE p.owner_id=? ORDER BY p.title",
        designerId);
  }
}
