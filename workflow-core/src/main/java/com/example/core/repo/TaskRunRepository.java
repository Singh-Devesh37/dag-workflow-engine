package com.example.core.repo;

import com.example.core.enums.RunStatus;
import com.example.core.model.TaskNode;
import com.example.core.model.TaskRun;

import java.util.List;
import java.util.Optional;

public interface TaskRunRepository {
  TaskRun save(TaskRun taskRun);

  Optional<TaskRun> findById(String id);

  List<TaskRun> findByWorkflowId(String workflowId);

  List<TaskRun> findByStatus(RunStatus status);

  List<TaskRun> findAll();
}
