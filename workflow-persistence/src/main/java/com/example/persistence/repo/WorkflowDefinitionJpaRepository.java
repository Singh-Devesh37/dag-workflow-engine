package com.example.persistence.repo;

import com.example.persistence.entity.WorkflowDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowDefinitionJpaRepository
    extends JpaRepository<WorkflowDefinitionEntity, String> {
    Optional<WorkflowDefinitionEntity> findByName(String name);
}
