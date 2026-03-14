package com.example.scheduler.model;

import java.util.Map;

public record WorkflowScheduleRequest(
    String workflowId, String cronExpression, Map<String, Object> context) {}
