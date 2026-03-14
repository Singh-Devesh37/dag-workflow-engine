package com.example.persistence.repo;

import com.example.core.enums.RunStatus;
import com.example.core.model.TaskRun;
import com.example.core.repo.TaskRunRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemTaskRunRepository implements TaskRunRepository {
  private final Map<String, TaskRun> store = new ConcurrentHashMap<>();

  @Override
  public TaskRun save(TaskRun taskRun) {
    store.put(taskRun.getId(), taskRun);
    return taskRun;
  }

  @Override
  public Optional<TaskRun> findById(String id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<TaskRun> findByWorkflowId(String workflowId) {
    return store.values().stream()
        .filter(taskRun -> taskRun.getWorkflowId().equals(workflowId))
        .toList();
  }

  @Override
  public List<TaskRun> findByStatus(RunStatus status) {
    return store.values().stream().filter(taskRun -> taskRun.getStatus() == status).toList();
  }

  @Override
  public List<TaskRun> findAll() {
    return new ArrayList<>(store.values());
  }
}
