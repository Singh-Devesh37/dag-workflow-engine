package com.example.core.exception;

import com.example.core.model.WorkflowDefinition;

public class WorkflowEngineException extends RuntimeException {
  public WorkflowEngineException(String message) {
    super(message);
  }

  public WorkflowEngineException(String message, Throwable cause) {
    super(message, cause);
  }
}
