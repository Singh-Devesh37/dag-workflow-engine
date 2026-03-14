package com.example.core.repo;

import com.example.core.enums.RunStatus;
import com.example.core.model.WorkflowRun;

import java.util.List;
import java.util.Optional;

public interface WorkflowRunRepository {
  WorkflowRun save(WorkflowRun workflowRun);

  Optional<WorkflowRun> findById(String id);

  List<WorkflowRun> findByStatus(RunStatus status);

  List<WorkflowRun> findAll();
}
