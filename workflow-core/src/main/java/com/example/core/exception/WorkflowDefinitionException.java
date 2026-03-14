package com.example.core.exception;

public class WorkflowDefinitionException extends WorkflowEngineException{
    public WorkflowDefinitionException(String message) {
        super(message);
    }

    public WorkflowDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
