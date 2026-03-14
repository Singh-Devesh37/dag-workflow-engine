package com.example.api.mapper;

import com.example.api.app.WorkflowDefinitionAO;
import com.example.core.engine.TaskExecutor;
import com.example.core.engine.TaskExecutorFactory;
import com.example.core.enums.TaskType;
import com.example.core.model.TaskNode;
import com.example.core.model.WorkflowDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WorkflowDefinitionMapper {

  private final TaskExecutorFactory executorFactory;

  public WorkflowDefinitionAO toAO(WorkflowDefinition domain) {
    if (domain == null) return null;

    List<WorkflowDefinitionAO.TaskDefinitionAO> taskAOs =
        domain.getTasks() == null
            ? List.of()
            : domain.getTasks().values().stream()
                .map(
                    t ->
                        new WorkflowDefinitionAO.TaskDefinitionAO(
                            t.getName(),
                            t.getTaskExecutor().getType().name(),
                            t.getConfig(),
                            t.getMaxRetries(),
                            t.getInitialDelayMillis(),
                            t.getTimeoutMillis()))
                .collect(Collectors.toList());

    return new WorkflowDefinitionAO(
        domain.getId(), domain.getName(), domain.getDescription(), taskAOs);
  }

  public WorkflowDefinition fromAO(WorkflowDefinitionAO ao) {
    if (ao == null) return null;

    // Convert TaskDefinitionAOs to TaskNodes with attached executors
    Map<String, TaskNode> taskMap =
        ao.tasks() == null
            ? Map.of()
            : ao.tasks().stream()
                .map(
                    t -> {
                      TaskType type = TaskType.valueOf(t.type().toUpperCase());
                      TaskExecutor executor = executorFactory.createExecutor(type, t.config());
                      return new TaskNode(
                          t.name(),
                          executor,
                          t.config(),
                          t.maxRetries(),
                          t.initialDelayMillis(),
                          t.timeoutMillis());
                    })
                .collect(Collectors.toMap(TaskNode::getName, node -> node));

    // Build WorkflowDefinition domain object
    return new WorkflowDefinition(ao.name(), ao.description(), taskMap);
  }
}
