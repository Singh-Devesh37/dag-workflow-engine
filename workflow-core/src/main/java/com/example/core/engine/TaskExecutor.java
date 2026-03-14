package com.example.core.engine;

import com.example.core.enums.TaskType;
import com.example.core.model.TaskExecutionResult;

public interface TaskExecutor {

  TaskExecutionResult execute(ExecutionContext context);

  TaskType getType();
}
