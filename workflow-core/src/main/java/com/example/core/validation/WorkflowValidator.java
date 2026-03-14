package com.example.core.validation;

import com.example.core.model.TaskNode;
import com.example.core.model.WorkflowDefinition;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service("WorkflowValidator")
public class WorkflowValidator {

  public void validate(WorkflowDefinition definition) {
    checkForUniqueNames(definition);
    checkForCycles(definition);
    checkTaskConfigs(definition);
  }

  private void checkForUniqueNames(WorkflowDefinition definition) {
    Set<String> names = new HashSet<>();
    for (TaskNode taskNode : definition.getTasks().values()) {
      if (!names.add(taskNode.getName())) {
        throw new IllegalArgumentException("Duplicate Task Name Found : " + taskNode.getName());
      }
    }
  }

  private void checkForCycles(WorkflowDefinition definition) {
    Map<TaskNode, Boolean> visiting = new HashMap<>();
    for (TaskNode node : definition.getTasks().values()) {
      if (hasCycle(node, visiting)) {
        throw new IllegalArgumentException(
            "Cycle Detected in workflow at task : " + node.getName());
      }
    }
  }

  private void checkTaskConfigs(WorkflowDefinition definition) {
    for (TaskNode node : definition.getTasks().values()) {
      if (node.getMaxRetries() < 0) {
        throw new IllegalArgumentException("Invalid retry count found for task: " + node.getName());
      }
      if (node.getTimeoutMillis() == 0) {
        throw new IllegalArgumentException("Invalid timeout for task: " + node.getName());
      }
    }
  }

  private boolean hasCycle(TaskNode node, Map<TaskNode, Boolean> visiting) {
    if (Boolean.TRUE.equals(visiting.get(node))) {
      return true;
    }
    if (Boolean.FALSE.equals(visiting.get(node))) {
      return false;
    }

    visiting.put(node, true);
    for (TaskNode depNode : node.getDependencies()) {
      if (hasCycle(depNode, visiting)) {
        return true;
      }
    }
    visiting.put(node, false);
    return false;
  }
}
