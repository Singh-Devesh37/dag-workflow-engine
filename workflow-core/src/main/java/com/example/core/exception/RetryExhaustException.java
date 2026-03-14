package com.example.core.exception;

public class RetryExhaustException extends WorkflowEngineException{
    public RetryExhaustException(String message) {
        super(message);
    }

    public RetryExhaustException(String message, Throwable cause) {
        super(message, cause);
    }
}
