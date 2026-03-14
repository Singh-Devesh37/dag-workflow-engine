package com.example.api.pub;

import com.example.api.app.TaskRunAO;
import com.example.api.app.WorkflowRunAO;
import com.example.core.model.TaskRun;
import com.example.core.model.WorkflowRun;
import com.example.core.pub.WorkflowEventPublisher;

public class NoOpWorkflowEventPublisher implements WorkflowEventPublisher {

  @Override
  public void publishTaskUpdate(String runId, TaskRun ao) {}

  @Override
  public void publishWorkflowUpdate(String runId, WorkflowRun ao) {}
}
