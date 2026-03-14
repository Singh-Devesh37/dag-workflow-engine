package com.example.core.exception;

public class ContextPropagationException extends WorkflowEngineException{
    public ContextPropagationException(String message) {
        super(message);
    }

    public ContextPropagationException(String message, Throwable cause) {
        super(message, cause);
    }
}
