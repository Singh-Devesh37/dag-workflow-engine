package com.example.persistence.repo;

import com.example.core.model.WorkflowDefinition;
import com.example.core.repo.WorkflowDefinitionRepository;
import com.example.persistence.entity.WorkflowDefinitionEntity;
import com.example.persistence.mapper.WorkflowDefinitionMapper;

import java.util.List;
import java.util.Optional;

public class WorkflowDefinitionRepositoryImpl implements WorkflowDefinitionRepository {

  private final WorkflowDefinitionJpaRepository workflowDefinitionJpaRepository;
  private final WorkflowDefinitionMapper mapper;

  public WorkflowDefinitionRepositoryImpl(
      WorkflowDefinitionJpaRepository workflowDefinitionJpaRepository,
      WorkflowDefinitionMapper mapper) {
    this.workflowDefinitionJpaRepository = workflowDefinitionJpaRepository;
    this.mapper = mapper;
  }

  @Override
  public WorkflowDefinition save(WorkflowDefinition workflowDefinition) {
    WorkflowDefinitionEntity entity = mapper.toEntity(workflowDefinition);
    WorkflowDefinitionEntity savedEntity = workflowDefinitionJpaRepository.save(entity);
    return mapper.fromEntity(savedEntity);
  }

  @Override
  public Optional<WorkflowDefinition> findById(String id) {
    return workflowDefinitionJpaRepository.findById(id).map(mapper::fromEntity);
  }

  @Override
  public Optional<WorkflowDefinition> findByName(String name) {
    return workflowDefinitionJpaRepository.findByName(name).map(mapper::fromEntity);
  }

  @Override
  public List<WorkflowDefinition> findAll() {
    return workflowDefinitionJpaRepository.findAll().stream().map(mapper::fromEntity).toList();
  }
}
