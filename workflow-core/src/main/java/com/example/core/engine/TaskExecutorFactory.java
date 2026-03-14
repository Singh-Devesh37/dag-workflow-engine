package com.example.core.engine;

import com.example.core.enums.TaskType;

import java.util.Map;

public interface TaskExecutorFactory {

    TaskExecutor createExecutor(TaskType type, Map<String,Object> config);

}
