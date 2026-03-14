package com.example.api.app;

import com.example.core.enums.RunStatus;

import java.time.Instant;
import java.util.Map;

public record TaskRunAO(
    String id,
    String taskName,
    RunStatus status,
    int attempt,
    int maxRetries,
    Instant startTime,
    Instant endTime,
    Long durationMillis,
    String errorMessage,
    Map<String, Object> contextDelta) {}
