package com.example.api.pub;

import com.example.api.model.TaskEventMessage;
import com.example.api.model.WorkflowEventMessage;
import com.example.core.model.TaskRun;
import com.example.core.model.WorkflowRun;
import com.example.core.pub.WorkflowEventPublisher;
import com.example.core.repo.TaskRunRepository;
import com.example.core.repo.WorkflowRunRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service("PersistWorkflowEventPublisher")
public class PersistWorkflowEventPublisher implements WorkflowEventPublisher {

  private final WorkflowRunRepository workflowRunRepository;
  private final TaskRunRepository taskRunRepository;
  private final SimpMessagingTemplate template;

  public PersistWorkflowEventPublisher(
      WorkflowRunRepository workflowRunRepository,
      TaskRunRepository taskRunRepository,
      SimpMessagingTemplate template) {
    this.workflowRunRepository = workflowRunRepository;
    this.taskRunRepository = taskRunRepository;
    this.template = template;
  }

  @Override
  public void publishTaskUpdate(String runId, TaskRun ao) {
    taskRunRepository.save(ao);

    TaskEventMessage event =
        new TaskEventMessage(
            ao.getId(),
            ao.getWorkflowId(),
            ao.getTaskName(),
            ao.getStatus(),
            Instant.now(),
            ao.getAttempt().get(),
            ao.getContextDelta());

    if (template != null) {
      template.convertAndSend("/topic/workflows/" + runId + "/tasks/", event);
    }
  }

  @Override
  public void publishWorkflowUpdate(String runId, WorkflowRun ao) {
    workflowRunRepository.save(ao);

    WorkflowEventMessage event =
        new WorkflowEventMessage(
            ao.getRunId(),
            ao.getWorkflowId(),
            ao.getStatus(),
            Instant.now(),
            Map.of(
                "startTime",
                ao.getStartTime(),
                "endTime",
                ao.getEndTime(),
                "taskCount",
                ao.getTaskRuns()));

    if (template != null) {
      template.convertAndSend("topic/workflows/" + runId, event);
    }
  }
}
