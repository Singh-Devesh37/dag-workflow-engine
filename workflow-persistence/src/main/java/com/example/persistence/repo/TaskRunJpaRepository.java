package com.example.persistence.repo;

import com.example.core.enums.RunStatus;
import com.example.core.model.TaskRun;
import com.example.persistence.entity.TaskRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRunJpaRepository extends JpaRepository<TaskRunEntity,String> {
    List<TaskRunEntity> findByWorkflowId(String workflowId);
    List<TaskRunEntity> findByStatus(RunStatus status);
}