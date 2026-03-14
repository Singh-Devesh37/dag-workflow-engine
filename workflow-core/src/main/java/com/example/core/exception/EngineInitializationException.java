package com.example.core.exception;

public class EngineInitializationException extends WorkflowEngineException{
    public EngineInitializationException(String message) {
        super(message);
    }

    public EngineInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
