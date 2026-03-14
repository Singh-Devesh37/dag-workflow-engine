package com.example.core.model;

import com.example.core.engine.TaskExecutorFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class TaskDefinitionConverter {

  private final TaskExecutorFactory executorFactory;

  public TaskDefinitionConverter(TaskExecutorFactory executorFactory) {
    this.executorFactory = executorFactory;
  }

  public Map<String, TaskNode> convert(Map<String, TaskDefinition> defsByName) {
    Objects.requireNonNull(defsByName, "defsByName");
    Map<String, TaskNode> nodes = new LinkedHashMap<>(defsByName.size());

    for (Map.Entry<String, TaskDefinition> e : defsByName.entrySet()) {
      TaskDefinition def = e.getValue();
      TaskNode node =
          TaskNode.builder()
              .name(def.getName())
              .taskExecutor(executorFactory.createExecutor(def.getType(), def.getConfig()))
              .maxRetries(def.getMaxRetries())
              .initialDelayMillis(def.getInitialDelay())
              .timeoutMillis(def.getTimeoutMillis())
              .build();
      nodes.put(e.getKey(), node);
    }

    for (Map.Entry<String, TaskDefinition> e : defsByName.entrySet()) {
      TaskDefinition def = e.getValue();
      TaskNode node = nodes.get(e.getKey());
      for (String depName : def.getDependencies()) {
        TaskNode dep = nodes.get(depName);
        if (dep == null) {
          throw new IllegalStateException(
              "Unknown dependency '" + depName + "' referenced by task '" + def.getName() + "'");
        }
        node.getDependencies().add(dep);
      }
    }

    return nodes;
  }
}
