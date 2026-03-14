package com.example.core.exception;

public class TaskConfigurationException extends WorkflowEngineException{
    public TaskConfigurationException(String message) {
        super(message);
    }

    public TaskConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
