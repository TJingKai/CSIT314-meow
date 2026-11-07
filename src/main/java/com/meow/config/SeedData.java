package com.meow.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meow.repository.Database;
import java.io.Console;
import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit demo initialisation. Existing databases are never reset or reseeded. */
@Component
public class SeedData implements ApplicationRunner {
  private final Database db;
  private final PasswordEncoder passwords;
  private final ObjectMapper json;
  private final boolean demo;

  public SeedData(
      Database db,
      PasswordEncoder passwords,
      ObjectMapper json,
      @Value("${meow.seed-demo:false}") boolean demo) {
    this.db = db;
    this.passwords = passwords;
    this.json = json;
    this.demo = demo;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) throws Exception {
    if (args.containsOption("meow.create-admin")) {
      Console console = System.console();
      if (console == null)
        throw new IllegalStateException(
            "Create an administrator from a terminal using java -jar, so passwords can be entered without echo.");
      String email = args.getOptionValues("meow.create-admin").get(0);
      String name = console.readLine("Administrator name: ");
      char[] first = console.readPassword("Password (10–128 characters): ");
      char[] second = console.readPassword("Confirm password: ");
      try {
        if (!java.util.Arrays.equals(first, second))
          throw new IllegalArgumentException("Passwords do not match.");
        com.meow.api.Inputs.password(Map.of("password", new String(first)), "password");
        email = com.meow.api.Inputs.email(Map.of("email", email));
        name = com.meow.api.Inputs.text(Map.of("name", name), "name", true, 100);
        db.update(
            "INSERT INTO users (id,name,email,password_hash,role,status,created_at) VALUES (?,?,?,?, 'admin','active',?)",
            Database.id(),
            name,
            email,
            passwords.encode(new String(first)),
            Database.now());
        console.printf("Administrator created.%n");
      } finally {
        java.util.Arrays.fill(first, '\0');
        java.util.Arrays.fill(second, '\0');
      }
      return;
    }
    if (demo) seed();
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public void seed() throws Exception {
    if (db.count("SELECT COUNT(*) FROM users") > 0) return;
    Map<String, Object> source;
    try (InputStream input = getClass().getResourceAsStream("/seed.json")) {
      source = json.readValue(input, new TypeReference<>() {});
    }
    String password = passwords.encode("MEOWDemo!2026");
    for (Map<String, Object> user : (List<Map<String, Object>>) source.get("users")) {
      boolean verified = user.get("role").equals("designer") && user.get("status").equals("active");
      db.update(
          "INSERT INTO users (id,name,email,password_hash,role,status,created_at,verified_at) VALUES (?,?,?,?,?,?,?,?)",
          user.get("id"),
          user.get("name"),
          user.get("email"),
          password,
          user.get("role"),
          user.get("status"),
          Database.now(),
          verified ? Database.now() : null);
    }
    for (Map<String, Object> company : (List<Map<String, Object>>) source.get("companies")) {
      db.update(
          "INSERT INTO companies (id,owner_id,name,description,uen,established_year,styles,contact,hours,areas,awards) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
          company.get("id"),
          company.get("owner"),
          company.get("name"),
          company.getOrDefault("description", ""),
          company.get("uen"),
          company.get("year"),
          company.getOrDefault("styles", ""),
          company.getOrDefault("contact", ""),
          company.getOrDefault("hours", ""),
          company.getOrDefault("areas", ""),
          company.getOrDefault("awards", ""));
    }
    for (Map<String, Object> category : (List<Map<String, Object>>) source.get("categories"))
      db.update(
          "INSERT INTO categories (id,name,active) VALUES (?,?,?)",
          category.get("id"),
          category.get("name"),
          Boolean.TRUE.equals(category.get("active")) ? 1 : 0);
    for (Map<String, Object> project : (List<Map<String, Object>>) source.get("projects")) {
      String created = Database.timestamp(Instant.parse((String) project.get("created")));
      db.update(
          "INSERT INTO projects (id,owner_id,title,description,property,location,style,budget,size,stage,duration,published,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,1,?,?)",
          project.get("id"),
          project.get("owner"),
          project.get("title"),
          project.get("description"),
          project.get("property"),
          project.get("location"),
          project.get("style"),
          project.get("budget"),
          project.get("size"),
          project.get("stage"),
          project.get("duration"),
          created,
          Database.now());
      db.update("UPDATE projects SET published_at=? WHERE id=?", created, project.get("id"));
      for (String category : new LinkedHashSet<>((List<String>) project.get("categories")))
        db.update("INSERT INTO project_categories VALUES (?,?)", project.get("id"), category);
      int index = 0;
      for (String url : (List<String>) project.get("photos")) {
        db.update(
            "INSERT INTO project_photos VALUES (?,?,NULL,?,?,?)",
            Database.id(),
            project.get("id"),
            url,
            index,
            index == 0 ? 1 : 0);
        index++;
      }
    }
  }
}
