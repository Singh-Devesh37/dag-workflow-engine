package com.example.api.pub;

import com.example.core.model.TaskRun;
import com.example.core.model.WorkflowRun;
import com.example.core.pub.WorkflowEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

public class StompWorkflowEventPublisher implements WorkflowEventPublisher {

  private final SimpMessagingTemplate template;

  public StompWorkflowEventPublisher(SimpMessagingTemplate template) {
    this.template = template;
  }

  @Override
  public void publishTaskUpdate(String runId, TaskRun ao) {
    template.convertAndSend("/topic/workflows/" + runId + "/tasks/", ao);
  }

  @Override
  public void publishWorkflowUpdate(String runId, WorkflowRun ao) {
    template.convertAndSend("/topic/workflows/" + runId, ao);
  }
}
