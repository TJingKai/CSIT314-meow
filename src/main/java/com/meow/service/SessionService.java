package com.meow.service;

import com.meow.api.ApiException;
import com.meow.domain.User;
import com.meow.repository.Database;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opaque sessions stored as digests; permissions are read from the current user record. */
@Service
public class SessionService {
  public static final String COOKIE = "meow_session";
  private final Database db;
  private final PasswordEncoder passwords;
  private final boolean secure;
  private final SecureRandom random = new SecureRandom();

  public SessionService(
      Database db,
      PasswordEncoder passwords,
      @Value("${meow.cookie-secure:false}") boolean secure) {
    this.db = db;
    this.passwords = passwords;
    this.secure = secure;
  }

  public static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }

  public static String cookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null)
      for (Cookie cookie : cookies) if (COOKIE.equals(cookie.getName())) return cookie.getValue();
    return "";
  }

  public void load(HttpServletRequest request) {
    String token = cookie(request);
    if (token.isEmpty()) return;
    Map<String, Object> row =
        db.find(
            "SELECT u.*,s.csrf_token FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>?",
            digest(token),
            Database.now());
    if (row == null) return;
    User user = User.from(row);
    if (user.canLogin()) {
      request.setAttribute("meow.user", user);
      request.setAttribute("meow.csrf", row.get("csrf_token"));
    }
  }

  public User authenticate(Map<String, Object> input, String remoteAddress) {
    String email = com.meow.api.Inputs.email(input);
    String key = digest(remoteAddress + "|" + email);
    Map<String, Object> attempt = db.find("SELECT * FROM login_attempts WHERE attempt_key=?", key);
    String cutoff = Database.timestamp(Instant.now().minus(15, ChronoUnit.MINUTES));
    if (attempt != null
        && Database.str(attempt, "started_at").compareTo(cutoff) >= 0
        && ((Number) attempt.get("count")).intValue() >= 10) {
      throw new ApiException(
          429, "rate_limited", "Too many login attempts. Try again in 15 minutes.");
    }
    if (attempt == null || Database.str(attempt, "started_at").compareTo(cutoff) < 0) {
      db.update(
          "MERGE INTO login_attempts (attempt_key,count,started_at) KEY(attempt_key) VALUES (?,0,?)",
          key,
          Database.now());
    }
    db.update("UPDATE login_attempts SET count=count+1 WHERE attempt_key=?", key);
    Map<String, Object> row = db.find("SELECT * FROM users WHERE email=?", email);
    Object password = input.get("password");
    if (!(password instanceof String secret)
        || secret.length() > 128
        || row == null
        || !passwords.matches((String) password, Database.str(row, "password_hash"))) {
      throw new ApiException(401, "invalid_credentials", "Email or password is incorrect.");
    }
    User user = User.from(row);
    if (!user.canLogin())
      throw new ApiException(403, "account_inactive", "This account does not have active access.");
    db.update("DELETE FROM login_attempts WHERE attempt_key=?", key);
    return user;
  }

  private String token() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  @Transactional
  public void issue(User user, HttpServletRequest request, HttpServletResponse response) {
    String token = token();
    db.update(
        "DELETE FROM sessions WHERE expires_at<=? OR token_hash=?",
        Database.now(),
        digest(cookie(request)));
    db.update(
        "INSERT INTO sessions VALUES (?,?,?,?)",
        digest(token),
        user.id(),
        token(),
        Database.timestamp(Instant.now().plus(8, ChronoUnit.HOURS)));
    response.addHeader(
        "Set-Cookie",
        ResponseCookie.from(COOKIE, token)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/")
            .maxAge(28800)
            .build()
            .toString());
  }

  public void logout(HttpServletRequest request, HttpServletResponse response) {
    db.update("DELETE FROM sessions WHERE token_hash=?", digest(cookie(request)));
    clearCookie(response);
  }

  public void clearCookie(HttpServletResponse response) {
    response.addHeader(
        "Set-Cookie",
        ResponseCookie.from(COOKIE, "")
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/")
            .maxAge(0)
            .build()
            .toString());
  }
}
