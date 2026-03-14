package com.example.core.model;

import lombok.*;

import java.time.Instant;
import java.util.Map;

@Data
@Getter
@AllArgsConstructor
@Builder
public class TaskExecutionResult {
  private final boolean success;
  private final String message;
  private final Throwable error;
  private final Map<String, Object> contextDelta;
  private final Instant startTime;
  private final Instant endTime;

  public static TaskExecutionResult success(
      String message, Map<String, Object> contextDelta, Instant start, Instant end) {
    return new TaskExecutionResult(
        true, message == null ? "success" : message, null, contextDelta, start, end);
  }

  public static TaskExecutionResult failure(
      String errMsg, Throwable error, Instant start, Instant end) {
    return new TaskExecutionResult(
        false,
        errMsg != null ? errMsg : error != null ? error.getMessage() : null,
        error,
        Map.of(),
        start,
        end);
  }
}
