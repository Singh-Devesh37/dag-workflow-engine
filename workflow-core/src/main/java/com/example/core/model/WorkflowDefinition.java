package com.example.core.model;

import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Getter
@Setter
@AllArgsConstructor
public class WorkflowDefinition {
  private String id = UUID.randomUUID().toString();
  private String name;
  private Integer version;
  private String description;
  private Map<String, TaskNode> tasks;
  private Instant created;
  private Instant updated;

  public WorkflowDefinition(String name, String description, Map<String, TaskNode> tasks) {
      this.name = name;
      this.version = 0;
      this.description = description;
      this.tasks = tasks;
  }
}
