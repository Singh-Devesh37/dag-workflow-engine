package com.example.core.model;

import com.example.core.enums.RunStatus;
import lombok.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
@AllArgsConstructor
public class WorkflowRun {
  private final String runId;
  private final String workflowId;

  @Builder.Default private volatile RunStatus status = RunStatus.PENDING;

  private Instant startTime;
  private Instant endTime;
  private Long durationMillis;

  private final ConcurrentHashMap<String, TaskRun> taskRuns = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, Object> mergedContextSnapshot = new ConcurrentHashMap<>();

    @Builder.Default
  private Map<String, Object> metaData = Map.of();
  private Instant created;
  private Instant updated;

  public WorkflowRun(String runId, String workflowId) {
    this.runId = runId;
    this.workflowId = workflowId;
  }

  public void putTaskRun(TaskRun taskRun) {
    this.taskRuns.put(taskRun.getTaskName(), taskRun);
  }
}
