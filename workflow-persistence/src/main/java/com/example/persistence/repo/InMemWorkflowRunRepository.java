package com.example.persistence.repo;

import com.example.core.enums.RunStatus;
import com.example.core.model.WorkflowRun;
import com.example.core.repo.WorkflowRunRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemWorkflowRunRepository implements WorkflowRunRepository {

  private final Map<String, WorkflowRun> store = new ConcurrentHashMap<>();

  @Override
  public WorkflowRun save(WorkflowRun workflowRun) {
    store.put(workflowRun.getWorkflowId(), workflowRun);
    return workflowRun;
  }

  @Override
  public Optional<WorkflowRun> findById(String id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<WorkflowRun> findByStatus(RunStatus status) {
    return store.values().stream()
        .filter(workflowRun -> workflowRun.getStatus() == status)
        .toList();
  }

  @Override
  public List<WorkflowRun> findAll() {
    return new ArrayList<>(store.values());
  }
}
