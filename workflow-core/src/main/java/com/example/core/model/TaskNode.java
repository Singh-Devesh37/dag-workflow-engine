package com.example.core.model;

import com.example.core.engine.TaskExecutor;
import com.example.core.enums.TaskType;
import com.sun.source.util.TaskListener;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@Getter
@AllArgsConstructor
public class TaskNode {
  private final String name;
  private final TaskExecutor taskExecutor;
  private final Map<String,Object> config;
  private final int maxRetries;
  private final long initialDelayMillis;
  private final long timeoutMillis;

  private final List<TaskNode> dependencies = new ArrayList<>();

  public TaskNode(String name, TaskExecutor executor, Map<String,Object> config) {
    this(name, executor,config, 0, 0, 30000);
  }

  public void addDependency(TaskNode node) {
    this.dependencies.add(node);
  }
}
