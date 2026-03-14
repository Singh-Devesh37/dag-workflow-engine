package com.example.core.exception;

public class TaskTimeoutException extends WorkflowEngineException{
    public TaskTimeoutException(String message) {
        super(message);
    }

    public TaskTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
