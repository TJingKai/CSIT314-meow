package com.meow.config;

import com.meow.api.ApiException;
import com.meow.domain.User;
import com.meow.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
  private final SessionService sessions;

  public WebConfiguration(SessionService sessions) {
    this.sessions = sessions;
  }

  @Bean
  public static PasswordEncoder passwordEncoder() {
    return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  HttpServletRequest request, HttpServletResponse response, Object handler) {
                sessions.load(request);
                User user = (User) request.getAttribute("meow.user");
                if (Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod())) {
                  String origin = request.getHeader("Origin");
                  if (origin != null) {
                    URI uri;
                    try {
                      uri = URI.create(origin);
                    } catch (IllegalArgumentException error) {
                      throw new ApiException(403, "csrf_failed", "Invalid Origin.");
                    }
                    int port = request.getServerPort();
                    String expected =
                        request.getScheme()
                            + "://"
                            + request.getServerName()
                            + ((port == 80 && request.getScheme().equals("http"))
                                    || (port == 443 && request.getScheme().equals("https"))
                                ? ""
                                : ":" + port);
                    if (!expected.equals(uri.toString()))
                      throw new ApiException(
                          403, "csrf_failed", "Cross-origin requests are not permitted.");
                  }
                  if (user != null) {
                    String supplied = request.getHeader("X-CSRF-Token");
                    String csrf = (String) request.getAttribute("meow.csrf");
                    if (supplied == null
                        || !MessageDigest.isEqual(
                            supplied.getBytes(StandardCharsets.UTF_8),
                            csrf.getBytes(StandardCharsets.UTF_8))) {
                      throw new ApiException(403, "csrf_failed", "Missing or invalid CSRF token.");
                    }
                  }
                }
                if (handler instanceof HandlerMethod method) {
                  RequireRole annotation =
                      AnnotatedElementUtils.findMergedAnnotation(
                          method.getMethod(), RequireRole.class);
                  if (annotation == null)
                    annotation =
                        AnnotatedElementUtils.findMergedAnnotation(
                            method.getBeanType(), RequireRole.class);
                  if (annotation != null) {
                    if (user == null)
                      throw new ApiException(401, "unauthenticated", "Log in to continue.");
                    if (annotation.value().length > 0
                        && !Arrays.asList(annotation.value()).contains(user.role())) {
                      throw new ApiException(
                          403, "forbidden", "This action is not allowed for your role.");
                    }
                  }
                }
                return true;
              }
            })
        .addPathPatterns("/api/**");
  }

  @Bean
  public OncePerRequestFilter responseHeaders() {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(
          HttpServletRequest request, HttpServletResponse response, FilterChain chain)
          throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader(
            "Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https://images.unsplash.com data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
        if (request.getRequestURI().startsWith("/api/"))
          response.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
      }
    };
  }
}
