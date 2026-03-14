package com.example.api.mapper;

import com.example.api.app.TaskRunAO;
import com.example.api.app.WorkflowRunAO;
import com.example.core.model.TaskRun;
import com.example.core.model.WorkflowRun;

import java.util.Map;
import java.util.stream.Collectors;

public class WorkflowResultMapper {
  private WorkflowResultMapper() {}

  public TaskRunAO toAO(TaskRun bo) {
    return new TaskRunAO(
        bo.getId(),
        bo.getTaskName(),
        bo.getStatus(),
        bo.getAttempt().get(),
        bo.getMaxRetries(),
        bo.getStartTime(),
        bo.getEndTime(),
        bo.getDurationMillis(),
        bo.getErrorMessage(),
        bo.getContextDelta());
  }

  public WorkflowRunAO toAO(WorkflowRun bo) {
    Map<String, TaskRunAO> tasks =
        bo.getTaskRuns().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> toAO(e.getValue())));
    return new WorkflowRunAO(
        bo.getRunId(),
        bo.getWorkflowId(),
        bo.getStatus(),
        bo.getStartTime(),
        bo.getEndTime(),
        tasks,
        Map.copyOf(bo.getMergedContextSnapshot()));
  }
}
