package com.example.core.exception;

public class TaskExecutionException extends WorkflowEngineException{
    public TaskExecutionException(String message) {
        super(message);
    }

    public TaskExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
