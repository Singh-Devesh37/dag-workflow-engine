package com.example.core.exception;

public class PersistenceException extends WorkflowEngineException{
    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
