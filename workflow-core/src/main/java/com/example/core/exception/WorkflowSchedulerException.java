package com.example.core.exception;

public class WorkflowSchedulerException extends WorkflowEngineException{
    public WorkflowSchedulerException(String message) {
        super(message);
    }

    public WorkflowSchedulerException(String message, Throwable cause) {
        super(message, cause);
    }
}
