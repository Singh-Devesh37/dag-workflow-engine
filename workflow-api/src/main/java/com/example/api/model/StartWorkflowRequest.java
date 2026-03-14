package com.example.api.model;

import com.example.core.model.TaskNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;


public record StartWorkflowRequest(
        @NotBlank String workflowId,
        @NotEmpty List<TaskNode> tasks,
        Map<String, Object> initialContext
) {}

