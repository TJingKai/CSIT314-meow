package com.meow.api;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps expected failures to a stable API error contract without exposing stack traces. */
@RestControllerAdvice
public class ErrorHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ErrorHandler.class);

  private ResponseEntity<Map<String, Object>> error(int status, String code, String message) {
    return ResponseEntity.status(status)
        .body(Json.object("error", Json.object("code", code, "message", message)));
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Map<String, Object>> expected(ApiException exception) {
    return error(exception.status(), exception.code(), exception.getMessage());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException exception) {
    return error(409, "conflict", "This operation conflicts with existing records.");
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    org.springframework.web.multipart.support.MissingServletRequestPartException.class,
    org.springframework.web.multipart.MultipartException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<Map<String, Object>> invalid(Exception exception) {
    return error(400, "validation_error", "Send a valid request with the required fields.");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, Object>> tooLarge(Exception exception) {
    return error(413, "file_too_large", "Uploads must be at most 2MB; requests at most 3MB.");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Map<String, Object>> missing(Exception exception) {
    return error(404, "not_found", "Resource not found.");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<Map<String, Object>> method(Exception exception) {
    return error(405, "method_not_allowed", "This HTTP method is not supported.");
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<Map<String, Object>> unsupportedMedia(Exception exception) {
    return error(415, "unsupported_media_type", "Use the Content-Type required by this endpoint.");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> unexpected(Exception exception) {
    LOG.error("Unhandled request failure", exception);
    return error(500, "internal_error", "An unexpected server error occurred.");
  }
}
