package com.example.api.model;

import com.example.core.model.TaskNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

public record ScheduleWorkflowRequest(
        @NotBlank String workflowId,
        Map<String, Object> initialContext,
        @NotBlank String cron) {}
