package com.example.api.app;

import java.util.List;
import java.util.Map;

public record WorkflowDefinitionAO(
    String id, String name, String description, List<TaskDefinitionAO> tasks) {
  public record TaskDefinitionAO(
      String name,
      String type,
      Map<String, Object> config,
      int maxRetries,
      long initialDelayMillis,
      long timeoutMillis) {}
}
