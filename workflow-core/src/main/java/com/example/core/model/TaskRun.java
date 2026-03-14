package com.example.core.model;

import com.example.core.enums.RunStatus;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Data
@Getter
@Setter
public class TaskRun {
  private String id = UUID.randomUUID().toString();
  private final String workflowId;
  private final String taskName;
  private volatile RunStatus status = RunStatus.PENDING;
  private final AtomicInteger attempt = new AtomicInteger(0);
  private int maxRetries = 0;
  private Instant startTime;
  private Instant endTime;
  private long durationMillis;
  private String errorMessage;
  private String errorStackTrace;
  private Map<String, Object> contextDelta = Collections.emptyMap();
  private Map<String, Object> metaData = Collections.emptyMap();
  private Instant created;
  private Instant updated;

  public TaskRun(String taskName, String workflowId) {
    this.taskName = taskName;
    this.workflowId = workflowId;
  }

  public void incrementAttempt() {
    this.attempt.incrementAndGet();
  }
}
