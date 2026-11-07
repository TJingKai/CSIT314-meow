package com.meow.workflow;

import com.meow.api.ApiException;
import java.io.File;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.multipart.MultipartFile;

/** Request-local transport state. Diagram methods keep the parameter lists in the report. */
@Component
@RequestScope
public class WorkflowContext {
  public static class State {
    public Map<String, Object> input = new LinkedHashMap<>();
    public Map<String, Object> output = new LinkedHashMap<>();
    public final List<Map<String, String>> messages = new ArrayList<>();
    public final List<String> calls = new ArrayList<>();
    public final Map<File, MultipartFile> files = new HashMap<>();
    public jakarta.servlet.http.HttpServletRequest request;
    public jakarta.servlet.http.HttpServletResponse response;
    public final List<String> infrastructureCalls = new ArrayList<>();
    public String createdId;
    public String selectedId;
    public int status = 200;
  }

  private final State state = new State();

  public State state() {
    return state;
  }

  public void call(String method) {
    state.calls.add(method);
  }

  public void message(String kind, String message) {
    state.messages.add(Map.of("kind", kind, "text", message));
    if (kind.equals("error")) state.status = 400;
  }

  public String value(String key) {
    Object value = state.input.get(key);
    return value == null ? "" : value.toString();
  }

  public MultipartFile upload(File file) {
    MultipartFile value = state.files.get(file);
    if (value == null) throw new ApiException("Upload is not attached to this request.");
    return value;
  }
}
