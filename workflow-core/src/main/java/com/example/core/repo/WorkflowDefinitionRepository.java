package com.example.core.repo;

import com.example.core.model.WorkflowDefinition;

import java.util.List;
import java.util.Optional;

public interface WorkflowDefinitionRepository {

  WorkflowDefinition save(WorkflowDefinition workflowDefinition);

  Optional<WorkflowDefinition> findById(String id);

  Optional<WorkflowDefinition> findByName(String name);

  List<WorkflowDefinition> findAll();
}
