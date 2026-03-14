package com.example.core.pub;

import com.example.core.model.TaskRun;
import com.example.core.model.WorkflowRun;

public interface WorkflowEventPublisher {
  void publishTaskUpdate(String runId, TaskRun ao);

  void publishWorkflowUpdate(String runId, WorkflowRun ao);
}
