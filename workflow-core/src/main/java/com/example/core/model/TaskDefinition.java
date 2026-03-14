package com.example.core.model;

import com.example.core.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDefinition {
    private String name;
    private TaskType type;
    private Map<String,Object> config;
    private int maxRetries;
    private long initialDelay;
    private long timeoutMillis;
    private List<String> dependencies;
}
