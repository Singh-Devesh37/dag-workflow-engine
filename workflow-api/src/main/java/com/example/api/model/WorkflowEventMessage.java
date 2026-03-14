package com.example.api.model;

import com.example.core.enums.RunStatus;

import java.time.Instant;
import java.util.Map;

public record WorkflowEventMessage(
        String runId,
        String workflowId,
        RunStatus runStatus,
        Instant timestamp,
        Map<String,Object> details
) {}
