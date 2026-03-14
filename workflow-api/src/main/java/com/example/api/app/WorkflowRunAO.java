package com.example.api.app;

import com.example.core.enums.RunStatus;

import java.time.Instant;
import java.util.Map;

public record WorkflowRunAO(
        String runId,
        String workflowId,
        RunStatus status,
        Instant startTime,
        Instant endTime,
        Map<String, TaskRunAO> taskRuns,
        Map<String, Object> mergedContextSnapshot) {}
