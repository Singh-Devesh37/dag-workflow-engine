package com.example.executor;

import com.example.core.engine.TaskExecutor;
import com.example.core.engine.TaskExecutorFactory;
import com.example.core.enums.TaskType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class TaskExecutorFactoryImpl implements TaskExecutorFactory {

    private final Map<TaskType, Function<Map<String,Object>, TaskExecutor>> registry = new HashMap<>();

    public TaskExecutorFactoryImpl(){
        register(TaskType.HTTP, HTTPTaskExecutor::new);
        register(TaskType.KAFKA, KafkaTaskExecutor::new);
        register(TaskType.SCRIPT, ScriptTaskExecutor::new);
        register(TaskType.EMAIL, EmailTaskExecutor::new);
    }

    @Override
    public TaskExecutor createExecutor(TaskType type, Map<String, Object> config) {
        Function<Map<String, Object>, TaskExecutor> builder = registry.get(type);
        if (builder == null) {
            throw new IllegalArgumentException("No TaskExecutor registered for type: " + type);
        }
        return builder.apply(config);
    }

    public void register(TaskType type, Function<Map<String,Object>, TaskExecutor> builder){
        this.registry.put(type,builder);
    }
}
