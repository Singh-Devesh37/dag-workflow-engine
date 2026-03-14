package com.example.core.exception;

public class UnsupportedTaskTypeException extends WorkflowEngineException{
    public UnsupportedTaskTypeException(String message) {
        super(message);
    }

    public UnsupportedTaskTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}
