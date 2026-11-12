package com.meow.service;

import com.meow.api.ApiException;
import com.meow.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class Identity {
  private final HttpServletRequest request;

  public Identity(HttpServletRequest request) {
    this.request = request;
  }

  public User optional() {
    return (User) request.getAttribute("meow.user");
  }

  public User user() {
    User user = optional();
    if (user == null) throw new ApiException(401, "unauthenticated", "Log in to continue.");
    return user;
  }

  public String csrf() {
    return (String) request.getAttribute("meow.csrf");
  }

  public void verified() {
    if (!user().verifiedDesigner())
      throw new ApiException(403, "verification_required", "Designer verification is required.");
  }
}
