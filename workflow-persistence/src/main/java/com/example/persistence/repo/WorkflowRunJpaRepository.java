package com.example.persistence.repo;

import com.example.persistence.entity.WorkflowRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRunJpaRepository extends JpaRepository<WorkflowRunEntity, String> {}
