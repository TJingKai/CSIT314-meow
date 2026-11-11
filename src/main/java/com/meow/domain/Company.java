package com.meow.domain;

import static com.meow.api.Json.object;

import com.meow.repository.Database;
import java.util.Map;

public record Company(
    String id,
    String ownerId,
    String name,
    String description,
    String uen,
    Integer year,
    String styles,
    String contact,
    String hours,
    String areas,
    String awards,
    String proofId,
    String logoId,
    String bannerId) {
  public static Company from(Map<String, Object> row) {
    return new Company(
        Database.str(row, "id"),
        Database.str(row, "owner_id"),
        Database.str(row, "name"),
        Database.str(row, "description"),
        Database.str(row, "uen"),
        row.get("established_year") == null
            ? null
            : ((Number) row.get("established_year")).intValue(),
        Database.str(row, "styles"),
        Database.str(row, "contact"),
        Database.str(row, "hours"),
        Database.str(row, "areas"),
        Database.str(row, "awards"),
        Database.str(row, "proof_id"),
        Database.str(row, "logo_id"),
        Database.str(row, "banner_id"));
  }

  private static String url(String id) {
    return id.isEmpty() ? null : "/api/v1/uploads/" + id + "/content";
  }

  public Map<String, Object> view(boolean privateView) {
    Map<String, Object> value =
        object(
            "id",
            id,
            "owner",
            ownerId,
            "name",
            name,
            "description",
            description,
            "uen",
            uen,
            "year",
            year,
            "styles",
            styles,
            "contact",
            contact,
            "hours",
            hours,
            "areas",
            areas,
            "awards",
            awards,
            "logo_id",
            logoId.isEmpty() ? null : logoId,
            "banner_id",
            bannerId.isEmpty() ? null : bannerId,
            "logoUrl",
            url(logoId),
            "bannerUrl",
            url(bannerId));
    if (privateView) value.put("proofUrl", url(proofId));
    return value;
  }
}
