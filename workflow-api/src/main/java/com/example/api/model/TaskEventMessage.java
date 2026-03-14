package com.example.api.model;

import com.example.core.enums.RunStatus;

import java.time.Instant;
import java.util.Map;

public record TaskEventMessage(
        String id,
        String workflowId,
        String taskName,
        RunStatus runStatus,
        Instant timestamp,
        int attempt,
        Map<String,Object> contextDelta
) {}
