package com.example.core.exception;

public class ExecutorNotFoundException extends WorkflowEngineException{
    public ExecutorNotFoundException(String message) {
        super(message);
    }

    public ExecutorNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
